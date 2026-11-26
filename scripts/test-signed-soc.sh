#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
D=$(mktemp -d); trap 'rm -rf "$D"' EXIT
P=extensions/automotive/src/main/java/app/morphe/extension/mapsautomotive/patches
cat > "$D/Test.java" <<'JAVA'
package app.morphe.extension.mapsautomotive.patches;
class Test {
 public static void main(String[] args) {
 String[] in={"100%","73%","0%","-51%","−66%","-100%","+31%","31％","31 %","101%","--66%","","12.5%","-999%"};
 Integer[] expected={100,73,0,-51,-66,-100,31,31,31,null,null,null,null,-999};
 for(int i=0;i<in.length;i++) if(!java.util.Objects.equals(expected[i],NatSignedSoc.parse(in[i]))) throw new AssertionError(in[i]);
 if(NatSignedSoc.bar(-66)!=0||NatSignedSoc.bar(59)!=59||NatSignedSoc.bar(101)!=100) throw new AssertionError("bar");
 System.out.println("PASS: 14 parser cases + 3 bar cases");
 }
}
JAVA
javac -encoding UTF-8 -d "$D" "$P/NatSignedSoc.java" "$D/Test.java"
java -cp "$D" app.morphe.extension.mapsautomotive.patches.Test
