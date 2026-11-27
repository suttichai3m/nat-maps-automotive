package app.morphe.extension.mapsautomotive.patches;
final class NatSplitMath {
 private NatSplitMath(){}
 // The footer is owned by NAT, not by the BYD system bar.
 static int viewportHeight(int height,int footer,int minimum){return height>0&&footer>0&&minimum>0&&height-footer>=minimum?height-footer:0;}
 static boolean geometry(int actualW,int actualH,int expectedW,int expectedH,int tolerance){return actualW>0&&actualH>0&&expectedW>0&&expectedH>0&&tolerance>=0&&Math.abs((long)actualW-expectedW)<=tolerance&&Math.abs((long)actualH-expectedH)<=tolerance;}
 // Only undo a cloned LayoutParams when it still carries our exact imposed geometry.
 static boolean ownsGeometry(int width,int height,int gravity,int imposedWidth,int imposedHeight,int imposedGravity){return imposedWidth>0&&imposedHeight>0&&width==imposedWidth&&height==imposedHeight&&gravity==imposedGravity;}
 static Integer current(double value){return Double.isFinite(value)&&value>=0&&value<=100?(int)Math.round(value):null;}
 static Integer arrival(boolean matched,Integer finalSoc,Integer nativeSoc){return matched&&validSoc(finalSoc)?finalSoc:validSoc(nativeSoc)?nativeSoc:null;}
 static boolean validSoc(Integer value){return value!=null&&value>=-999&&value<=100;}
 static String percent(Integer v){return v==null?"—":v+"%";}
 static boolean keepViewport(boolean evidence,long now,long lastEvidence){return evidence||fresh(now,lastEvidence,5000);}
 // A held display is only a short, labelled bridge over a native text refresh.
 static boolean holdArrival(long now,long at,long oldSession,long newSession,double oldKm,double newKm,String oldEta,String newEta){
  return fresh(now,at,5000)&&oldSession>=0&&oldSession==newSession&&Double.isFinite(oldKm)&&Double.isFinite(newKm)&&oldKm>=0&&newKm>=0
   &&Math.abs(oldKm-newKm)<=Math.max(.2,oldKm*.01)&&oldEta!=null&&oldEta.equals(newEta);
 }
 static boolean moving(double speed){return Double.isFinite(speed)&&speed>1&&speed<=250;}

 static int side(int width,int height,int minMap,int minSide){if(width<=0||height<=0||width<=height||minMap<=0||minSide<=0)return 0;int s=width/4;return s>=minSide&&width-s>=minMap?s:0;}
 static boolean fresh(long now,long at,long max){return at>=0&&now>=at&&now-at<=max;}
 static double speed(double v,long now,long at,long max){return fresh(now,at,max)&&Double.isFinite(v)&&v>=0&&v<=250?v:Double.NaN;}
 static double turn(double before,double after,double kmh,long interval){if(!Double.isFinite(before)||!Double.isFinite(after)||before<0||before>=360||after<0||after>=360||!Double.isFinite(kmh)||kmh<5||kmh>250||interval<300||interval>5000)return Double.NaN;return Math.max(-30,Math.min(30,((after-before+540)%360)-180));}
}
