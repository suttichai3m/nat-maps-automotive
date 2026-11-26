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
 String[] extra={"12 km\u00a0· 10:30 AM · \uFFFC 75%", "12 km · 10:30 · \u200e75%\u200f", "12 km · 10:30 · +100%", "0.5 km · 9:05 · 1%", "500 เมตร ・ 22:59 น. ・ −3％", "12 km · 10:30 · 72%", "12 km · 10:30 · 69%", "12 km · 10:30 · 1000%", "12 km · 10:99 · 75%", "12 km · 10:30 · 75% → 80%", "Arrival battery 75%", "12 km · 10:30", "12 km · 10:30 · 75% · charging", "12 km\u202f· 10:30 · 75%", "12 km · 10:30 · \u206675%\u2069"};
 Integer[] more={75,75,100,1,-3,72,69,null,null,null,null,null,null,75,75};
 for(int i=0;i<extra.length;i++) if(!java.util.Objects.equals(more[i],NatNavigationSummary.parse(extra[i]))) throw new AssertionError(extra[i]);
 if(NatNavigationSummary.parse(null)!=null) throw new AssertionError("null");
 String[] expanded={"18 กม. · 00:38 น. · 🔋75%", "18 กม. · ถึง 00:38 น. · 75%", "28 นาที · 18 กม. · 00:38 น. · 75%", "28 min 18 km ETA 00:38 Arrival battery: 75%", "1 hr 25 min · 100 km · Arrive at 9:30 AM · −5%", "12 km | 10:30 | Battery on arrival: 50%", "18 km 10:30 75%", "75%", "18 km · 10:30 · Current battery 75%", "18 km · 10:30 · 75% · 80%", "18 km · 10:30 · 🔋101%", "18 km · 10:30 · 75% charging", "12 km · 10:30 · 75% → 80%", "12 km · 25:30 · 75%", "28 min 18 km 75%", "18 km · 10:30 · Arrival battery: 0%"};
 Integer[] exp={75,75,75,75,-5,50,75,null,null,null,null,null,null,null,null,0};
 for(int i=0;i<expanded.length;i++) if(!java.util.Objects.equals(exp[i],NatNavigationSummary.parse(expanded[i]))) throw new AssertionError(expanded[i]);
 String[][] split={{"28 นาที","18 กม.","ถึง 00:38 น.","🔋75%"},{"12 km","10:30","75%"},{"12 km ·","10:30 ·","-66%"},{"12 km","10:30","Arrival battery: 32%"},{"12 km","10:30","75%","80%"},{"12 km","75%"},{"75%"},{"12 km","10:30","Current battery 75%"},{"12 km","10:30","75%","charging"}};
 Integer[] sexp={75,75,-66,32,null,null,null,null,null};
 for(int i=0;i<split.length;i++) if(!java.util.Objects.equals(sexp[i],NatNavigationSummary.parseParts(java.util.Arrays.asList(split[i])))) throw new AssertionError(java.util.Arrays.toString(split[i]));
 if(NatNavigationSummary.isExplicitSoc("75%") || !NatNavigationSummary.isExplicitSoc("Arrival battery: 75%") || NatNavigationSummary.isExplicitSoc("Arrival battery: 101%")) throw new AssertionError("explicit label");
 System.out.println("PASS: 53 navigation summary cases (25 original + 16 expanded + 9 split + 3 explicit-label)");
 }
}
JAVA
javac -encoding UTF-8 -d "$D" "$P/NatSignedSoc.java" "$P/NatNavigationSummary.java" "$D/TestNav.java"
java -cp "$D" app.morphe.extension.mapsautomotive.patches.TestNav
