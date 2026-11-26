#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
D=$(mktemp -d); trap 'rm -rf "$D"' EXIT
P=extensions/automotive/src/main/java/app/morphe/extension/mapsautomotive/patches
cat > "$D/Test.java" <<'JAVA'
package app.morphe.extension.mapsautomotive.patches;
class Test {
 public static void main(String[] args) {
 int[][] cases={{0,1080,360,1},{359,1080,360,1},{360,1080,360,0},{1079,1080,360,0},{1080,1080,360,1},{1439,1080,360,1},{359,360,1080,0},{360,360,1080,1},{1079,360,1080,1},{1080,360,1080,0},{0,0,0,0},{-1,1080,360,0},{1440,1080,360,0},{0,-1,360,0},{0,1080,1440,0}};
 for(int[] c:cases) if(NatThemeSchedule.nightAt(c[0],c[1],c[2])!=(c[3]==1)) throw new AssertionError(java.util.Arrays.toString(c));
 System.out.println("PASS: 15 theme schedule cases");
 }
}
JAVA
javac -d "$D" "$P/NatThemeSchedule.java" "$D/Test.java"
java -cp "$D" app.morphe.extension.mapsautomotive.patches.Test
