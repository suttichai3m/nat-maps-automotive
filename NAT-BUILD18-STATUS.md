# NAT V3.1 Build 18 – Native measured battery card candidate

Base: Build 16, incorporating the Build 17 changes. Target: original Google Maps Automotive 26.10.120003.E (1068833413).

## Actual code changes
- `NatNativeBatteryPatch.kt`: decorates the real Compose row after its semantics modifier in `Lcmp.v`, called by the trip-summary renderer `Lhdp` with `Lkrj.e` (Google's forecast). The icon is 42 dp, text 50 sp, bold weight 700. No guessed TextView lookup in this path.
- `NatNativeCard.java`: native measured minimum 220 × 124 dp, rounded day/night background, reserved top and bottom padding, label on Android Canvas where available, progress track from the signed native SOC. Negative forecasts remain native negative text; the bar is empty. The measured size follows the actual parent constraints. No new GPS, network, service, or timer. Native decoration fails safely if reflection fails; native icon and text remain.
- `ColorSchemePatch.kt`: overrides both native mode consumers and calls the verified shared `Lmjy.b()` refresh sink after updating them. The original `f/g` methods gate that sink on BOTH old/new state and a notification boolean, so merely passing true did not guarantee refresh. One unconditional native refresh on an explicit change, no restart requested by this code.
- `ColorScheme.java`: card label/color and native mode support. Existing route-preview logic and GPS/MicroG are not intentionally changed relative to Build 17.
- CI runs the new native-card mock tests.

## Verification actually performed
- Original APK DEX anchors, exact registers, method/field signatures and branch targets: PASS (`scripts/check-native-dex.py`).
- Java standalone tests: 62 existing cases + 21 new native-card mock checks = 83 checks passed.
- Native-card mock test exercises the actual Java decoration implementation against local mock native classes. It is NOT an Android UI test.

## Not verified
- Android/Kotlin full Gradle build, Morphe patch application, APK signature, car runtime, exact parent measurement, preview/active visual parity and live map refresh are not verified in this environment. The APK may use different rendering paths in some states. The title is optional if the underlying Android Canvas is unavailable. This is a source candidate, NOT a certified final release. Do not infer a successful car test from successful source tests.

Use the original APK of the exact supported version with Morphe; do not patch an already patched APK. Keep a working APK backup. Only test while parked. The Google forecast is not a guarantee of arrival charge.
