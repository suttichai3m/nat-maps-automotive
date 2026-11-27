package app.morphe.extension.mapsautomotive.patches;
import java.util.Locale;
final class NatMinimalEvRules {
    private NatMinimalEvRules(){}
    // Keep ownership only while the visible EV result context remains proven.
    static boolean keepSearch(boolean opened,boolean confirmed,boolean results,boolean evidence,boolean map,long age){
        if(!opened||!map||age<0)return false;
        if(confirmed)return results&&evidence;
        return age<=15000;
    }
    static boolean confirmedSearch(boolean opened,boolean results,boolean evidence,boolean map){return opened&&results&&evidence&&map;}
    static String norm(CharSequence s){return s==null?"":s.toString().replace('\u00a0',' ').replaceAll("[\\u200e\\u200f\\u202a-\\u202e\\u2066-\\u2069]","").trim().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);}
    static boolean ev(CharSequence s){String n=norm(s);return n.equals("ev charging")||n.equals("ev charging stations")||n.equals("charging stations")||n.equals("charging station")||n.equals("electric vehicle charging station")||n.equals("การชาร์จ ev")||n.equals("สถานีชาร์จ")||n.equals("สถานีชาร์จ ev")||n.equals("สถานีชาร์จรถยนต์ไฟฟ้า");}
    static boolean along(CharSequence s){String n=norm(s);return n.equals("search along your route")||n.equals("search along route")||n.equals("ค้นหาตามเส้นทาง")||n.equals("ค้นหาตามเส้นทางของคุณ")||n.equals("ค้นหาระหว่างทาง");}
    // Exact action labels only; do not treat the EV search category as a layer switch.
    static boolean explicitShow(CharSequence s){String n=norm(s);return n.equals("show ev charging stations")||n.equals("show charging stations")||n.equals("แสดงสถานีชาร์จ")||n.equals("แสดงสถานีชาร์จ ev")||n.equals("แสดงสถานีชาร์จรถยนต์ไฟฟ้า");}
    static int layerAction(boolean map,boolean results,int show,int hide){
        if(!map||show<0||hide<0||show>1||hide>1||show+hide!=1)return 0;
        if(hide==1)return -1;
        return results?0:1;
    }
    static boolean explicitHide(CharSequence s){String n=norm(s);return n.equals("hide ev charging stations")||n.equals("hide charging stations")||n.equals("ซ่อนสถานีชาร์จ")||n.equals("ซ่อนสถานีชาร์จ ev");}
    static boolean closeSearch(CharSequence s){String n=norm(s);return n.equals("close search")||n.equals("clear search results")||n.equals("ปิดการค้นหา")||n.equals("ล้างผลการค้นหา");}
    static boolean allowClose(boolean opened,boolean results,boolean evEvidence,boolean exactClose){return opened&&results&&evEvidence&&exactClose;}
}
