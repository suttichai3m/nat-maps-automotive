package app.morphe.extension.mapsautomotive.patches;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Only distance + ETA + one arrival SOC; bare percentages are not navigation summaries. */
final class NatNavigationSummary {
    private static final String SEP = "(?:\\s*[·•・|]\\s*|\\s+)";
    private static final String DIST = "[0-9]+(?:[.,][0-9]+)?\\s*(?:กม\\.?|km|mi|เมตร|ม\\.?|m)";
    private static final String ETA = "(?:(?:ถึง|Arrive at|Arrival at|ETA)\\s*)?(?:[01]?[0-9]|2[0-3]):[0-5][0-9](?:\\s*(?:น\\.?|AM|PM))?";
    private static final String DURATION = "[0-9]+\\s*(?:ชม\\.?|h|hr|hours?)(?:\\s*[0-9]+\\s*(?:นาที|min|minutes?))?|[0-9]+\\s*(?:นาที|min|minutes?)";
    private static final String LABEL = "(?:Arrival battery|Arrive with|Battery on arrival|แบตเมื่อถึง(?:ปลายทาง)?|แบตเตอรี่เมื่อถึง(?:ปลายทาง)?|ไปถึงโดยเหลือ)";
    private static final String ICON = "(?:\\uFFFC|🔋)";
    private static final Pattern NAV = Pattern.compile("(?iu)^\\s*(?:(?:"+DURATION+")"+SEP+")?"+DIST+SEP+ETA+SEP+"(?:(?:"+LABEL+")\\s*[:：]?\\s*)?(?:"+ICON+"\\s*)?("+NatSignedSoc.TOKEN+")(?:\\s|\\uFFFC|🔋)*$");
    private static final Pattern SOC = Pattern.compile("(?iu)^\\s*(?:(?:"+LABEL+")\\s*[:：]?\\s*)?(?:"+ICON+"\\s*)?("+NatSignedSoc.TOKEN+")(?:\\s|\\uFFFC|🔋)*$");
    private static final Pattern EXPLICIT = Pattern.compile("(?iu)^\\s*"+LABEL+"\\s*[:：]?.*$");
    static boolean isExplicitSoc(CharSequence text) {
        return EXPLICIT.matcher(normalize(text)).matches() && parseSocPart(text)!=null;
    }
    static String normalize(CharSequence text) {
        return text == null ? "" : text.toString().replace('\u00a0',' ').replace('\u202f',' ')
            .replaceAll("[\\u200e\\u200f\\u202a-\\u202e\\u2066-\\u2069]", "").trim();
    }
    // Keep the previous preview/native-span classification unchanged unless a frame actually owns the source.
    private static final String LEGACY_SEP="\\s*[·•・]\\s*";
    private static final Pattern LEGACY=Pattern.compile("(?iu)^\\s*"+DIST+LEGACY_SEP+"(?:[01]?[0-9]|2[0-3]):[0-5][0-9](?:\\s*(?:น\\.?|AM|PM))?"+LEGACY_SEP+"(?:\\uFFFC\\s*)?("+NatSignedSoc.TOKEN+")(?:\\s|\\uFFFC|🔋)*$");
    static Integer parseLegacy(CharSequence text) {
        Matcher m=LEGACY.matcher(normalize(text));
        return m.matches()?NatSignedSoc.parse(m.group(1)):null;
    }
    private static final Pattern KM=Pattern.compile("(?iu)(?<![0-9.,])([0-9]+(?:[.,][0-9]+)?)\\s*(กม\\.?|km|mi|เมตร|ม\\.?|m)(?![\\p{L}])");
    static double routeKm(CharSequence text){
        Matcher m=KM.matcher(normalize(text));if(!m.find())return Double.NaN;
        String n=m.group(1),unit=m.group(2).toLowerCase(java.util.Locale.ROOT);
        if(m.find())return Double.NaN;
        try{double d=Double.parseDouble(n.replace(',','.'));return unit.equals("m")||unit.equals("ม")||unit.equals("ม.")||unit.equals("เมตร")?d/1000:unit.equals("mi")?d*1.609344:d;}catch(NumberFormatException e){return Double.NaN;}
    }
    static Integer parse(CharSequence text) {
        Matcher m = NAV.matcher(normalize(text));
        return m.matches() ? NatSignedSoc.parse(m.group(1)) : null;
    }
    static Integer parseSocPart(CharSequence text) {
        Matcher m = SOC.matcher(normalize(text));
        return m.matches() ? NatSignedSoc.parse(m.group(1)) : null;
    }
    // Split summaries are permitted only within one compact native status panel, checked by the caller.
    static Integer parseParts(java.util.List<String> parts) {
        if (parts.size()<2 || parts.size()>6) return null;
        return parse(String.join(" ",parts));
    }
    private static final Pattern DIST_ONLY=Pattern.compile("(?iu)^"+DIST+"$");
    private static final Pattern ETA_ONLY=Pattern.compile("(?iu)^"+ETA+"$");
    private static final Pattern DURATION_ONLY=Pattern.compile("(?iu)^"+DURATION+"$");
    private static final Pattern DURATION_DIST_ETA=Pattern.compile("(?iu)^"+DURATION+SEP+DIST+SEP+ETA+"$");
    private static final Pattern DIST_ETA=Pattern.compile("(?iu)^"+DIST+SEP+ETA+"$");
    private static final Pattern LABEL_ONLY=Pattern.compile("(?iu)^"+LABEL+"\\s*[:：]?$");
    static Integer parsePanelParts(java.util.List<String> parts) {
        Integer exact=parseParts(parts);
        if(exact!=null) return exact;
        if(parts.size()<2 || parts.size()>6) return null;
        int distances=0,etas=0,durations=0,labels=0; Integer soc=null;
        for(String raw:parts) {
            String piece=normalize(raw).replaceAll("^[·•・|\\s]+|[·•・|\\s]+$", "");
            if(piece.isEmpty()) continue;
            Integer value=parseSocPart(piece);
            if(value!=null) { if(soc!=null) return null; soc=value; }
            else if(DURATION_DIST_ETA.matcher(piece).matches()) { durations++; distances++; etas++; }
            else if(DIST_ETA.matcher(piece).matches()) { distances++; etas++; }
            else if(DIST_ONLY.matcher(piece).matches()) distances++;
            else if(ETA_ONLY.matcher(piece).matches()) etas++;
            else if(DURATION_ONLY.matcher(piece).matches()) durations++;
            else if(LABEL_ONLY.matcher(piece).matches()) labels++;
            else return null;
        }
        return distances==1 && etas==1 && durations<=1 && labels<=1?soc:null;
    }
}
