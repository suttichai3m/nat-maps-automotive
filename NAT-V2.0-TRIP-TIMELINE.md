# NAT V2.0 — Trip Timeline preview

Entry: BYD battery settings > NAT V2.0 — Trip Timeline. Recording is off by default.
Enable while parked, grant Maps location permission via Android settings if required. Existing BYD worker samples every ~10 s. Requests GPS while vehicle speed is available and above 1 km/h. Stops after stationary samples for five minutes or when disabled. No new foreground service; recording outside navigation depends on process survival and Android location policy. No guaranteed recording after process kill or vehicle shutdown.

Private SQLite database, 90 days / 100,000 points, accurate fresh fixes only. No network or paid API. Daily history, schematic GPS trace (not a street map), segment summaries and explicit CSV export. Battery decrease sums are partial observations, not net energy, and cannot account for charging. No claim of full V1.7–1.9 integration or continuous recording. Export contains sensitive location data. Delete disables recording and clears database; exported copies remain.

Acceptance: build, open settings, enable, drive with GPS permission, park, inspect timeline, switch dates, export, disable, verify no new rows, delete. Test process kill, permission revocation, SDK unavailable, GPS gaps and vehicle sleep. Do not interact while driving.
