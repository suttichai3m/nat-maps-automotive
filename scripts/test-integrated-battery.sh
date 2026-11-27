#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
D=$(mktemp -d); trap 'rm -rf "$D"' EXIT
P=extensions/automotive/src/main/java/app/morphe/extension/mapsautomotive/patches
cat > "$D/TestIntegrated.java" <<'JAVA'
package app.morphe.extension.mapsautomotive.patches;
import java.util.*;
class TestIntegrated {
 static NatBatteryPlacement.Box b(int x,int y,int w,int h){return new NatBatteryPlacement.Box(x,y,w,h);}
 static int cases=0;
 static void check(boolean v,String msg){if(!v)throw new AssertionError(msg);}
 static void parse(Integer expected,String... pieces){check(Objects.equals(expected,NatNavigationSummary.parsePanelParts(Arrays.asList(pieces))),Arrays.toString(pieces));cases++;}
 public static void main(String[] args){
  parse(9,"9 นาที","3.2 กม. · 21:16 น.","9%");
  parse(9,"9%","9 นาที","3.2 กม. · 21:16 น.");
  parse(45,"45%","แบตเมื่อถึง","3 นาที","1.5 กม. · 21:16 น.");
  parse(9,"9 นาที","3.2 กม.","21:16 น.","🔋9%");
  parse(9,"9%","9 นาที · 3.2 กม. · 21:16 น.");
  parse(45,"Arrival battery","45%","ETA 9:05 AM","1.5 km","3 min");
  parse(-45,"1.5 km","9:05","−45%");
  parse(0,"0%","500 เมตร","ถึง 00:05 น.");
  parse(100,"100%","1.5 km","9:05");
  parse(null,"1.5 km","9:05","45%","65%");
  parse(null,"45%","Current battery","1.5 km","9:05");
  parse(null,"45%","1.5 km");
  parse(null,"45%","9:05");
  parse(null,"45%","1.5 km","9:99");
  parse(null,"101%","1.5 km","9:05");
  parse(null,"45%","1.5 km","9:05","2 km");
  parse(null,"45%","1.5 km","9:05","9:06");
  parse(null,"45%","1.5 km","9:05","charging");
  parse(null,"45%","1.5 km","9:05","Arrival battery","แบตเมื่อถึง");
  // Reference-like wide bottom status panel; summary at left, small SOC at right, controls below.
  for(int scale:new int[]{1,2,3}){
   NatBatteryPlacement.Box panel=b(120*scale,440*scale,360*scale,124*scale);
   NatBatteryPlacement.Box soc=b(394*scale,482*scale,34*scale,20*scale);
   NatBatteryPlacement.Box time=b(130*scale,455*scale,136*scale,50*scale);
   NatBatteryPlacement.Box buttons=b(120*scale,530*scale,360*scale,34*scale);
   for(int w:new int[]{160,144,128}){
    List<NatBatteryPlacement.Box> opts=NatBatteryPlacement.integrated(panel,soc,w*scale,w*scale/2,4*scale);
    check(!opts.isEmpty(),"integrated candidates");
    for(NatBatteryPlacement.Box c:opts)check(panel.contains(c),"outside panel");
    check(opts.stream().anyMatch(c->c.contains(soc)&&!c.overlaps(time)&&!c.overlaps(buttons)),"no safe reference-like cell");cases++;
   }
  }
  NatBatteryPlacement.Box viewport=b(0,0,1280,720),panel=b(80,625,300,55),soc=b(300,640,35,20);
  check(NatBatteryPlacement.integrated(panel,soc,160,80,4).isEmpty(),"too short");cases++;
  List<NatBatteryPlacement.Box> adj=NatBatteryPlacement.adjacent(viewport,panel,soc,160,80,4);
  check(!adj.isEmpty()&&adj.get(0).y==541,"adjacent above");cases++;
  for(NatBatteryPlacement.Box c:adj)check(viewport.contains(c)&&!c.overlaps(panel),"adjacent collision");cases++;
  check(NatBatteryPlacement.adjacent(b(0,0,90,50),b(0,0,90,50),b(1,1,10,10),160,80,4).isEmpty(),"tiny screen");cases++;
  check(Objects.equals(16,NatNavigationSummary.parse("465 กม. · 14:21 น. · 16% 🔋")),"charging stop icon suffix");cases++;
  check(Objects.equals(-31,NatNavigationSummary.parse("754 กม. · 18:36 น. · -31% 🔋")),"negative route icon suffix");cases++;
  check(NatNavigationSummary.parse("465 กม. · 14:21 น. · 16% · 92%")==null,"ambiguous charging SOC rejected");cases++;
  check(Objects.equals(-61,NatNavigationSummary.parse("942 กม. · 21:21 น. · -61%")),"observed negative arrival");cases++;
  check(Objects.equals(92,NatNavigationSummary.parse("18 กม. · 09:13 น. · 92%")),"observed positive arrival");cases++;
  check(Objects.equals(16,NatNavigationSummary.parse("465 กม. · 14:21 น. · 16%")),"replanned route arrival");cases++;
  check(NatNavigationSummary.parse("942 กม. · 21:21 น. · -61% · 16%")==null,"do not confuse charge stop with destination");cases++;
  // Long Thai trip summary: reject candidates covering the ETA/distance prefix.
  for(int scale:new int[]{1,2,3}){
   NatBatteryPlacement.Box longPanel=b(0,0,360*scale,124*scale);
   NatBatteryPlacement.Box longSoc=b(215*scale,40*scale,48*scale,20*scale);
   NatBatteryPlacement.Box prefix=b(8*scale,20*scale,190*scale,40*scale);
   int selected=0;
   for(int w:new int[]{160,144,128,112}){
    List<NatBatteryPlacement.Box> cells=NatBatteryPlacement.rightCell(longPanel,longSoc,prefix,w*scale,w*scale/2,4*scale);
    check(w==160?cells.isEmpty():!cells.isEmpty(),"reject unsafe size, allow safe fallback");
    for(NatBatteryPlacement.Box c:cells){
     check(longPanel.contains(c),"right cell outside panel");
     check(c.x+c.w==356*scale,"right edge must stay fixed");
     check(!c.overlaps(prefix),"right cell covers ETA");
    }
    if(selected==0&&!cells.isEmpty())selected=w;
    cases++;
   }
   check(selected==144,"largest safe fallback must be 144dp");cases++;
   check(!NatBatteryPlacement.rightCell(longPanel,longSoc,null,160*scale,80*scale,4*scale).isEmpty(),"split SOC supports full size");cases++;
   check(NatBatteryPlacement.rightCell(longPanel,longSoc,longPanel,112*scale,56*scale,4*scale).isEmpty(),"fully occupied panel rejected");cases++;
  }
  check(NatBatteryPlacement.rightCell(b(0,0,90,50),b(20,10,20,10),null,112,56,4).isEmpty(),"no unsafe tiny cell");cases++;
  System.out.println("PASS: "+cases+" integrated summary parser/placement cases (pure JVM)");
 }
}
JAVA
javac --release 17 -encoding UTF-8 -d "$D" "$P/NatSignedSoc.java" "$P/NatNavigationSummary.java" "$P/NatBatteryPlacement.java" "$D/TestIntegrated.java"
java -cp "$D" app.morphe.extension.mapsautomotive.patches.TestIntegrated

cat > "$D/TestNatTripMath.java" <<'JAVA'
package app.morphe.extension.mapsautomotive.patches;
public class TestNatTripMath {
 static int n;
 static void check(boolean b){if(!b)throw new AssertionError("Trip case "+n);n++;}
 static boolean near(double a,double b){return Math.abs(a-b)<1e-6;}
 public static void main(String[] args){
  check(NatTripMath.linked(1,300001));check(!NatTripMath.linked(1,300002));check(!NatTripMath.linked(2,1));
  check(NatTripMath.contiguous(1,30001));check(!NatTripMath.contiguous(1,30002));
  check(near(NatTripMath.charge(82.56,20,80),49.536));
  try{NatTripMath.charge(82.56,80,20);throw new AssertionError();}catch(IllegalArgumentException ok){n++;}
  try{NatTripMath.charge(Double.NaN,20,80);throw new AssertionError();}catch(IllegalArgumentException ok){n++;}
  check(near(NatTripMath.averageKw(50000,49900,10000),36));
  check(near(NatTripMath.averageKw(49900,50000,10000),-36));
  check(Double.isNaN(NatTripMath.averageKw(50000,0,10000)));
  check(Double.isNaN(NatTripMath.averageKw(50000,49900,40000)));
  NatTripMath.Energy e=new NatTripMath.Energy();e.add(50000,49900,10000,.2);e.add(49900,50000,10000,.2);
  check(near(e.net(),0));check(near(e.down,.1));check(near(e.up,.1));check(near(e.km,.4));
  e.add(-1,100,10000,1);e.add(50000,0,10000,1);e.add(50000,49900,40000,1);check(e.pairs==2);
  System.out.println("PASS: "+n+" NAT trip math cases");
 }
}
JAVA
javac --release 17 -encoding UTF-8 -d "$D" "$P/NatTripMath.java" "$D/TestNatTripMath.java"
java -cp "$D" app.morphe.extension.mapsautomotive.patches.TestNatTripMath

cat > "$D/TestNatMinimalEv.java" <<'JAVA'
package app.morphe.extension.mapsautomotive.patches;
public class TestNatMinimalEv {
 static int n; static void check(boolean x){if(!x)throw new AssertionError("EV case "+n);n++;}
 public static void main(String[] args){
  check(NatMinimalEvRules.ev("การชาร์จ EV"));check(NatMinimalEvRules.ev("  EV charging stations "));check(NatMinimalEvRules.ev("สถานีชาร์จรถยนต์ไฟฟ้า"));
  check(!NatMinimalEvRules.ev("Station near charging stations"));check(!NatMinimalEvRules.ev("End navigation"));
  check(NatMinimalEvRules.along("Search along your route"));check(!NatMinimalEvRules.along("Search"));
  check(NatMinimalEvRules.explicitHide("Hide charging stations"));check(!NatMinimalEvRules.explicitHide("Hide route"));
  check(NatMinimalEvRules.closeSearch("Clear search results"));check(!NatMinimalEvRules.closeSearch("Clear route"));check(!NatMinimalEvRules.closeSearch("Back"));
  check(NatMinimalEvRules.allowClose(true,true,true,true));check(!NatMinimalEvRules.allowClose(false,true,true,true));check(!NatMinimalEvRules.allowClose(true,false,true,true));check(!NatMinimalEvRules.allowClose(true,true,false,true));check(!NatMinimalEvRules.allowClose(true,true,true,false));
  System.out.println("PASS: "+n+" minimal EV native control rules");
 }
}
JAVA
javac --release 17 -encoding UTF-8 -d "$D" "$P/NatMinimalEvRules.java" "$D/TestNatMinimalEv.java"
java -cp "$D" app.morphe.extension.mapsautomotive.patches.TestNatMinimalEv

cat > "$D/TestNatStability.java" <<'JAVA'
package app.morphe.extension.mapsautomotive.patches;
public class TestNatStability {
 static int n;static void check(boolean x){if(!x)throw new AssertionError("Stability case "+n);n++;}
 static boolean near(double a,double b){return Math.abs(a-b)<1e-6;}
 public static void main(String[] args){
  check(NatStabilityMath.fresh(30000,0));check(!NatStabilityMath.fresh(30001,0));check(!NatStabilityMath.fresh(0,1));
  check(NatStabilityMath.signedSoc(-40000,80000,-1)==-50);check(NatStabilityMath.signedSoc(0,80000,-1)==0);check(NatStabilityMath.signedSoc(40000,80000,49)==49);
  check(near(NatStabilityMath.remainingKwh(80000,-40000,0,true),120));
  check(near(NatStabilityMath.remainingKwh(80000,20000,60000,true),120));
  check(Double.isNaN(NatStabilityMath.remainingKwh(80000,20000,0,false)));check(Double.isNaN(NatStabilityMath.remainingKwh(-1,20000,0,true)));
  check(near(NatStabilityMath.rate(120,750),16));check(Double.isNaN(NatStabilityMath.rate(120,0)));
  check(near(NatStabilityMath.cost(5,7.5),37.5));check(Double.isNaN(NatStabilityMath.cost(5,Double.NaN)));check(Double.isNaN(NatStabilityMath.cost(-5,7)));
  check(NatStabilityMath.autoHide(false,-1,true,0));check(NatStabilityMath.autoHide(true,0,true,1));check(!NatStabilityMath.autoHide(true,1,true,1));check(!NatStabilityMath.autoHide(true,1,false,-1));
  check(!NatStabilityMath.moving(Double.NaN));check(!NatStabilityMath.moving(0));check(NatStabilityMath.moving(60));check(!NatStabilityMath.pair(31000,60,60));check(!NatStabilityMath.pair(10000,0,60));
  check(NatStabilityMath.matches(20,100,20,100));check(!NatStabilityMath.matches(20,100,20,200));check(!NatStabilityMath.matches(20,100,21,100));
  NatStabilityMath.Observed o=new NatStabilityMath.Observed();o.observe(50000,10000,40);o.observe(49900,20000,40);check(near(o.net,.1)&&o.pairs==1);o.observe(50100,30000,0);o.observe(50000,40000,40);check(near(o.net,.1)&&o.pairs==1);o.observe(49000,80000,40);check(o.pairs==1);o.reset();check(o.pairs==0&&o.net==0);
  System.out.println("PASS: "+n+" NAT V2.0.1 stability cases");
 }
}
JAVA
javac --release 17 -encoding UTF-8 -d "$D" "$P/NatStabilityMath.java" "$D/TestNatStability.java"
java -cp "$D" app.morphe.extension.mapsautomotive.patches.TestNatStability

cat > "$D/TestNatLiveEnergy.java" <<'JAVA'
package app.morphe.extension.mapsautomotive.patches;
public class TestNatLiveEnergy {
 static int n;static void check(boolean x){if(!x)throw new AssertionError("Live case "+n);n++;}
 static boolean near(double a,double b){return Math.abs(a-b)<1e-6;}
 public static void main(String[] args){
  NatLiveEnergy e=new NatLiveEnergy();
  check(Double.isNaN(e.read(0,true).soc));check(Double.isNaN(e.read(0,true).net));
  e.accept(10000,82,65000,1000,50,true);
  check(near(e.read(10000,true).soc,82));check(Double.isNaN(e.read(10000,true).power));
  e.accept(20000,81,64900,1001,50,true);
  NatLiveEnergy.Reading r=e.read(20000,true);
  check(near(r.power,36));check(near(r.net,.1));check(Double.isNaN(r.rate));check(near(r.km,.1));check(near(r.increase,0));check(r.pairs==1);
  e.accept(30000,81,64950,1002,50,true);r=e.read(30000,true);
  check(near(r.power,-18));check(near(r.net,.05));check(near(r.increase,.05));check(Double.isNaN(r.rate));
  e.accept(40000,82,66000,1002,0,true);r=e.read(40000,true);
  check(Double.isNaN(r.power));check(near(r.net,.05));check(near(r.increase,.05));
  e.accept(50000,82,65900,1003,50,true);check(e.read(50000,true).pairs==2);
  e.accept(90000,82,65500,1005,50,true);check(e.read(90000,true).pairs==2);
  e.accept(100000,81,60000,1006,50,true);check(Double.isNaN(e.read(100000,true).power));check(e.read(100000,true).pairs==2);
  e.accept(110000,81,59900,-1,50,true);r=e.read(110000,true);check(near(r.net,.15));check(Double.isNaN(r.rate));
  check(Double.isNaN(e.read(140001,true).soc));check(Double.isNaN(e.read(140001,true).net));
  e.accept(120000,80,59800,1007,50,false);check(near(e.read(120000,false).soc,80));check(Double.isNaN(e.read(120000,false).net));
  e.accept(130000,80,59700,1008,50,true);check(Double.isNaN(e.read(130000,true).net));
  e.accept(125000,99,59700,1008,50,true);check(near(e.read(130000,true).soc,80));
  e.accept(140000,101,59600,1009,50,true);check(Double.isNaN(e.read(140000,true).soc));
  e.endRoute();check(Double.isNaN(e.read(140000,true).net));
  check(!NatLiveEnergy.fresh(0,1));check(!NatLiveEnergy.moving(251));
  System.out.println("PASS: "+n+" NAT live energy cases");
 }
}
JAVA
javac --release 17 -encoding UTF-8 -d "$D" "$P/NatLiveEnergy.java" "$D/TestNatLiveEnergy.java"
java -cp "$D" app.morphe.extension.mapsautomotive.patches.TestNatLiveEnergy

cat > "$D/TestNatRelease.java" <<'JAVA'
package app.morphe.extension.mapsautomotive.patches;
public class TestNatRelease {
 static int n;static void check(boolean x){if(!x)throw new AssertionError("Release case "+n);n++;}
 static boolean near(double a,double b){return Math.abs(a-b)<1e-6;}
 public static void main(String[] args){
  check(Double.isNaN(NatLiveEnergy.coveredRate(.2,.1)));check(Double.isNaN(NatLiveEnergy.coveredRate(.2,1.99)));check(near(NatLiveEnergy.coveredRate(.3,2),15));check(Double.isNaN(NatLiveEnergy.coveredRate(-1,2)));
  NatLiveEnergy e=new NatLiveEnergy();e.setSession(1);e.accept(10000,80,60000,1000,72,true);
  for(int i=1;i<=10;i++)e.accept(10000+i*10000,80,60000-30*i,1000+2*i,72,true);
  NatLiveEnergy.Reading r=e.read(110000,true);check(near(r.km,2));check(near(r.net,.3));check(near(r.rate,15));check(r.gaps==0);
  e.accept(150000,79,59500,1028,72,true);r=e.read(150000,true);check(r.gaps==1);check(near(r.net,.3));check(Double.isNaN(r.power));
  e.setSession(1);check(near(e.read(150000,true).net,.3));e.setSession(2);check(Double.isNaN(e.read(150000,true).net));
  e.accept(160000,79,59470,1030,72,true);check(Double.isNaN(e.read(160000,true).power));
  e.accept(170000,79,59440,1032,72,true);check(near(e.read(170000,true).net,.03));
  e.setSession(-1);check(Double.isNaN(e.read(170000,true).net));check(near(e.read(170000,false).soc,79));
  check(NatMinimalEvRules.keepSearch(true,false,false,false,true,15000));check(!NatMinimalEvRules.keepSearch(true,false,false,false,true,15001));
  check(NatMinimalEvRules.keepSearch(true,true,true,true,true,3600000));check(!NatMinimalEvRules.keepSearch(true,true,true,false,true,100));check(!NatMinimalEvRules.keepSearch(true,true,false,true,true,100));check(!NatMinimalEvRules.keepSearch(true,true,true,true,false,100));check(!NatMinimalEvRules.keepSearch(false,true,true,true,true,100));check(!NatMinimalEvRules.keepSearch(true,true,true,true,true,-1));
  check(NatMinimalEvRules.confirmedSearch(true,true,true,true));check(!NatMinimalEvRules.confirmedSearch(true,true,false,true));check(!NatMinimalEvRules.confirmedSearch(false,true,true,true));
  System.out.println("PASS: "+n+" NAT release hardening cases");
 }
}
JAVA
javac --release 17 -encoding UTF-8 -d "$D" "$P/NatLiveEnergy.java" "$P/NatMinimalEvRules.java" "$D/TestNatRelease.java"
java -cp "$D" app.morphe.extension.mapsautomotive.patches.TestNatRelease
