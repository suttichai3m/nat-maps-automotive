#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
D=$(mktemp -d); trap 'rm -rf "$D"' EXIT
P=extensions/automotive/src/main/java/app/morphe/extension/mapsautomotive/patches
cat > "$D/TestContinuity.java" <<'JAVA'
package app.morphe.extension.mapsautomotive.patches;
class TestContinuity {
 public static void main(String[] args) {
 long[] t={-1,0,1000,2500,2501,10000};
 boolean[] e={false,true,true,true,false,false};
 for(int i=0;i<t.length;i++) if(NatCardContinuity.keep(t[i])!=e[i]) throw new AssertionError(t[i]);
 System.out.println("PASS: 6 continuity cases");
 }
}
JAVA
javac -d "$D" "$P/NatCardContinuity.java" "$D/TestContinuity.java"
java -cp "$D" app.morphe.extension.mapsautomotive.patches.TestContinuity
