package app.morphe.extension.mapsautomotive.patches;
import java.util.regex.Pattern;
final class NatSignedSoc {
    static final String TOKEN = "[+\u2212-]?[0-9]{1,3}\\s*[%\uff05]";
    private static final Pattern EXACT = Pattern.compile(TOKEN);
    static Integer parse(String text) {
        if (text == null || !EXACT.matcher(text).matches()) return null;
        try {
            int value = Integer.parseInt(text.replace('\u2212', '-').replaceAll("[\\s%\uff05]", ""));
            return value <= 100 ? value : null;
        } catch (NumberFormatException e) { return null; }
    }
    static int bar(int value) { return Math.max(0, Math.min(100, value)); }
}
