#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
P=extensions/automotive/src/main/java/app/morphe/extension/mapsautomotive/patches
D="$(mktemp -d)"
trap 'rm -rf "$D"' EXIT
javac --release 17 -encoding UTF-8 -d "$D" "$P/NatEvPlanRules.java" "$P/NatReadableRules.java" scripts/NatReadableTest.java
java -cp "$D" app.morphe.extension.mapsautomotive.patches.NatReadableTest
