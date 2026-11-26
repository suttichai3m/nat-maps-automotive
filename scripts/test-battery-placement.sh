#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
D=$(mktemp -d); trap 'rm -rf "$D"' EXIT
P=extensions/automotive/src/main/java/app/morphe/extension/mapsautomotive/patches
cat > "$D/TestPlacement.java" <<'JAVA'
package app.morphe.extension.mapsautomotive.patches;
import java.util.*;
class TestPlacement {
 static NatBatteryPlacement.Box b(int x,int y,int w,int h){return new NatBatteryPlacement.Box(x,y,w,h);}
 static void check(boolean v){if(!v)throw new AssertionError();}
 public static void main(String[] args){
  NatBatteryPlacement.Box[] vp={b(0,0,1280,720),b(0,0,720,1280),b(35,60,900,500),b(0,0,300,180),b(0,0,190,110)};
  int cases=0;
  for(NatBatteryPlacement.Box v:vp) for(int size:new int[]{200,184,168}) for(int pos=0;pos<3;pos++){
   NatBatteryPlacement.Box a=b(v.x+10,v.y+(pos==0?0:pos==1?v.h/2:v.h-40),100,35);
   List<NatBatteryPlacement.Box> options=NatBatteryPlacement.candidates(v,a,size,Math.round(size*.6f),8);
   for(NatBatteryPlacement.Box c:options){check(v.contains(c));check(!c.overlaps(a));check(c.w==size);}
   if(v.w<size || v.h<Math.round(size*.6f))check(options.isEmpty());
   if(v.w>=720)check(options.size()>2);
   cases++;
  }
  NatBatteryPlacement.Box v=b(0,0,1280,720),a=b(60,630,250,40);
  List<NatBatteryPlacement.Box> opts=NatBatteryPlacement.candidates(v,a,200,120,8);
  check(opts.get(0).y==502);cases++;
  NatBatteryPlacement.Box obstacle=opts.get(0);
  check(opts.stream().anyMatch(c->!c.overlaps(obstacle)));cases++;
  check(NatBatteryPlacement.candidates(v,a,0,120,8).isEmpty());cases++;
  System.out.println("PASS: "+cases+" placement scenarios (pure geometry)");
 }
}
JAVA
javac -encoding UTF-8 -d "$D" "$P/NatBatteryPlacement.java" "$D/TestPlacement.java"
java -cp "$D" app.morphe.extension.mapsautomotive.patches.TestPlacement
