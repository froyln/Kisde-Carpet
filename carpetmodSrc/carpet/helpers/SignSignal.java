// Adapted from Void-Skeleton/Carpet-Vastech-Addition; LGPL-3.0.
package carpet.helpers;

public final class SignSignal {
    public static int parse(String text) {
        try {
            int radix = 10;
            if (text.startsWith("0b")) { radix = 2; text = text.substring(2); }
            else if (text.startsWith("0x")) { radix = 16; text = text.substring(2); }
            else if (text.length() > 1 && text.startsWith("0")) { radix = 8; text = text.substring(1); }
            return Math.max(0, Math.min(15, Integer.parseInt(text, radix)));
        } catch (NumberFormatException e) { return 0; }
    }
}
