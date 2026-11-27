package app.morphe.extension.mapsautomotive.patches;
import java.util.Locale;
final class NatV35Rules {
 private NatV35Rules(){}
 static long recoveryDelay(long last,long now,long gap){return last>0&&now>=last?Math.max(0,gap-(now-last)):0;}
 static String climate(int state,int unit,int c){return state==0?"OFF":state==1&&unit==1&&c>=17&&c<=33?c+"°C":"—";}
 static String psi(int kpa){return kpa>0&&kpa<=600?String.format(Locale.US,"%.1f",kpa/6.894757293):"—";}
}
