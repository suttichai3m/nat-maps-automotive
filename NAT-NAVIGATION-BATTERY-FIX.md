# NAT V3.1 — Navigation Arrival Battery Test Build

Base: supplied `map-original` source ZIP. No theme, GPS, SOC calculation, signing or build configuration changes.

The original deliberately disabled `NatNavigationCard.refresh` and cleared the overlay on global layout. This candidate enables a guarded drawable above the single visible native distance/ETA/arrival SOC summary, keeps the native text intact, and refreshes from current native data on layout, text changes and the existing one-second polling loop. It uses the existing `ColorScheme.nightColors` and BYD Battery SafeMode flag. No cached SOC or independent calculation. Native summary remains visible. Card 168 x 96 dp, outlined battery, nominal 32 dp digits, auto-fit for wide signed numbers. On ambiguity, missing summary, no focus, insufficient space or detected control collision, the overlay is removed. Exact layout and route identity on BYD are not verified; the card can remain hidden on unrecognized/split summaries or crowded layouts. This is not a final APK.

Tests: signed SOC 17, navigation summary 25, theme schedule 15 = 57 Java cases. Android SDK, Morphe package credentials and vehicle runtime unavailable here. No Android compile, MPP or APK generated. Native map renderer controls not necessarily represented in Android View hierarchy; visually check placement while parked.

## Build using existing GitHub Actions
1. Apply the supplied git patch to clean `map-original` checkout (or copy changed files from source ZIP). Commit and push using GitHub Desktop. Do not merge main yet.
2. Actions > NAT V3.1 integrated candidate build > Run workflow > map-original > Run workflow. Existing workflow requires package access (`MORPHE_PACKAGES_TOKEN` if needed). If manual workflow is unavailable on default branch, use existing local build procedure in `docs/build-setup.md`.
3. Download artifact NAT-V3.1-integrated-candidate-mpp after successful run. Extract .mpp and use existing Morphe Desktop workflow with the same compatible original Maps APK and same signing key. Artifact is not itself an APK.
4. Park before testing: search route, preview battery, Start, change day/night, reroute, stop navigation, rotate if used. Check 0/negative SOC only if naturally available or simulated safely. Verify no obstruction, no duplicate large card, no stale value. If no large card appears, capture full screen and view/log data for actual native summary and placement before changing parser or weakening guards.
