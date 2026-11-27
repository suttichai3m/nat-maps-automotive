package app.morphe.extension.mapsautomotive.patches;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.widget.*;
import android.text.InputType;
import java.util.Locale;

/** Independent planning estimate; never changes Maps or vehicle data. */
final class NatPrediction {
    private static volatile Snapshot snapshot;
    private static final NatLiveEnergy live=new NatLiveEnergy();
    static synchronized void observe(BydDriveLog.Sample s){
        live.accept(s.elapsed,s.soc,s.energyWh,s.mileage01km,s.speedKmh,freshSnapshot()!=null);
    }
    static synchronized NatLiveEnergy.Reading liveReading(){return BydBatteryPatch.isActive()?live.read(SystemClock.elapsedRealtime(),freshSnapshot()!=null):new NatLiveEnergy.Reading(Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,0);}
    static synchronized double observedKwh(){return liveReading().net;}
    static Snapshot freshSnapshot(){Snapshot s=snapshot;return s!=null&&BydBatteryPatch.isActive()&&NatStabilityMath.fresh(SystemClock.elapsedRealtime(),s.at)?s:null;}
    static synchronized void clear(){snapshot=null;live.endRoute();}
    static final class Snapshot {
        final long at; final double soc, capacity, km; final int google, charges; final String plan; final int next; final double nextKm; final double remainingKwh;
        Snapshot(double s,double c,double d,int g,int n,String plan,int next,double nextKm,double remainingKwh) { at=SystemClock.elapsedRealtime(); soc=s; capacity=c; km=d; google=g; charges=n; this.plan=plan; this.next=next; this.nextKm=nextKm; this.remainingKwh=remainingKwh; }
    }
    static synchronized void capture(BydForecast.Forecast f, double soc, int capacityWh,int currentWh) {
        if (f.isEmpty() || !Double.isFinite(soc) || capacityWh <= 0 || f.finalArrivalWh()==BydForecast.NO_ENERGY) { clear(); return; }
        StringBuilder plan=new StringBuilder(); int i=0; long minimumAdded=0,cumulativeM=0; boolean complete=true;
        for(BydForecast.Stop stop:f.stops) { cumulativeM+=Math.max(0,stop.distanceM); if(!stop.charging)continue;
            if(!stop.hasArrival||stop.arrivalWh<0||stop.minDepartureWh<=0||stop.minDepartureWh<stop.arrivalWh||stop.minDepartureWh>capacityWh)complete=false;
            else minimumAdded+=(long)stop.minDepartureWh-stop.arrivalWh;
            plan.append("\nจุดชาร์จ ").append(++i).append(": ").append(stop.hasArrival?energyPercent(stop.arrivalWh,capacityWh):"?").append(" → ").append(stop.minDepartureWh>0?energyPercent(stop.minDepartureWh,capacityWh):"?").append(" (ขั้นต่ำ)");
            if(stop.chargeTimeS>0) plan.append(" · ").append(Math.round(stop.chargeTimeS/60.0)).append(" นาที");
            if(stop.chargePowerW>0)plan.append(" · ").append(fmt(stop.chargePowerW/1000.0)).append(" kW (forecast)");
            if(cumulativeM>0)plan.append(" · ").append(fmt(cumulativeM/1000.0)).append(" กม.");
        }
        BydForecast.Stop first=f.stops.get(0);
        snapshot=new Snapshot(soc,capacityWh/1000.0,f.totalDistanceM()/1000.0,signed(f.finalArrivalWh(),capacityWh),f.chargeStops(),plan.toString(),first.hasArrival?signed(first.arrivalWh,capacityWh):Integer.MIN_VALUE,first.distanceM/1000.0,NatStabilityMath.remainingKwh(currentWh,f.finalArrivalWh(),minimumAdded,complete));
    }
    private static int signed(int wh,int cap){return NatStabilityMath.signedSoc(wh,cap,BydDisplayCurve.shown(wh,cap));}
    private static String energyPercent(int wh,int cap) { return wh<=0 ? fmt(wh*100.0/cap)+"%" : BydDisplayCurve.shown(wh,cap)+"%"; }
    static String navigationSummary(android.content.Context context,int value,double km) {
        Snapshot s=snapshot;
        if(s==null || SystemClock.elapsedRealtime()-s.at>30000 || !BydBatteryPatch.isActive() || !Double.isFinite(km)) return "";
        if(s.charges>0 && s.next==value && Math.abs(s.nextKm-km)<=Math.max(.2,km*.01) && s.google>=0) return "Final est. "+s.google+"%";
        return "";
    }
    // Only compare a fresh, matching, no-charge forecast. Never replace Maps SOC.
    static String comparison(android.content.Context context, int google, double km) {
        Snapshot s=snapshot;
        if(s==null || SystemClock.elapsedRealtime()-s.at>30000 || !BydBatteryPatch.isActive() || s.charges!=0 || s.google!=google || !Double.isFinite(km) || Math.abs(s.km-km)>Math.max(0.2,km*0.01)) return "";
        try {
            SharedPreferences p=context.getSharedPreferences("nat_prediction",0);
            double rate=Double.parseDouble(p.getString("rate","").replace(',','.'));
            double cap=Double.parseDouble(p.getString("capacity",String.valueOf(s.capacity)).replace(',','.'));
            double n=NatPredictionMath.arrival(s.soc,s.km,rate,cap);
            return n<0 ? "NAT est. "+Math.round(n)+"%" : "NAT est. "+Math.round(n)+"%";
        } catch(RuntimeException e) { return ""; }
    }
    private static EditText field(LinearLayout layout,String label,String value) {
        TextView t=new TextView(layout.getContext()); t.setText(label); layout.addView(t);
        EditText e=new EditText(layout.getContext()); e.setSingleLine(true); e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED); e.setText(value); layout.addView(e); return e;
    }
    private static double number(EditText e) { return Double.parseDouble(e.getText().toString().trim().replace(',','.')); }
    static void show(Activity a) {
        SharedPreferences p=a.getSharedPreferences("nat_prediction",0);
        Snapshot s=snapshot;
        boolean live=s!=null && SystemClock.elapsedRealtime()-s.at<=30000 && BydBatteryPatch.isActive();
        LinearLayout l=new LinearLayout(a); l.setOrientation(LinearLayout.VERTICAL); int pad=(int)(20*a.getResources().getDisplayMetrics().density); l.setPadding(pad,pad,pad,pad);
        TextView note=new TextView(a); note.setText(live?"ข้อมูลจาก forecast ล่าสุด ณ เวลาเปิดหน้าต่าง / ตรวจสอบเส้นทางก่อนใช้":"ไม่มี forecast สด — กรอกข้อมูลเอง (ไม่ใช่ข้อมูลสด)"); l.addView(note); if(live && s.charges==0) { TextView estimate=new TextView(a); double energy=s.remainingKwh; if(energy>=0&&s.km>0) estimate.setText(String.format(Locale.US,"SOC-based estimate: %.1f kWh • %.1f kWh/100 km",energy,energy*100/s.km)); else estimate.setText("Energy estimate unavailable"); l.addView(estimate); } if(live && s.charges>0) { TextView plan=new TextView(a); plan.setText("แผนจาก Maps (ไม่มีชื่อสถานีใน forecast)"+s.plan+"\nปลายทาง: "+(s.google+"%")); l.addView(plan); }
        EditText soc=field(l,"แบตปัจจุบัน %",live?fmt(s.soc):"");
        EditText km=field(l,"ระยะทางคงเหลือ กม.",live?fmt(s.km):"");
        EditText cap=field(l,"ความจุใช้งาน kWh (ปรับได้)",p.getString("capacity",live?fmt(s.capacity):""));
        EditText rate=field(l,"อัตรากินไฟ kWh/100 กม. (กรอกเอง)",p.getString("rate",""));
        EditText reserve=field(l,"แบตสำรอง %",p.getString("reserve",""));
        EditText google=field(l,"Google Estimate % (ไม่บังคับ)",live?String.valueOf(s.google):"");
        CheckBox charge=new CheckBox(a); charge.setText("มีแผนชาร์จระหว่างทาง"); charge.setChecked(live&&s.charges>0); l.addView(charge);
        TextView result=new TextView(a); result.setTextSize(20); l.addView(result);
        Button calc=new Button(a); calc.setText("คำนวณ / Calculate"); l.addView(calc);
        calc.setOnClickListener(v->{try {
            double r=number(reserve); if(!Double.isFinite(r)||r<0||r>100) throw new IllegalArgumentException();
            double n=NatPredictionMath.arrival(number(soc),number(km),number(rate),number(cap));
            p.edit().putString("rate",rate.getText().toString()).putString("capacity",cap.getText().toString()).putString("reserve",reserve.getText().toString()).apply();
            String g=google.getText().toString().trim(); if(!g.isEmpty() && (!Double.isFinite(number(google))||number(google)>100)) throw new IllegalArgumentException();
            result.setText((g.isEmpty()?"":"Google: "+g+"%\n")+"NAT (ไม่ชาร์จ): "+(n<0?"พลังงานไม่พอ (ขาด "+fmt(-n)+"%)":fmt(n)+"%")+(n<r?"\nต่ำกว่าแบตสำรอง — วางแผนชาร์จ":"")+(charge.isChecked()?"\nมีจุดชาร์จ: ห้ามเทียบกับ Google โดยตรง":"")+"\nค่าประมาณ ไม่ใช่การรับประกันระยะทาง");
        } catch(RuntimeException e){result.setText("กรุณาตรวจสอบค่าที่กรอก");}});
        ScrollView scroll=new ScrollView(a); scroll.addView(l); new AlertDialog.Builder(a).setTitle("NAT Smart EV Prediction").setView(scroll).setPositiveButton("ปิด",null).show();
    }
    private static String fmt(double v){return String.format(Locale.ROOT,"%.1f",v);}
}
