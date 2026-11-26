# V3.1 integrated candidate (not Final)
Merged Alpha 1 full source and cumulative Alpha 3 delta. Voice Search excluded. No verified destination/route hook; experimental overlay remains disabled. Charging stop correlation and live renderer refresh are unverified. Do not install as Final.

Local Java tests: 14 parser + 3 bar + 9 navigation summary + 15 theme schedule = 41 passed. Android SDK absent in this environment; Android compilation, MPP/APK generation and BYD runtime not performed. Original V3.0 Verified APK SHA256: a49b8266dc933ed844e8d4d72d4cd07f7f40730c91e79be7aa377873d3ad08be.

CI: workflow_dispatch, Java 21, Android SDK 36, all three test scripts before Gradle. Verify MORPHE_PACKAGES_TOKEN access. Preserve baseline branch and APK. Do not merge to main or install until route identity, safe layout and regression checks pass.
