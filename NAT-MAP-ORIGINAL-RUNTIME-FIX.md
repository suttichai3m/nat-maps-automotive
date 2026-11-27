# NAT map-original: targeted runtime fixes (candidate)

Base: faa3c9d0aa3e3993ffaf67a3885002e1ac42e46f. This change does not replace NatNavigationCard, arrival forecast, GPS or route data.

- EV: stop opening generic Search from the shortcut when the native EV category is absent. This is a guard, not an independent charging-station overlay. The actual map marker integration remains pending; do not report it as working.
- Power: label the existing delta-Wh value as estimated average, never as dashboard live kW. Expire it after 5 seconds, preserve cumulative energy; show unavailable for dashboard live kW until the specific BYD read-only signal is validated.
- Theme: port the native mode-consumer injection and refresh sink from the integrated branch; preserve existing map-original Java controller retry and battery styling. Requires patch validation on original 26.10.120003.E and on-car testing.
- Split 75:25: ported guarded native viewport candidate from nat-split-screen into map-original. Default Full Map on each new Maps activity, ignoring previous saved split preference. Dedicated Split Screen 75:25 button follows Diagnostics (below when vertical); idle toolbar has both buttons. Full Map 100% in split footer. Diagnostics long-press remains a fallback if there is not enough safe space. Failed geometry clears the request and restores Full Map; user must manually retry. Preserve existing native integrated battery decoration; if it cannot fit, show verified SOC in side panel (native small SOC may remain). Added pure geometry/motion and power-freshness regression cases. The actual head-unit viewport, touch bounds and GPU renderer are still unverified.

- Luxury Executive visual refresh: replace low-resolution embedded car photo with a locally drawn metallic-blue Seal-inspired vector; improve metric borders and motion status legibility. Preserve the existing metric values, data sources, viewport and navigation actions. No new vehicle signal or ADAS data is claimed.

No full Android build, signed APK or BYD runtime validation has been performed for this commit. Do not deploy as final. Keep testing parked.
