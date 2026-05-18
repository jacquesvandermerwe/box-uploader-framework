package com.boxuploadperf.metrics;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UploadFailureClassifier {

    private static final Pattern STATUS_IN_MESSAGE = Pattern.compile("(?:failed|status)[:\\s]+(\\d{3})",
            Pattern.CASE_INSENSITIVE);

    private UploadFailureClassifier() {}

    public static UploadFailureReason classify(Throwable error, int lastStatusCode, boolean saw429) {
        if (error instanceof InterruptedException) {
            return UploadFailureReason.INTERRUPTED;
        }
        if (error instanceof SQLException) {
            return UploadFailureReason.METRICS_DB;
        }
        if (error instanceof FileSystemException || isLocalIo(error)) {
            return UploadFailureReason.LOCAL_IO;
        }
        int status = lastStatusCode > 0 ? lastStatusCode : parseStatusFromMessage(error);
        if (status == 429 || saw429 && status == 0 && error != null && messageContains429(error)) {
            return UploadFailureReason.HTTP_429;
        }
        if (status >= 500) {
            return UploadFailureReason.HTTP_5XX;
        }
        if (status >= 400) {
            return UploadFailureReason.HTTP_4XX;
        }
        if (error instanceof IOException) {
            return UploadFailureReason.NETWORK;
        }
        return UploadFailureReason.UNKNOWN;
    }

    public static String truncateMessage(Throwable error) {
        if (error == null) {
            return "";
        }
        String msg = error.getClass().getSimpleName();
        if (error.getMessage() != null && !error.getMessage().isBlank()) {
            msg = msg + ": " + error.getMessage();
        }
        return msg.length() <= 500 ? msg : msg.substring(0, 497) + "...";
    }

    private static boolean isLocalIo(Throwable error) {
        String m = error.getMessage();
        return m != null && (m.contains("Too many open files") || m.contains("No space left"));
    }

    private static int parseStatusFromMessage(Throwable error) {
        if (error == null || error.getMessage() == null) {
            return 0;
        }
        Matcher m = STATUS_IN_MESSAGE.matcher(error.getMessage());
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return 0;
    }

    private static boolean messageContains429(Throwable error) {
        return error.getMessage() != null && error.getMessage().contains("429");
    }
}
