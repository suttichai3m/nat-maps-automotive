package app.morphe.extension.mapsautomotive.patches;
final class NatSplitMath {
 private NatSplitMath(){}
 static int side(int width,int height,int minMap,int minSide){if(width<=0||height<=0||width<=height||minMap<=0||minSide<=0)return 0;int s=width/4;return s>=minSide&&width-s>=minMap?s:0;}
 static boolean fresh(long now,long at,long max){return at>=0&&now>=at&&now-at<=max;}
 static double speed(double v,long now,long at,long max){return fresh(now,at,max)&&Double.isFinite(v)&&v>=0&&v<=250?v:Double.NaN;}
 static double turn(double before,double after,double kmh,long interval){if(!Double.isFinite(before)||!Double.isFinite(after)||before<0||before>=360||after<0||after>=360||!Double.isFinite(kmh)||kmh<5||interval<300||interval>5000)return Double.NaN;return Math.max(-30,Math.min(30,((after-before+540)%360)-180));}
}
