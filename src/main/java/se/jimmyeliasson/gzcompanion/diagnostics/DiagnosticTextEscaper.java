package se.jimmyeliasson.gzcompanion.diagnostics;

/**
 * Turns arbitrary text (e.g. a raw vanilla TAB header/display string) into a form where every
 * invisible or unusual character is visible as an explicit escape sequence - purely for human
 * diagnostics. This exists because GameZone's real TAB text can contain formatting-adjacent
 * Unicode, non-breaking spaces, or zero-width characters that look identical to an ordinary space
 * or to nothing at all when printed normally, which can silently break structural parsing in ways
 * that are otherwise invisible to a human reading a screenshot or a pasted log line.
 *
 * <p>Ordinary printable characters (including non-ASCII letters like å/ä/ö and ordinary symbols)
 * are left untouched - only whitespace other than a plain space, and control/format/surrogate/
 * private-use/unassigned characters, are escaped.
 */
public final class DiagnosticTextEscaper {
    private DiagnosticTextEscaper() {}

    /** Never throws; {@code null} is represented as the literal string {@code "null"}. */
    public static String escape(String text) {
        if (text == null) return "null";

        StringBuilder result = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\n' -> result.append("\\n");
                case '\t' -> result.append("\\t");
                case '\r' -> result.append("\\r");
                default -> {
                    if (isInvisibleOrUnusual(c)) {
                        result.append(String.format("\\u%04X", (int) c));
                    } else {
                        result.append(c);
                    }
                }
            }
        }
        return result.toString();
    }

    private static boolean isInvisibleOrUnusual(char c) {
        if (c == ' ') return false;
        if (Character.isWhitespace(c) || Character.isSpaceChar(c)) return true;

        int type = Character.getType(c);
        return type == Character.CONTROL
                || type == Character.FORMAT
                || type == Character.SURROGATE
                || type == Character.PRIVATE_USE
                || type == Character.UNASSIGNED;
    }
}
