package com.boxuploadperf.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PdfPayloadGenerator {

    /**
     * Builds a valid minimal PDF, then pads to {@code targetSizeBytes} when needed (fast vs per-page growth).
     */
    public long generate(Path output, long targetSizeBytes, String runId) throws IOException {
        Files.createDirectories(output.getParent());
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("Box Upload Performance Framework — run " + runId);
                cs.newLineAtOffset(0, -14);
                cs.showText("Target payload size: " + targetSizeBytes + " bytes");
                cs.newLineAtOffset(0, -14);
                cs.showText("Lorem ipsum dolor sit amet, consectetur adipiscing elit. ".repeat(40));
                cs.endText();
            }
            doc.save(output.toFile());
        }
        long actual = Files.size(output);
        if (actual < targetSizeBytes) {
            padFile(output, targetSizeBytes - actual);
        } else if (actual > targetSizeBytes) {
            truncateFile(output, targetSizeBytes);
        }
        return Files.size(output);
    }

    /**
     * @return true when an on-disk file is reused (size within 2% of target).
     */
    public boolean tryReuseExisting(Path output, long targetSizeBytes) throws IOException {
        if (!Files.isRegularFile(output)) {
            return false;
        }
        long size = Files.size(output);
        return size >= targetSizeBytes * 0.98 && size <= targetSizeBytes * 1.02;
    }

    private static void padFile(Path path, long extraBytes) throws IOException {
        if (extraBytes <= 0) {
            return;
        }
        try (OutputStream out = Files.newOutputStream(path,
                java.nio.file.StandardOpenOption.APPEND)) {
            byte[] chunk = new byte[8192];
            long remaining = extraBytes;
            while (remaining > 0) {
                int n = (int) Math.min(chunk.length, remaining);
                out.write(chunk, 0, n);
                remaining -= n;
            }
        }
    }

    private static void truncateFile(Path path, long targetSizeBytes) throws IOException {
        try (var ch = Files.newByteChannel(path, java.nio.file.StandardOpenOption.WRITE)) {
            ch.truncate(targetSizeBytes);
        }
    }
}
