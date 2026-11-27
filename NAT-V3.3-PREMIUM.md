# NAT V3.3 Real-Time Premium — map-original

Implements the approved dashboard concept using native Maps 75:25 and a static blue Seal studio image. All vehicle access remains read-only.

- Dashboard SOC/speed cache polls known BYD SDK getters every 500 ms when split is active/focused; existing Maps model, forecast, calibration and log sampling remain at 10 seconds. Actual firmware latency and worker contention are not measured. TS/unsupported getters use existing slow fallback.
- ODO comes from the documented 0.1 km reading; range is labelled an estimate from BMS energy/recent consumption. No fake values.
- POWER/REGEN is a signed **net BMS average over accepted moving samples**, not calibrated instantaneous motor power. Coarse quantization, no stationary signal, gaps and zero-delta samples show unavailable. A/C setpoints remain unavailable pending SDK method/unit validation.
- Daily distance and net observed moving energy are stored per Bangkok calendar day; same-day ODO restart gaps contribute distance but not unobserved energy. Partial totals are explicitly marked. Requires app process running for energy capture. Charging, stationary intervals and unavailable samples are not reconstructed. Cost is estimated net observed kWh × user tariff, default THB 7/kWh; changing tariff reprices today's observed energy. No charging-loss accounting.
- Preserves native EV Charging, Energy, Charging Plan, Clean, Diagnostics and Full Map callbacks; no new charger marker implementation.
- Layout has scrollable details; battery cards stay pinned. Static hero is a compressed copy of the approved preview crop; no road animation.
- Android build/vehicle validation still required; verify start/cancel/reroute, day/night, real SDK values, standby power/regen, midnight, restart, charge gaps, rate persistence and low-resolution layout.
