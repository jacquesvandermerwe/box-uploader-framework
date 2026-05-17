package com.boxuploadperf.box;

import com.boxuploadperf.config.AppConfig;
import com.boxuploadperf.http.InstrumentedHttpClient;
import com.boxuploadperf.http.NetworkTiming;
import com.boxuploadperf.http.TimedBodyPublisher;
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
import java.util.Map;
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
        var result = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        record(db, runId, null, null, ApiPhase.AUTH_TOKEN, true, false, "POST", TOKEN_URI.toString(),
                result.response().statusCode(), result.timing(), threadMode, 1, null);
        if (result.response().statusCode() != 200) {
            throw new IOException("CCG auth failed: " + result.response().statusCode() + " " + new String(result.response().body()));
        }
        accessToken = extractJsonField(new String(result.response().body()), "access_token");
    }

    public String createRunFolder(MetricsDatabase db, String runId, String threadMode) throws Exception {
        String json = "{\"name\":\"" + escapeJson(config.boxRunFolderName) + "\",\"parent\":{\"id\":\"" + config.boxParentFolderId + "\"}}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(API_BASE + "/folders"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        var result = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        record(db, runId, null, null, ApiPhase.FOLDER_CREATE, true, false, "POST",
                RequestUrlMetrics.fromUri(request.uri()),
                result.response().statusCode(), result.timing(), threadMode, 1, null);
        if (result.response().statusCode() != 201) {
            throw new IOException("Create folder failed: " + result.response().statusCode());
        }
        return extractJsonField(new String(result.response().body()), "id");
    }

    /**
     * One preflight per run (PRD §9.3): OPTIONS on API host, cache regional upload base like SDK
     * {@code preflightFileUploadCheck} but reuse base for all files instead of per-file full URL.
     */
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
        var result = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        int status = result.response().statusCode();
        if (status != 200) {
            record(db, runId, null, null, ApiPhase.PREFLIGHT_CHECK, true, false, "OPTIONS",
                    RequestUrlMetrics.fromUri(request.uri()),
                    status, result.timing(), threadMode, 1, null);
            throw new IOException("Upload preflight failed: " + status + " " + new String(result.response().body()));
        }
        String responseBody = new String(result.response().body());
        String uploadUrl = extractJsonField(responseBody, "upload_url");
        String uploadToken = extractJsonFieldOptional(responseBody, "upload_token");
        uploadZone = UploadZoneResolver.fromPreflightResponse(uploadUrl, uploadToken);
        record(db, runId, null, null, ApiPhase.PREFLIGHT_CHECK, true, false, "OPTIONS",
                RequestUrlMetrics.fromUri(URI.create(uploadUrl)),
                status, result.timing(), threadMode, 1, null);
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
            String attributes = "{\"name\":\"" + escapeJson(fileName) + "\",\"parent\":{\"id\":\"" + parentFolderId + "\"}}";
            byte[] body = buildMultipart(attributes, fileBytes, fileName);
            timing.requestBytes = body.length;
            long transferStart = System.nanoTime();
            HttpRequest request = HttpRequest.newBuilder(URI.create(uploadZone.uploadBaseUrl() + "/files/content"))
                    .header("Authorization", uploadAuthHeader())
                    .header("Content-Type", "multipart/form-data; boundary=boxperf")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            var result = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            timing.transferMs = (System.nanoTime() - transferStart) / 1_000_000.0;
            timing.durationMs += timing.transferMs;
            timing.requestBytes = body.length;
            int status = result.response().statusCode();
            had429 = status == 429;
            record(db, runId, uploadGuid, uploadIndex, ApiPhase.UPLOAD_SIMPLE, false, true, "POST",
                    RequestUrlMetrics.fromUri(request.uri()),
                    status, timing, threadMode, 1, extractRetryAfter(result.response()));
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
        var sessionResult = http.send(createSession, HttpResponse.BodyHandlers.ofByteArray());
        ancillary++;
        had429 |= sessionResult.response().statusCode() == 429;
        record(db, runId, uploadGuid, uploadIndex, ApiPhase.UPLOAD_SESSION_CREATE, true, false, "POST",
                RequestUrlMetrics.fromUri(createSession.uri()),
                sessionResult.response().statusCode(), sessionResult.timing(), threadMode, 1, null);
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

            NetworkTiming partTiming = new NetworkTiming();
            TimedBodyPublisher publisher = new TimedBodyPublisher(part, partTiming);
            HttpRequest partReq = HttpRequest.newBuilder(URI.create(uploadPartUrl))
                    .header("Authorization", uploadAuthHeader())
                    .header("Digest", "sha=" + sha1)
                    .header("Content-Type", "application/octet-stream")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(part))
                    .build();
            long partStart = System.nanoTime();
            var partResult = http.send(partReq, HttpResponse.BodyHandlers.ofByteArray());
            partTiming.transferMs = (System.nanoTime() - partStart) / 1_000_000.0;
            partTiming.requestBytes = len;
            partTiming.durationMs = partTiming.transferMs;
            primaryMs += partTiming.transferMs;
            had429 |= partResult.response().statusCode() == 429;
            record(db, runId, uploadGuid, uploadIndex, ApiPhase.UPLOAD_PART, false, true, "PUT",
                    RequestUrlMetrics.fromUri(partReq.uri()),
                    partResult.response().statusCode(), partTiming, threadMode, 1, null,
                    chunkIndex, offset, len);
            if (partResult.response().statusCode() != 200) {
                throw new IOException("Part upload failed: " + partResult.response().statusCode());
            }
        }

        StringBuilder shaArray = new StringBuilder("[");
        for (int i = 0; i < partSha1s.size(); i++) {
            if (i > 0) shaArray.append(',');
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
        var commitResult = http.send(commitReq, HttpResponse.BodyHandlers.ofByteArray());
        ancillary++;
        had429 |= commitResult.response().statusCode() == 429;
        record(db, runId, uploadGuid, uploadIndex, ApiPhase.UPLOAD_COMMIT, true, false, "POST",
                RequestUrlMetrics.fromUri(commitReq.uri()),
                commitResult.response().statusCode(), commitResult.timing(), threadMode, 1, null);
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

    private void record(MetricsDatabase db, String runId, String uploadGuid, Integer uploadIndex,
                          ApiPhase phase, boolean ancillary, boolean primary, String method, String url,
                          int status, NetworkTiming timing, String threadMode, int attempt, Integer retryAfter) throws Exception {
        record(db, runId, uploadGuid, uploadIndex, phase, ancillary, primary, method, url, status, timing, threadMode, attempt, retryAfter, null, null, null);
    }

    private void record(MetricsDatabase db, String runId, String uploadGuid, Integer uploadIndex,
                          ApiPhase phase, boolean ancillary, boolean primary, String method, String url,
                          int status, NetworkTiming timing, String threadMode, int attempt, Integer retryAfter,
                          Integer chunkIndex, Long chunkOffset, Integer chunkLength) throws Exception {
        db.insertApiCall(new ApiCallRecord(
                runId, uploadGuid, null, uploadIndex, phase, chunkIndex, chunkOffset, chunkLength,
                config.useChunkedUpload() ? "CHUNKED" : "SINGLE_STREAM",
                ancillary, primary, Instant.now(), method, url, status, timing, threadMode, attempt,
                status == 429, retryAfter, status >= 400 ? "HTTP " + status : null));
    }

    /** Same as SDK {@code uploadWithPreflightCheck}: main access token on regional host. */
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

    private static String extractJsonFieldOptional(String json, String field) {
        Pattern p = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json);
        if (m.find()) {
            String v = m.group(1);
            return v.isEmpty() ? null : v;
        }
        return null;
    }

    private static String extractNested(String json, String object, String field) {
        int idx = json.indexOf("\"" + object + "\"");
        if (idx < 0) throw new IllegalStateException("Missing " + object);
        String sub = json.substring(idx);
        return extractJsonField(sub, field);
    }

    private static Integer extractRetryAfter(java.net.http.HttpResponse<?> response) {
        return response.headers().firstValue("retry-after").map(Integer::parseInt).orElse(null);
    }

    @Override
    public void close() {
        http.close();
    }

    public record UploadResult(String boxFileId, double uploadDurationMs, double endToEndMs,
                               int chunkCount, int ancillaryCalls, boolean had429, int parts) {}
}
