import sys, re, pathlib, json
root, out = map(pathlib.Path, sys.argv[1:])
out.mkdir(parents=True, exist_ok=True)
patterns = {
 '01-navigation': r'EvNavigationState|isNavigating|publishOffNavState|startNavigation|stopNavigation|AggregatedNavigationStateGenerator',
 '02-soc': r'EvBatteryGroupLayoutBlock|arrivalBattery|arrivalStateOfCharge|arrivalSoc|chargingStop|chargingStation.*[Bb]attery|ev_target_.*arrival_battery_percentage|estimatedBattery',
 '03-destination': r'destinationName|destinationTitle|finalDestination|waypointName|destinationDisplayName|destinationAddress',
 '04-theme': r'CarNightModeController|updateNightModeState|setNightMode|onNightModeChanged|isNightMode|nightModeState',
}
rx={k:re.compile(v,re.I) for k,v in patterns.items()}
hits={k:[] for k in rx}; counts=dict.fromkeys(rx,0); scanned=0
for p in sorted(root.rglob('*.java')):
    scanned+=1
    lines=p.read_text(errors='replace').splitlines()
    for i,line in enumerate(lines):
        for k,r in rx.items():
            if r.search(line):
                counts[k]+=1
                if len(hits[k])<350:
                    hits[k].append({'file':str(p.relative_to(root)), 'line':i+1,'context':'\n'.join(f'{j+1}: {lines[j][:1500]}' for j in range(max(0,i-8),min(len(lines),i+9)))})
for k, rows in hits.items():
    (out/(k+'.json')).write_text(json.dumps(rows,ensure_ascii=False,indent=2))
    (out/(k+'.txt')).write_text('\n\n'.join(f"{r['file']}:{r['line']}\n{r['context']}" for r in rows))
(out/'SUMMARY.txt').write_text(f'Java files scanned: {scanned}\nCandidate matches (not verified hooks):\n'+ '\n'.join(f'{k}: {v} (saved {len(hits[k])})' for k,v in counts.items())+'\nThreshold settings are NOT predicted SOC. Check JADX exit code and log. Runtime validation required.\n')
if not scanned: sys.exit('No Java files found')
