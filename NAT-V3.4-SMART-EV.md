# NAT V3.4 — Smart EV Intelligence candidate

Branch: map-original. Extends V3.3 without changing the native split geometry, car image, dashboard metrics, existing footer callbacks or underlying Maps renderer.

One new **Smart EV** button is first on the existing footer (left, under the map). It opens a compact left-side dialog without dimming the right dashboard. Close returns to the original 75:25 layout. Details are snapshots while the dialog is open; the normal focus-related refresh resumes on close. No Grab integration.

- Smart Charging Assistant: uses a fresh, matched forecast captured before the dialog takes focus; adjustable reserve (default 15%) and assumed charger power (default 50 kW). Computes minimum stored energy, approximate time and cost only for no-charge forecasts. Existing native station shortcut and charging plan are linked; no invented charger locations, availability or automatic stop insertion.
- Daily Driving Timeline: links to existing opt-in private recorder, date/period selector, schematic trace/replay and CSV/GPX/JSON export. No new background tracking permissions; user must enable recorder. No Google basemap promise.
- Energy Intelligence: daily recorded net cost using V3.3 tariff, matched sample coverage, positive/negative BMS energy changes and consumption/cost per covered km from V3.4 onwards. Incomplete/older data are not treated as full-day REGEN. New counters survive same-day restart. No synthetic driving score.
- Vehicle Health: reads existing cached BMS SOH and battery temperature with age; unknown/stale values shown as unavailable. TPMS and 12V not verified and not fabricated.
- Existing V3.3 AC and instantaneous POWER/REGEN limitations remain. No build dependency changes.

Acceptance: open/close Smart EV repeatedly with no active route, preview and navigation; verify map stays left 75%, dashboard right 25%, native controls work, negative SOC, no stale route estimate, tariff consistency, day reset, timeline opt-in, stale health readings and Full Map return. Test at actual BYD display dimensions while parked.
