package app.morphe.extension.mapsautomotive.patches;

final class NatDiagnosticMath {
    private NatDiagnosticMath(){}
    static double cpu(long cpuDelta,long elapsed,int cores){
        if(cpuDelta<0||elapsed<=0||cores<=0)return Double.NaN;
        double p=100.0*cpuDelta/elapsed/cores;
        return p<=100.0? p:Double.NaN;
    }
    static long age(long now,long stamp){return stamp<0||now<stamp?-1:now-stamp;}
    static boolean fresh(long now,long stamp,long max){long a=age(now,stamp);return a>=0&&a<=max;}
    static double usedMemory(long total,long available){return total>0&&available>=0&&available<=total?(total-available)/1048576.0:Double.NaN;}
    static double rate(long frames,long slow){return frames>0&&slow>=0&&slow<=frames?100.0*slow/frames:Double.NaN;}
    static String gps(boolean permission,Boolean enabled,boolean requested,long fixAge,boolean hasAccuracy,double accuracy,boolean gnssFresh,int count,int used){
        if(!permission)return "PERMISSION DENIED";
        if(enabled==null)return "UNKNOWN";
        if(!enabled)return "GPS DISABLED";
        if(!requested)return "LOCATION REQUEST UNAVAILABLE";
        if(fixAge>=0&&fixAge<=30000){
            if(!hasAccuracy||!Double.isFinite(accuracy)||accuracy<0)return "FIX / ACCURACY UNKNOWN";
            return accuracy>50?"FIX / LOW ACCURACY":"FIX";
        }
        if(fixAge>30000)return "STALE LOCATION";
        if(!gnssFresh)return "WAITING FOR GNSS";
        if(count<0||used<0||used>count)return "UNKNOWN";
        return count==0?"NO SATELLITES REPORTED":used==0?"NO FIX":"WAITING FOR LOCATION";
    }
    static double skyRadius(double elevation){return Double.isFinite(elevation)&&elevation>=0&&elevation<=90?(90-elevation)/90:Double.NaN;}
    static boolean angle(double azimuth,double elevation){return Double.isFinite(azimuth)&&azimuth>=0&&azimuth<360&&Double.isFinite(skyRadius(elevation));}
}
