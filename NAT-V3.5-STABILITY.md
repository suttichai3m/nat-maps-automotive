# NAT V3.5 — native theme and Send to Car recovery candidate

Only map-original, based on V3.4. Preserves 75:25 and all existing controls. No Grab integration.

Theme: refresh all live controller identities (bounded weak references), call the known native screen/basemap consumers with notifications enabled, retain the shared refresh sink, and retry twice after successful callback. Independent native enum traces and ages in diagnostics distinguish screen and basemap consumer requests. These are not proof of pixels changing; actual 26.10.120003.E patch application and vehicle renderer validation are required. No app restart or route cancellation.

Send to Car: retain MicroG's 100-second minimum heartbeat gap (90-second timeout). Add validated-network and screen-wake recovery, coalesced foreground/resume retry and 110-second periodic check. No automatic replay of old trips or app restart. Ignore superseded short-link results. Existing explicit restart and status remain; status now linked from Smart EV. Push transport, provider registration and Google delivery remain external dependencies.

Dashboard: adds outside temperature (fresh existing sample), BMS SOH and pack average temperature, tyre front/rear and climate setpoints. Named optional SDK reads only, no vehicle commands. Climate reads each second, TPMS every ten seconds, on existing worker while split requests data. Requires actual SDK fields/methods, Celsius mode, valid signal and documented kPa units. Unknown or unsupported fields are unavailable. AC OFF only on explicit off. No claim of instantaneous REGEN; previous average remains. TPMS not a substitute for the native warning system.

References: BYD Open API v1.0.5 https://oip.byd.com/uploads/20210824/46cd2d7c2e878b0a1c6b066967e7f6fd.pdf ; independent DiLink 3 AC documentation https://github.com/wheregoes/byd-dolphin-hacking/blob/master/docs/ac-climate-control.md . These sources are not Seal firmware validation.

Acceptance while parked: manually select day/night in Full Map and Split before/after Start; confirm actual basemap tiles, not just cards, then Auto/time and return from sleep. Test repeated phone Send to Car, Wi-Fi loss/recovery, screen wake, pending notification, route arrival, provider disabled and no duplicate open. Compare both climate zones, outside temp, pressure and SOH against native screens. Verify data expires, unavailable cases, no forced restart, and no layout overlap. Android build and actual APK tests pending.
