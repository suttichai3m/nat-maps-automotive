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
  // Long Thai trip summary: right cell may overlap the verified SOC suffix,
  // but must never cover the time/distance prefix or leave the panel.
  for(int scale:new int[]{1,2,3}){
   NatBatteryPlacement.Box longPanel=b(0,0,360*scale,124*scale);
   NatBatteryPlacement.Box longSoc=b(215*scale,40*scale,48*scale,20*scale);
   NatBatteryPlacement.Box prefix=b(8*scale,20*scale,190*scale,40*scale);
   for(int w:new int[]{160,144,128,112}){
    List<NatBatteryPlacement.Box> cells=NatBatteryPlacement.rightCell(longPanel,longSoc,w*scale,w*scale/2,4*scale);
    check(!cells.isEmpty(),"right cell");
    for(NatBatteryPlacement.Box c:cells){
     check(longPanel.contains(c),"right cell outside panel");
     check(c.x+c.w==356*scale,"right edge must stay fixed");
     check(!c.overlaps(prefix),"right cell covers ETA");
    }
    cases++;
   }
  }
  check(NatBatteryPlacement.rightCell(b(0,0,90,50),b(20,10,20,10),112,56,4).isEmpty(),"no unsafe tiny cell");cases++;
  System.out.println("PASS: "+cases+" integrated summary parser/placement cases (pure JVM)");
 }
}
JAVA
javac --release 17 -encoding UTF-8 -d "$D" "$P/NatSignedSoc.java" "$P/NatNavigationSummary.java" "$P/NatBatteryPlacement.java" "$D/TestIntegrated.java"
java -cp "$D" app.morphe.extension.mapsautomotive.patches.TestIntegrated
