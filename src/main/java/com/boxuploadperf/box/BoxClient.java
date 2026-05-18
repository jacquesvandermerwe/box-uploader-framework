package com.boxuploadperf.box;

import com.boxuploadperf.config.AppConfig;
import com.boxuploadperf.http.InstrumentedHttpClient;
import com.boxuploadperf.http.NetworkTiming;
import com.boxuploadperf.http.RetryAfterParser;
import com.boxuploadperf.http.RetryDelayPolicy;
import com.boxuploadperf.metrics.ApiCallRecord;
import com.boxuploadperf.metrics.ApiPhase;
import com.boxuploadperf.metrics.MetricsDatabase;
import com.boxuploadperf.metrics.RequestUrlMetrics;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BoxClient implements AutoCloseable {

    private static final URI TOKEN_URI = URI.create("https://api.box.com/oauth2/token");
    private static final String API_BASE = "https://api.box.com/2.0";
    private final AppConfig config;
    private final InstrumentedHttpClient http;
    private String accessToken;
    private UploadZoneContext uploadZone = UploadZoneContext.globalDefault();

    public BoxClient(AppConfig config) {
        this.config = config;
        this.http = new InstrumentedHttpClient();
    }

    public void authenticate(MetricsDatabase db, String runId, String threadMode) throws Exception {
        String body = buildTokenBody();
        HttpRequest request = HttpRequest.newBuilder(TOKEN_URI)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        var result = sendWithRetry(db, runId, null, null, ApiPhase.AUTH_TOKEN, true, false,
                request, HttpResponse.BodyHandlers.ofByteArray(), threadMode, null, null, null);
        if (result.response().statusCode() != 200) {
            throw new IOException("CCG auth failed: " + result.response().statusCode() + " "
                    + new String(result.response().body()));
        }
        accessToken = extractJsonField(new String(result.response().body()), "access_token");
    }

    public String createRunFolder(MetricsDatabase db, String runId, String threadMode) throws Exception {
        String json = "{\"name\":\"" + escapeJson(config.boxRunFolderName) + "\",\"parent\":{\"id\":\""
                + config.boxParentFolderId + "\"}}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(API_BASE + "/folders"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        var result = sendWithRetry(db, runId, null, null, ApiPhase.FOLDER_CREATE, true, false,
                request, HttpResponse.BodyHandlers.ofByteArray(), threadMode, null, null, null);
        if (result.response().statusCode() != 201) {
            throw new IOException("Create folder failed: " + result.response().statusCode());
        }
        return extractJsonField(new String(result.response().body()), "id");
    }

    public UploadZoneContext resolveUploadZone(MetricsDatabase db, String runId, String threadMode,
                                               String parentFolderId, long payloadBytes) throws Exception {
        String body = String.format(
                "{\"name\":\"%s\",\"size\":%d,\"parent\":{\"id\":\"%s\"}}",
                escapeJson(UploadZoneResolver.PREFLIGHT_PROBE_NAME), payloadBytes, parentFolderId);
        HttpRequest request = HttpRequest.newBuilder(URI.create(API_BASE + "/files/content"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .method("OPTIONS", HttpRequest.BodyPublishers.ofString(body))
                .build();
        var result = sendWithRetry(db, runId, null, null, ApiPhase.PREFLIGHT_CHECK, true, false,
                request, HttpResponse.BodyHandlers.ofByteArray(), threadMode, null, null, null);
        int status = result.response().statusCode();
        if (status != 200) {
            throw new IOException("Upload preflight failed: " + status + " " + new String(result.response().body()));
        }
        String responseBody = new String(result.response().body());
        String uploadUrl = extractJsonField(responseBody, "upload_url");
        uploadZone = UploadZoneResolver.fromPreflightResponse(uploadUrl);
        return uploadZone;
    }

    public UploadZoneContext uploadZone() {
        return uploadZone;
    }

    public UploadResult uploadFile(MetricsDatabase db, String runId, String threadMode, int uploadIndex,
                                   String uploadGuid, Path payload, String parentFolderId, boolean chunked) throws Exception {
        byte[] fileBytes = Files.readAllBytes(payload);
        String fileName = uploadGuid + ".pdf";
        long startE2e = System.nanoTime();
        int ancillary = 0;
        boolean had429 = false;

        if (!chunked) {
            NetworkTiming timing = new NetworkTiming();
            String attributes = "{\"name\":\"" + escapeJson(fileName) + "\",\"parent\":{\"id\":\""
                    + parentFolderId + "\"}}";
            byte[] body = buildMultipart(attributes, fileBytes, fileName);
            timing.requestBytes = body.length;
            long transferStart = System.nanoTime();
            HttpRequest request = HttpRequest.newBuilder(URI.create(uploadZone.uploadBaseUrl() + "/files/content"))
                    .header("Authorization", uploadAuthHeader())
                    .header("Content-Type", "multipart/form-data; boundary=boxperf")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            var result = sendWithRetry(db, runId, uploadGuid, uploadIndex, ApiPhase.UPLOAD_SIMPLE, false, true,
                    request, HttpResponse.BodyHandlers.ofByteArray(), threadMode, null, null, null);
            timing.transferMs = (System.nanoTime() - transferStart) / 1_000_000.0;
            timing.durationMs = result.timing().durationMs + timing.transferMs;
            timing.requestBytes = body.length;
            int status = result.response().statusCode();
            had429 = status == 429;
            if (status != 201) {
                throw new IOException("Upload failed: " + status);
            }
            String fileId = extractJsonField(new String(result.response().body()), "id");
            double e2e = (System.nanoTime() - startE2e) / 1_000_000.0;
            return new UploadResult(fileId, timing.transferMs, e2e, 0, ancillary, had429, 0);
        }

        return uploadChunked(db, runId, threadMode, uploadIndex, uploadGuid, fileName, fileBytes, parentFolderId, startE2e);
    }

    private UploadResult uploadChunked(MetricsDatabase db, String runId, String threadMode, int uploadIndex,
                                       String uploadGuid, String fileName, byte[] fileBytes, String parentFolderId,
                                       long startE2e) throws Exception {
        int ancillary = 0;
        boolean had429 = false;
        String sessionJson = String.format(
                "{\"file_name\":\"%s\",\"file_size\":%d,\"parent\":{\"id\":\"%s\"}}",
                escapeJson(fileName), fileBytes.length, parentFolderId);
        HttpRequest createSession = HttpRequest.newBuilder(URI.create(uploadZone.uploadBaseUrl() + "/files/upload_sessions"))
                .header("Authorization", uploadAuthHeader())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(sessionJson))
                .build();
        var sessionResult = sendWithRetry(db, runId, uploadGuid, uploadIndex, ApiPhase.UPLOAD_SESSION_CREATE,
                true, false, createSession, HttpResponse.BodyHandlers.ofByteArray(), threadMode, null, null, null);
        ancillary++;
        had429 |= sessionResult.response().statusCode() == 429;
        if (sessionResult.response().statusCode() != 200) {
            throw new IOException("Session create failed: " + sessionResult.response().statusCode());
        }
        String sessionBody = new String(sessionResult.response().body());
        String uploadPartUrl = extractNested(sessionBody, "session_endpoints", "upload_part");
        String commitUrl = extractNested(sessionBody, "session_endpoints", "commit");

        long chunkSize = config.uploadChunkSizeBytes;
        List<String> partSha1s = new ArrayList<>();
        double primaryMs = 0;
        int chunkIndex = 0;
        for (long offset = 0; offset < fileBytes.length; offset += chunkSize, chunkIndex++) {
            int len = (int) Math.min(chunkSize, (long) fileBytes.length - offset);
            byte[] part = new byte[len];
            System.arraycopy(fileBytes, (int) offset, part, 0, len);
            String sha1 = sha1Hex(part);
            partSha1s.add(sha1);

            HttpRequest partReq = HttpRequest.newBuilder(URI.create(uploadPartUrl))
                    .header("Authorization", uploadAuthHeader())
                    .header("Digest", "sha=" + sha1)
                    .header("Content-Type", "application/octet-stream")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(part))
                    .build();
            long partStart = System.nanoTime();
            var partResult = sendWithRetry(db, runId, uploadGuid, uploadIndex, ApiPhase.UPLOAD_PART, false, true,
                    partReq, HttpResponse.BodyHandlers.ofByteArray(), threadMode, chunkIndex, offset, len);
            double partMs = (System.nanoTime() - partStart) / 1_000_000.0;
            primaryMs += partMs;
            had429 |= partResult.response().statusCode() == 429;
            if (partResult.response().statusCode() != 200) {
                throw new IOException("Part upload failed: " + partResult.response().statusCode());
            }
        }

        StringBuilder shaArray = new StringBuilder("[");
        for (int i = 0; i < partSha1s.size(); i++) {
            if (i > 0) {
                shaArray.append(',');
            }
            shaArray.append('"').append(partSha1s.get(i)).append('"');
        }
        shaArray.append(']');
        String commitJson = "{\"parts\":" + shaArray + "}";
        HttpRequest commitReq = HttpRequest.newBuilder(URI.create(commitUrl))
                .header("Authorization", uploadAuthHeader())
                .header("Content-Type", "application/json")
                .header("Digest", "sha=" + sha1Hex(fileBytes))
                .POST(HttpRequest.BodyPublishers.ofString(commitJson))
                .build();
        var commitResult = sendWithRetry(db, runId, uploadGuid, uploadIndex, ApiPhase.UPLOAD_COMMIT, true, false,
                commitReq, HttpResponse.BodyHandlers.ofByteArray(), threadMode, null, null, null);
        ancillary++;
        had429 |= commitResult.response().statusCode() == 429;
        if (commitResult.response().statusCode() != 200 && commitResult.response().statusCode() != 201) {
            throw new IOException("Commit failed: " + commitResult.response().statusCode());
        }
        String fileId = extractJsonField(new String(commitResult.response().body()), "id");
        double e2e = (System.nanoTime() - startE2e) / 1_000_000.0;
        return new UploadResult(fileId, primaryMs, e2e, chunkIndex, ancillary, had429, chunkIndex);
    }

    public void deleteFolder(String folderId) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(API_BASE + "/folders/" + folderId + "?recursive=true"))
                .header("Authorization", "Bearer " + accessToken)
                .DELETE()
                .build();
        http.send(req, HttpResponse.BodyHandlers.discarding());
    }

    private <T> InstrumentedHttpClient.HttpResult<T> sendWithRetry(
            MetricsDatabase db, String runId, String uploadGuid, Integer uploadIndex,
            ApiPhase phase, boolean ancillary, boolean primary,
            HttpRequest request, HttpResponse.BodyHandler<T> handler, String threadMode,
            Integer chunkIndex, Long chunkOffset, Integer chunkLength) throws Exception {
        InterruptedException interrupted = null;
        for (int attempt = 1; attempt <= config.retryMaxAttempts; attempt++) {
            var result = http.send(request, handler);
            int status = result.response().statusCode();
            RetryAfterParser.OptionalInt parsedRetry = RetryAfterParser.parseSeconds(result.response());
            Integer retryAfterSec = parsedRetry.isPresent() ? parsedRetry.getAsInt() : null;
            boolean retryable = status == 429 || status >= 500;
            RetryDelayPolicy.Delay delay = retryable && attempt < config.retryMaxAttempts
                    ? RetryDelayPolicy.compute(status, retryAfterSec, attempt, config.retryBackoffMs)
                    : null;
            Long sleepMs = delay != null && delay.sleepMs() > 0 ? delay.sleepMs() : null;
            String delaySource = delay != null && sleepMs != null ? delay.source().name() : null;
            record(db, runId, uploadGuid, uploadIndex, phase, ancillary, primary,
                    request.method(), RequestUrlMetrics.fromUri(request.uri()),
                    status, result.timing(), threadMode, attempt, retryAfterSec,
                    chunkIndex, chunkOffset, chunkLength, sleepMs, delaySource);
            if (!retryable || attempt >= config.retryMaxAttempts) {
                return result;
            }
            try {
                Thread.sleep(sleepMs != null ? sleepMs : 0L);
            } catch (InterruptedException e) {
                interrupted = e;
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (interrupted != null) {
            throw interrupted;
        }
        throw new IOException("Request failed after " + config.retryMaxAttempts + " attempts: " + request.uri());
    }

    private void record(MetricsDatabase db, String runId, String uploadGuid, Integer uploadIndex,
                          ApiPhase phase, boolean ancillary, boolean primary, String method, String url,
                          int status, NetworkTiming timing, String threadMode, int attempt, Integer retryAfterSec,
                          Integer chunkIndex, Long chunkOffset, Integer chunkLength,
                          Long retrySleepMs, String retryDelaySource) throws Exception {
        db.insertApiCall(new ApiCallRecord(
                runId, uploadGuid, null, uploadIndex, phase, chunkIndex, chunkOffset, chunkLength,
                config.useChunkedUpload() ? "CHUNKED" : "SINGLE_STREAM",
                ancillary, primary, Instant.now(), method, url, status, timing, threadMode, attempt,
                status == 429, retryAfterSec, status >= 400 ? "HTTP " + status : null,
                retrySleepMs, retryDelaySource));
    }

    private String uploadAuthHeader() {
        return "Bearer " + accessToken;
    }

    private String buildTokenBody() {
        StringBuilder sb = new StringBuilder();
        sb.append("grant_type=client_credentials");
        sb.append("&client_id=").append(enc(config.boxClientId));
        sb.append("&client_secret=").append(enc(config.boxClientSecret));
        if (config.boxUserId != null && !config.boxUserId.isBlank()) {
            sb.append("&box_subject_type=user&box_subject_id=").append(enc(config.boxUserId));
        } else {
            sb.append("&box_subject_type=enterprise&box_subject_id=").append(enc(config.boxEnterpriseId));
        }
        return sb.toString();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static byte[] buildMultipart(String attributes, byte[] fileBytes, String fileName) {
        String boundary = "boxperf";
        String header = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"attributes\"\r\n\r\n"
                + attributes + "\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n"
                + "Content-Type: application/pdf\r\n\r\n";
        String footer = "\r\n--" + boundary + "--\r\n";
        byte[] h = header.getBytes(StandardCharsets.UTF_8);
        byte[] f = footer.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[h.length + fileBytes.length + f.length];
        System.arraycopy(h, 0, out, 0, h.length);
        System.arraycopy(fileBytes, 0, out, h.length, fileBytes.length);
        System.arraycopy(f, 0, out, h.length + fileBytes.length, f.length);
        return out;
    }

    private static String sha1Hex(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        return HexFormat.of().formatHex(md.digest(data));
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String extractJsonField(String json, String field) {
        Pattern p = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        p = Pattern.compile("\"" + field + "\"\\s*:\\s*([^,}\\]]+)");
        m = p.matcher(json);
        if (m.find()) {
            return m.group(1).replace("\"", "").trim();
        }
        throw new IllegalStateException("Missing field: " + field);
    }

    private static String extractNested(String json, String object, String field) {
        int idx = json.indexOf("\"" + object + "\"");
        if (idx < 0) {
            throw new IllegalStateException("Missing " + object);
        }
        String sub = json.substring(idx);
        return extractJsonField(sub, field);
    }

    @Override
    public void close() {
        http.close();
    }

    public record UploadResult(String boxFileId, double uploadDurationMs, double endToEndMs,
                               int chunkCount, int ancillaryCalls, boolean had429, int parts) {}
}
