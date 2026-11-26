package app.morphe.extension.mapsautomotive.patches;
import java.util.regex.*;
final class NatNavigationSummary {
    private static final Pattern NAV = Pattern.compile("(?iu)^\\s*[0-9]+(?:[.,][0-9]+)?\\s*(?:กม\\.?|km|mi|เมตร|m)\\s*[·•・]\\s*[0-9]{1,2}:[0-9]{2}(?:\\s*(?:น\\.?|AM|PM))?\\s*[·•・]\\s*([+−-]?[0-9]{1,3}\\s*[%％])(?:\\s|\\uFFFC|\\u200E|\\u200F)*$");
    static Integer parse(CharSequence text) { if(text==null)return null; Matcher m=NAV.matcher(text.toString().replace("\u00a0", " ")); return m.matches()?NatSignedSoc.parse(m.group(1)):null; }
}
