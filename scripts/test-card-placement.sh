#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
D=$(mktemp -d); trap 'rm -rf "$D"' EXIT
P=extensions/automotive/src/main/java/app/morphe/extension/mapsautomotive/patches
cat > "$D/TestPlacement.java" <<'JAVA'
package app.morphe.extension.mapsautomotive.patches;
class TestPlacement {
 public static void main(String[] args) {
  int[][] screens={{800,480,500,350},{800,480,0,0},{800,480,780,450},{220,124,0,0}};
  for(int[] s:screens){var c=NatCardPlacement.candidates(s[0],s[1],220,124,8,s[2],s[3],80,20); if(c.isEmpty())throw new AssertionError(); for(int[] a:c)if(a[0]<0||a[1]<0||a[0]+220>s[0]||a[1]+124>s[1])throw new AssertionError();}
  if(!NatCardPlacement.candidates(219,124,220,124,8,0,0,80,20).isEmpty())throw new AssertionError();
  System.out.println("PASS: 5 placement cases");
 }
}
JAVA
javac -d "$D" "$P/NatCardPlacement.java" "$D/TestPlacement.java"
java -cp "$D" app.morphe.extension.mapsautomotive.patches.TestPlacement
