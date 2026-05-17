package com.boxuploadperf.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PdfPayloadGenerator {

    public long generate(Path output, long targetSizeBytes, String runId) throws IOException {
        Files.createDirectories(output.getParent());
        try (PDDocument doc = new PDDocument()) {
            long written = 0;
            int pageNum = 0;
            while (written < targetSizeBytes) {
                PDPage page = new PDPage(PDRectangle.LETTER);
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(50, 700);
                    String text = "Box Upload Performance Framework — run " + runId + " — page " + (++pageNum)
                            + " — target bytes " + targetSizeBytes;
                    cs.showText(text);
                    cs.newLineAtOffset(0, -14);
                    cs.showText("Lorem ipsum dolor sit amet, consectetur adipiscing elit. ".repeat(20));
                    cs.endText();
                }
                written = estimateSize(doc);
            }
            doc.save(output.toFile());
        }
        long actual = Files.size(output);
        if (actual < targetSizeBytes * 0.98) {
            padFile(output, targetSizeBytes - actual);
        }
        return Files.size(output);
    }

    private static long estimateSize(PDDocument doc) {
        try {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            doc.save(baos);
            return baos.size();
        } catch (IOException e) {
            return doc.getNumberOfPages() * 4096L;
        }
    }

    private static void padFile(Path path, long extraBytes) throws IOException {
        byte[] existing = Files.readAllBytes(path);
        byte[] padded = new byte[(int) (existing.length + extraBytes)];
        System.arraycopy(existing, 0, padded, 0, existing.length);
        for (int i = existing.length; i < padded.length; i++) {
            padded[i] = ' ';
        }
        Files.write(path, padded);
    }
}
