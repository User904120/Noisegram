package org.cleargram.integration;

import android.text.Spanned;
import android.text.SpannedString;

/** Builds an immutable presentation prefix from the classifier's final trim boundary. */
public final class TelegramChannelFooterTrimPreparer {

    public enum Status { SUCCESS, FAIL_OPEN }

    public static final class Result {
        private static final Result FAIL_OPEN = new Result(Status.FAIL_OPEN, null, -1);
        private final Status status;
        private final CharSequence text;
        private final int trimBoundary;

        private Result(Status status, CharSequence text, int trimBoundary) {
            this.status = status;
            this.text = text;
            this.trimBoundary = trimBoundary;
        }

        public Status getStatus() { return status; }
        public CharSequence getText() { return text; }
        public int getTrimBoundary() { return trimBoundary; }
    }

    private TelegramChannelFooterTrimPreparer() { }

    public static Result prepare(CharSequence source, int trimBoundaryUtf16) {
        try {
            if (source == null || source.length() == 0 || trimBoundaryUtf16 < 0
                    || trimBoundaryUtf16 >= source.length()
                    || splitsSurrogatePair(source, trimBoundaryUtf16)) {
                return Result.FAIL_OPEN;
            }
            CharSequence projection = source instanceof Spanned
                    ? new SpannedString(source.subSequence(0, trimBoundaryUtf16))
                    : source.subSequence(0, trimBoundaryUtf16).toString();
            return new Result(Status.SUCCESS, projection, trimBoundaryUtf16);
        } catch (RuntimeException ignored) {
            return Result.FAIL_OPEN;
        }
    }

    private static boolean splitsSurrogatePair(CharSequence source, int offset) {
        return offset > 0 && offset < source.length()
                && Character.isHighSurrogate(source.charAt(offset - 1))
                && Character.isLowSurrogate(source.charAt(offset));
    }
}
