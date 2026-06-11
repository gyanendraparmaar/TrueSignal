package com.truesignal.cli.formatter;

import java.util.List;

public final class TableFormatter {

    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN = "\u001B[36m";
    private static final String BOLD = "\u001B[1m";

    private TableFormatter() {}

    public static String formatTable(String[] headers, List<String[]> rows) {
        int cols = headers.length;
        int[] widths = new int[cols];
        for (int i = 0; i < cols; i++) {
            widths[i] = stripAnsi(headers[i]).length();
        }
        for (String[] row : rows) {
            for (int i = 0; i < Math.min(cols, row.length); i++) {
                widths[i] = Math.max(widths[i], stripAnsi(row[i]).length());
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(formatRow(headers, widths, true));
        sb.append(separator(widths));
        for (String[] row : rows) {
            sb.append(formatRow(row, widths, false));
        }
        return sb.toString();
    }

    private static String formatRow(String[] cells, int[] widths, boolean bold) {
        StringBuilder sb = new StringBuilder("  ");
        for (int i = 0; i < widths.length; i++) {
            String cell = i < cells.length ? cells[i] : "";
            int padding = widths[i] - stripAnsi(cell).length();
            if (bold) {
                sb.append(BOLD).append(cell).append(RESET);
            } else {
                sb.append(cell);
            }
            sb.append(" ".repeat(Math.max(0, padding)));
            if (i < widths.length - 1) sb.append("  ");
        }
        sb.append("\n");
        return sb.toString();
    }

    private static String separator(int[] widths) {
        StringBuilder sb = new StringBuilder("  ");
        for (int i = 0; i < widths.length; i++) {
            sb.append("─".repeat(widths[i]));
            if (i < widths.length - 1) sb.append("──");
        }
        sb.append("\n");
        return sb.toString();
    }

    private static String stripAnsi(String s) {
        return s.replaceAll("\u001B\\[[;\\d]*m", "");
    }

    public static String statusIcon(String status) {
        return switch (status) {
            case "UP" -> GREEN + "✓ UP" + RESET;
            case "DOWN" -> RED + "✗ DOWN" + RESET;
            case "DEGRADED" -> YELLOW + "! DEGRADED" + RESET;
            default -> "? UNKNOWN";
        };
    }

    public static String nodeStatusIcon(String status) {
        return switch (status) {
            case "ALIVE" -> GREEN + "● ALIVE" + RESET;
            case "SUSPECT" -> YELLOW + "● SUSPECT" + RESET;
            case "DEAD" -> RED + "● DEAD" + RESET;
            default -> "● " + status;
        };
    }

    public static String formatDuration(long seconds) {
        if (seconds < 0) return "—";
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) return h + "h " + m + "m " + s + "s";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }

    public static String formatUptime(double percent) {
        String formatted = String.format("%.2f%%", percent);
        if (percent >= 99.9) return GREEN + formatted + RESET;
        if (percent >= 95.0) return YELLOW + formatted + RESET;
        return RED + formatted + RESET;
    }

    public static String colorize(String text, String color) {
        return color + text + RESET;
    }

    public static String header(String text) {
        return "\n" + BOLD + CYAN + "  " + text + RESET + "\n";
    }
}
