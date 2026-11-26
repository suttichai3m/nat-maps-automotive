#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
D=$(mktemp -d); trap 'rm -rf "$D"' EXIT
P=extensions/automotive/src/main/java/app/morphe/extension/mapsautomotive/patches
cat > "$D/TestNav.java" <<'JAVA'
package app.morphe.extension.mapsautomotive.patches;
class TestNav {
 public static void main(String[] args) {
 String[] in={"846 กม. · 19:58 น. · -66%","18 กม. · 00:38 น. · 75%","299 กม. · 06:38 น. · 31%","12 km · 10:30 AM · −51%","12 km · 10:30 · 0%","12 km · 10:30 · 101%","30% → 79%","75%","12 km · 10:30 · 31% · 79%"};
 Integer[] expected={-66,75,31,-51,0,null,null,null,null};
 for(int i=0;i<in.length;i++) if(!java.util.Objects.equals(expected[i],NatNavigationSummary.parse(in[i]))) throw new AssertionError(in[i]);
 if(!NatNavigationSummary.splitRoute("741 กม. 06:06 น. -49%")) throw new AssertionError("split Thai");
 if(!NatNavigationSummary.splitRoute("12 km 10:30 AM 31%")) throw new AssertionError("split English");
 if(NatNavigationSummary.splitRoute("741 กม. 06:06 น. -49% 71%")) throw new AssertionError("ambiguous");
 if(!java.util.Objects.equals(-49,NatNavigationSummary.parse("741 กม. · 06:06 น. · \u200e-49%\uFFFC"))) throw new AssertionError("format markers");
 if (!java.util.Objects.equals(-49,NatNavigationSummary.localSoc("10 ชม. 59 นาที 741 กม. · 06:06 น. · -49%"))) throw new AssertionError("duration prefix");
 if (NatNavigationSummary.localSoc("741 กม. 06:06 น. -49% 71%")!=null) throw new AssertionError("ambiguous local");
 if (NatNavigationSummary.localSoc("71%")!=null) throw new AssertionError("standalone");
 System.out.println("PASS: 16 navigation summary cases");
 }
}
JAVA
javac -encoding UTF-8 -d "$D" "$P/NatSignedSoc.java" "$P/NatNavigationSummary.java" "$D/TestNav.java"
java -cp "$D" app.morphe.extension.mapsautomotive.patches.TestNav
