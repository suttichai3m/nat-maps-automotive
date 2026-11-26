# Lightweight continuity candidate
Shared root-level 250 ms refresh queue for polling, layout and text changes. Focus loss clears overlay. Watched text changes invalidate SOC immediately until next scan. Pending overlay expires after 2.5 seconds even if fallback scan runs later. Fixed 220 x 124 dp and 50 dp retained. No GPS/MicroG changes.
Limitations: UI heuristics only; no verified Start/Stop, destination identity or charging-stop hook. Card can disappear when safe placement is unavailable. Android build and car runtime not tested. Existing pure Java tests do not cover Android callbacks.
