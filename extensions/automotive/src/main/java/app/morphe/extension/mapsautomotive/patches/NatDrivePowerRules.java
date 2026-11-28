package app.morphe.extension.mapsautomotive.patches;
/** Documented V1.0.5 engine power range; no unit guessing or saturation. */
final class NatDrivePowerRules {
 static boolean valid(double kw){return Double.isFinite(kw)&&kw>=-100&&kw<=300;}
 static boolean fresh(long now,long stamp){return stamp>=0&&now>=stamp&&now-stamp<=1800;}
 static boolean moving(double speed){return Double.isFinite(speed)&&speed>1&&speed<=250;}
 static boolean regen(double kw,double speed){return valid(kw)&&kw<0&&moving(speed);}
 static String label(double kw,double speed){return !valid(kw)?"POWER / REGEN":regen(kw,speed)?"REGEN • SDK":kw==0?"0 • SDK":kw<0?"NEGATIVE • SDK":"POWER • SDK";}
}
