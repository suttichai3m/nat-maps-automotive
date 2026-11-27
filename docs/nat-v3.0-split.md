# NAT V3.0 Smart Split Screen — Final Design Candidate
Based on the user-approved split-screen concept and real vehicle reports. This is a release candidate, not a certified final APK.

## Layout
- Existing Google Maps native child: 75% of content width (integer rounding), not reparented. Dedicated right dashboard: 25%.
- Common NAT footer: 56dp, 5 action buttons plus Full Map. BYD's system controls are not modified. Minimum 540dp map width, 200dp dashboard width and 260dp map/dashboard height.
- Fixed top area: owner vehicle crop, heading trend, two independent large SOC cards. Metrics below scroll only if necessary.
- Preserve original Google route summary and native SOC as secondary backup; NAT cards are primary. No fabricated values or paid APIs.
- Full Map fallback uses V2.0.5 independent cards and launcher, when safe slots exist. No dependency on verified route summary for toolbar visibility. Native controls still must be recognized.

## Data and behavior
- Current SOC: fresh BYD observation, independent of route. Arrival: fresh forecast matched to native summary, otherwise unique verified native arrival SOC with generic Maps label (may be next stop). No data -> em dash; never reuse stale route value. Thai meter unit (600 ม.) and other supported units parsed by V2.0.5 parser.
- Speed: cached real vehicle sample, nominal poll 10s, max age 15s. Average battery power and observed consumption are sampled metrics, not instantaneous motor power/verified regen. Remaining energy requires matching forecast. Distance/ETA: verified native route summary; may refer to next stop.
- GPS: actual recent GPS fix (not inferred from motion); heading trend: successive accurate GPS bearings, speed gate, max age 3s. BYD feed status from cached diagnostic data.
- Owner photo is a low-resolution cropped WebP, drawn with decorative road motion; turning is illustrative heading trend, not actual steering angle/ADAS/road lanes. Stops if source stale, stopped, focus lost, or UI updates stop. No new data upload.
- EV and Clean call existing guarded native actions; Energy and Charging Plan open snapshot dialogs; Diagnostics opens existing live dashboard with UI recovery reason; Trip Timeline remains available in Settings. Full Map returns layout params; toggle Split from BYD Battery Settings.
- Resize changes and native child replacement recover after a short retry delay; if actual map and panel geometry or renderer bounds are incompatible, restore native layout and block retry on same child/size until user toggles or geometry changes. A 5-second grace handles temporary map recognition loss. Native renderer and touch must still be validated in vehicle; no guarantee that native Maps can resize on every DiLink.
- NAT theme follows existing theme state; rebuild only NAT views on theme change. This does not independently guarantee Google native map changes color.

## Verification
Run existing nat-build.yml with branch nat-split-screen, not map-original. Expected suite 501 cases (409 baseline + 28 persistent UI + 64 split/motion). Pure-Java local tests verify math/parser/layout only; Android compile, MPP generation, actual APK signature/install, renderer/touch, visibility during 30-minute navigation, and theme changes need separate verification. No automatic dispatch configured.
