package com.boxuploadperf.charts;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;

/** Renders line charts (Chart.js-style) to a bitmap for embedding in PDF reports. */
final class PdfLineChartImage {

    private static final Color BG = Color.WHITE;
    private static final Color GRID = new Color(229, 231, 235);
    private static final Color AXIS = new Color(107, 114, 128);
    private static final Color TEXT = new Color(17, 24, 39);

    private PdfLineChartImage() {}

    static BufferedImage render(String title, String xAxisTitle, String yAxisTitle,
                                String seriesLabel, List<Double> yValues, Color seriesColor,
                                int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(BG);
            g.fillRect(0, 0, width, height);

            int padL = 56;
            int padR = 24;
            int padT = 44;
            int padB = 52;
            int plotX = padL;
            int plotY = padT;
            int plotW = width - padL - padR;
            int plotH = height - padT - padB;

            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            g.setColor(TEXT);
            g.drawString(title, padL, 26);

            if (yValues == null || yValues.isEmpty()) {
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
                g.setColor(AXIS);
                g.drawString("No data", plotX + plotW / 2 - 28, plotY + plotH / 2);
                return image;
            }

            int n = yValues.size();
            double yMin = 0;
            double yMax = yValues.stream().mapToDouble(Double::doubleValue).max().orElse(1);
            if (yMax <= yMin) {
                yMax = yMin + 1;
            }
            double yPad = (yMax - yMin) * 0.08;
            yMax += yPad;

            int gridLines = 5;
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            g.setStroke(new BasicStroke(1f));
            for (int i = 0; i <= gridLines; i++) {
                int gy = plotY + (int) (plotH * (1 - (double) i / gridLines));
                g.setColor(GRID);
                g.drawLine(plotX, gy, plotX + plotW, gy);
                double tickVal = yMin + (yMax - yMin) * i / gridLines;
                g.setColor(AXIS);
                String tick = formatTick(tickVal);
                g.drawString(tick, plotX - 8 - g.getFontMetrics().stringWidth(tick), gy + 4);
            }

            g.setColor(GRID);
            g.drawRect(plotX, plotY, plotW, plotH);

            Path2D path = new Path2D.Double();
            for (int i = 0; i < n; i++) {
                float px = plotX + (n == 1 ? plotW / 2f : (plotW * i / (float) (n - 1)));
                float py = plotY + plotH - (float) ((yValues.get(i) - yMin) / (yMax - yMin) * plotH);
                if (i == 0) {
                    path.moveTo(px, py);
                } else {
                    path.lineTo(px, py);
                }
            }
            g.setColor(new Color(seriesColor.getRed(), seriesColor.getGreen(), seriesColor.getBlue(), 40));
            Path2D fill = new Path2D.Double(path);
            fill.lineTo(plotX + (n == 1 ? plotW / 2f : plotW), plotY + plotH);
            fill.lineTo(plotX, plotY + plotH);
            fill.closePath();
            g.fill(fill);

            g.setColor(seriesColor);
            g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(path);

            for (int i = 0; i < n; i++) {
                float px = plotX + (n == 1 ? plotW / 2f : (plotW * i / (float) (n - 1)));
                float py = plotY + plotH - (float) ((yValues.get(i) - yMin) / (yMax - yMin) * plotH);
                g.setColor(seriesColor);
                g.fillOval((int) px - 3, (int) py - 3, 6, 6);
            }

            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            g.setColor(AXIS);
            int xLabelY = plotY + plotH + 28;
            g.drawString(xAxisTitle, plotX + plotW / 2 - g.getFontMetrics().stringWidth(xAxisTitle) / 2, xLabelY);

            g.rotate(-Math.PI / 2);
            g.drawString(yAxisTitle, -(plotY + plotH / 2 + g.getFontMetrics().stringWidth(yAxisTitle) / 2), 14);
            g.rotate(Math.PI / 2);

            g.setColor(seriesColor);
            g.fillRect(plotX, height - 22, 12, 3);
            g.setColor(TEXT);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            g.drawString(seriesLabel, plotX + 16, height - 14);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static String formatTick(double v) {
        if (Math.abs(v) >= 1000) {
            return String.format(Locale.US, "%.0f", v);
        }
        if (Math.abs(v) >= 10) {
            return String.format(Locale.US, "%.1f", v);
        }
        return String.format(Locale.US, "%.2f", v);
    }
}
