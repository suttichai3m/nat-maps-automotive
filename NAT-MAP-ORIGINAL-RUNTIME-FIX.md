# NAT map-original: targeted runtime fixes (candidate)

Base: faa3c9d0aa3e3993ffaf67a3885002e1ac42e46f. This change does not replace NatNavigationCard, arrival forecast, GPS or route data.

- EV: stop opening generic Search from the shortcut when the native EV category is absent. This is a guard, not an independent charging-station overlay. The actual map marker integration remains pending; do not report it as working.
- Power: label the existing delta-Wh value as estimated average, never as dashboard live kW. Expire it after 5 seconds, preserve cumulative energy; show unavailable for dashboard live kW until the specific BYD read-only signal is validated.
- Theme: port the native mode-consumer injection and refresh sink from the integrated branch; preserve existing map-original Java controller retry and battery styling. Requires patch validation on original 26.10.120003.E and on-car testing.
- Split 75:25: pending safe native layout integration on map-original; no renderer reparenting or replacement of working card in this change.

No full Android build, signed APK or BYD runtime validation has been performed for this commit. Do not deploy as final. Keep testing parked.
