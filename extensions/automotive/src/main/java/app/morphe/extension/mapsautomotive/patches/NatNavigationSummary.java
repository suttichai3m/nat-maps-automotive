package app.morphe.extension.mapsautomotive.patches;
import java.util.regex.*;
final class NatNavigationSummary {
    private static final Pattern NAV = Pattern.compile("(?iu)^\\s*[0-9]+(?:[.,][0-9]+)?\\s*(?:กม\\.?|km|mi|เมตร|m)\\s*[·•・]\\s*[0-9]{1,2}:[0-9]{2}(?:\\s*(?:น\\.?|AM|PM))?\\s*[·•・]\\s*([+−-]?[0-9]{1,3}\\s*[%％])(?:\\s|\\uFFFC|\\u200E|\\u200F)*$");
    static boolean hasRoute(CharSequence text) { return text != null && ROUTE.matcher(normalize(text)).matches(); }
    private static final Pattern ROUTE = Pattern.compile("(?iu)^\\s*[0-9]+(?:[.,][0-9]+)?\\s*(?:กม\\.?|km|mi|เมตร|m)\\s*[·•・]\\s*[0-9]{1,2}:[0-9]{2}(?:\\s*(?:น\\.?|AM|PM))?(?:\\s*[·•・]\\s*(?:--[%％]|[+−-]?[0-9]{1,3}\\s*[%％]))?(?:\\s|\\uFFFC|\\u200E|\\u200F)*$");
    static String normalize(CharSequence text) {
        return text.toString().replace('\u00a0',' ').replace('\u202f',' ')
            .replaceAll("[\\p{Cf}\\uFFFC]", "").replaceAll("[\\s]+", " ").trim();
    }
    static boolean splitRoute(CharSequence text) {
        if(text==null) return false;
        String t=normalize(text);
        return hasRoute(t) || hasRoute(t.replaceAll("(?iu)(กม\\.?|km|mi|เมตร|m)\\s+(?=[0-9]{1,2}:)", "$1 · ").replaceAll("(?iu)([0-9]{1,2}:[0-9]{2}(?:\\s*(?:น\\.?|AM|PM))?)\\s+(?=[+−-]?[0-9]{1,3}\\s*[%％])", "$1 · "));
    }
    static Integer parse(CharSequence text) { if(text==null)return null; Matcher m=NAV.matcher(normalize(text)); return m.matches()?NatSignedSoc.parse(m.group(1)):null; }
}
