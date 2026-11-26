# V3.1 alpha1 — development only, not vehicle-ready
Base: V3.0 Candidate source; verified APK SHA256 a49b8266dc933ed844e8d4d72d4cd07f7f40730c91e79be7aa377873d3ad08be.
Changes: signed SOC parser and native span styling; negative warning; disable heuristic forecast comparison in inline UI until route identity is verified. Existing send-to-car code unchanged. CI runs JVM tests before build.
Test: bash scripts/test-signed-soc.sh (17 assertions). Android compilation and vehicle tests not performed. No MPP/APK supplied. Existing TextView rendering limitation remains. Destination name, live theme, voice and route identity hooks not implemented. Do not install as stable. Keep V3.0 backup.
