package app.morphe.extension.mapsautomotive.patches;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.*;
import android.view.View;
import org.json.*;
import java.util.*;
import java.text.SimpleDateFormat;

/** Opt-in local work ledger, not a Grab API or automatic trip detector. Existing BYD sample cadence. */
final class NatDriverWork {
 private static final String PREF="nat_driver_work_v1";
 private static JSONObject state;private static long at=-1;private static int odo=-1;private static double wh=Double.NaN,speed=Double.NaN;
 private static SharedPreferences pref(Context c){return c.getSharedPreferences(PREF,0);}
 private static String date(long t){SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.US);f.setTimeZone(TimeZone.getTimeZone("Asia/Bangkok"));return f.format(new Date(t));}
 private static JSONObject json(String s){try{return new JSONObject(s);}catch(Exception e){throw new IllegalStateException("Driver ledger invalid; preserve original data",e);}}
 private static JSONObject data(Context c){if(state==null)state=json(pref(c).getString("ledger","{}"));return state;}
 private static void put(JSONObject o,String k,Object v){try{o.put(k,v);}catch(JSONException e){throw new IllegalArgumentException(e);}}
 private static double num(JSONObject o,String k){return o.optDouble(k,0);}
 private static void add(JSONObject o,String k,double d){if(Double.isFinite(d))put(o,k,num(o,k)+d);}
 private static JSONArray array(JSONObject o,String k){JSONArray a=o.optJSONArray(k);if(a==null){a=new JSONArray();put(o,k,a);}return a;}
 private static JSONObject active(Context c){return data(c).optJSONObject("active");}
 private static void save(Context c){pref(c).edit().putString("ledger",data(c).toString()).apply();}
 private static void reset(){at=-1;odo=-1;wh=speed=Double.NaN;}
 static boolean dashboard(Context c){return pref(c).getBoolean("dashboard",false);}
 static void dashboard(Context c,boolean on){pref(c).edit().putBoolean("dashboard",on).apply();}
 private static boolean parked(){NatV36Daily.Energy e=NatV36Daily.energy();return e!=null&&NatDriverRules.stopped(e.speed);}
 static synchronized void sample(Context c,BydEnergyReader.Sample s){
  JSONObject a=active(c);if(a==null){reset();return;}
  int phase=a.optInt("phase");if(phase==NatDriverRules.BREAK){reset();return;}
  long dt=s.elapsed-at;
  if(at>=0&&NatDriverRules.interval(dt)){
   add(a,"observed_ms",dt);add(a,"phase_ms_"+phase,dt);
   double km=NatDriverRules.km(odo,s.mileage,dt);
   if(Double.isFinite(km)){
    add(a,"km",km);add(a,"phase_km_"+phase,km);add(a,"odo_pairs",1);
    JSONObject trip=a.optJSONObject("trip");if(trip!=null){add(trip,"km",km);add(trip,"phase_km_"+phase,km);add(trip,"ms",dt);}
    if(Double.isFinite(wh)&&Double.isFinite(s.energy)&&s.energy>=0&&s.energy<=150&&speed>1&&s.speedKmh>1){
     double delta=wh-s.energy;if(Math.abs(delta)<=250*dt/3600000.0+.15){add(a,"net_kwh",delta);add(a,"energy_km",km);add(a,"energy_pairs",1);if(trip!=null){add(trip,"net_kwh",delta);add(trip,"energy_pairs",1);}}
    }
   }else add(a,"gaps",1);
  }else if(at>=0)add(a,"gaps",1);
  at=s.elapsed;odo=s.mileage;wh=s.energy;speed=s.speedKmh;save(c);
 }
 private static synchronized String start(Context c){
  if(!parked())return "ต้องจอดรถและมีข้อมูลความเร็วล่าสุด";
  if(active(c)!=null)return "มีช่วงงานเปิดอยู่";
  if(array(data(c),"history").length()>=365)return "ประวัติเต็ม 365 ช่วง • ส่งออกและล้างก่อน";
  JSONObject a=new JSONObject();long now=System.currentTimeMillis();put(a,"start",now);put(a,"day",date(now));put(a,"phase",NatDriverRules.WAIT);put(a,"tariff",NatV33Telemetry.tariff(c));put(a,"fixed",pref(c).getFloat("fixed",0));put(a,"target",pref(c).getFloat("target",2000));
  NatV36Daily.Energy e=NatV36Daily.energy();if(e!=null&&Double.isFinite(e.soc))put(a,"start_soc",e.soc);
  put(data(c),"active",a);reset();save(c);return "เริ่มงานแล้ว • GPS ใช้ Timeline แยกตามการอนุญาต";
 }
 private static synchronized String transition(Context c,int next,String platform,String zone,double fare,double fees){
  JSONObject a=active(c);if(a==null)return "เริ่มงานก่อน";
  if(!parked())return "เปลี่ยนสถานะหรือกรอกข้อมูลเมื่อจอดรถเท่านั้น";
  int phase=a.optInt("phase");if(!NatDriverRules.can(phase,next))return "ลำดับสถานะไม่ถูกต้อง";
  if(next==NatDriverRules.PICKUP){
   if(array(a,"trips").length()>=300)return "เต็ม 300 เที่ยวต่อช่วงงาน";
   JSONObject t=new JSONObject();put(t,"platform",platform);put(t,"zone",zone);put(t,"start",System.currentTimeMillis());put(a,"trip",t);
  }
  if(phase==NatDriverRules.PICKUP&&next==NatDriverRules.WAIT){a.remove("trip");add(a,"cancelled",1);}
  if(phase==NatDriverRules.RIDE&&next==NatDriverRules.WAIT){
   if(!NatDriverRules.amount(fare)||!NatDriverRules.amount(fees))return "ตรวจสอบยอดค่าโดยสาร/ค่าธรรมเนียม";
   JSONObject t=a.optJSONObject("trip");if(t==null)return "ไม่พบเที่ยวงาน";
   put(t,"fare",fare);put(t,"fees",fees);put(t,"end",System.currentTimeMillis());array(a,"trips").put(t);add(a,"fare",fare);add(a,"fees",fees);a.remove("trip");
  }
  if(next==NatDriverRules.OFF){put(a,"end",System.currentTimeMillis());NatV36Daily.Energy e=NatV36Daily.energy();if(e!=null&&Double.isFinite(e.soc))put(a,"end_soc",e.soc);array(data(c),"history").put(a);data(c).remove("active");}
  else put(a,"phase",next);
  // Do not allocate an interval crossing a manually marked boundary.
  reset();add(a,"boundaries",1);save(c);return "บันทึกแล้ว";
 }
 private static String money(double v){return Double.isFinite(v)?String.format(Locale.US,"฿%,.2f",v):"—";}
 private static String fmt(double v){return Double.isFinite(v)?String.format(Locale.US,"%.1f",v):"—";}
 private static String phase(int p){switch(p){case 1:return "รองาน";case 2:return "ไปรับ";case 3:return "ส่งผู้โดยสาร";case 4:return "พัก";default:return "หยุดงาน";}}
 private static double kwh(JSONObject a){return num(a,"energy_pairs")>0?Math.max(0,num(a,"net_kwh")):Double.NaN;}
 private static String report(JSONObject a){
  double km=num(a,"odo_pairs")>0?num(a,"km"):Double.NaN,dead=num(a,"phase_km_1")+num(a,"phase_km_2"),energy=kwh(a),fare=num(a,"fare"),fees=num(a,"fees");
  double cost=Double.isFinite(energy)?energy*num(a,"tariff"):Double.NaN;
  return "รายได้ "+money(fare)+" / "+money(num(a,"target"))+"\nค่าธรรมเนียม "+money(fees)+"\nเที่ยว "+array(a,"trips").length()+" • "+NatV36Daily.duration((long)num(a,"observed_ms"))+" ที่บันทึก\nรองาน "+NatV36Daily.duration((long)num(a,"phase_ms_1"))+"\nระยะที่บันทึก "+fmt(km)+" km\nรถเปล่า "+fmt(Double.isFinite(km)?dead:Double.NaN)+" km ("+fmt(NatDriverRules.dead(dead,km))+"%)\nค่าไฟเฉพาะช่วงที่วัด "+money(cost)+"\nหลังค่าธรรมเนียมและค่าไฟที่วัด "+money(fare-fees-cost)+"\nหลังหักต้นทุนที่ตั้ง "+money(NatDriverRules.profit(fare,fees,energy,num(a,"tariff"),num(a,"fixed")))+"\nรายได้/ชม. ที่บันทึก "+money(NatDriverRules.perHour(fare,(long)num(a,"observed_ms")))+"\nข้อมูลบางช่วง • ไม่ใช่กำไรเต็มวัน\nช่องว่าง "+(long)num(a,"gaps");
 }
 static synchronized String summary(Context c){JSONObject a=active(c);return a==null?"DRIVER MODE\nยังไม่เริ่มงาน\nSmart EV → Driver Mode":"DRIVER MODE • "+phase(a.optInt("phase"))+"\n"+report(a);}
 private static void msg(Activity a,String s){Toast.makeText(a,s,Toast.LENGTH_LONG).show();}
 private static void btn(Activity a,LinearLayout l,String s,Runnable r){Button b=new Button(a);b.setText(s);b.setAllCaps(false);l.addView(b);b.setOnClickListener(v->{try{r.run();}catch(RuntimeException e){msg(a,"ไม่สามารถบันทึก • ตรวจสอบข้อมูล");}});}
 private static EditText field(Activity a,LinearLayout l,String label,String value,boolean number){TextView t=new TextView(a);t.setText(label);l.addView(t);EditText e=new EditText(a);e.setSingleLine(true);if(number)e.setInputType(8194);e.setText(value);l.addView(e);return e;}
 private static double val(EditText e){try{return Double.parseDouble(e.getText().toString().trim());}catch(Exception x){return Double.NaN;}}
 static void show(Activity a){
  if(a==null||a.isDestroyed()||a.isFinishing()||!a.hasWindowFocus()||!SafeMode.on(a,SafeMode.BYD_BATTERY))return;
  LinearLayout l=new LinearLayout(a);l.setOrientation(1);l.setPadding(16,12,16,12);ScrollView sc=new ScrollView(a);sc.addView(l);
  TextView title=new TextView(a);l.addView(title);title.setText(summary(a));
  Runnable refresh=()->title.setText(summary(a));
  Switch sw=new Switch(a);sw.setText("แสดง Driver Dashboard ด้านขวา");sw.setChecked(dashboard(a));l.addView(sw);sw.setOnCheckedChangeListener((b,v)->dashboard(a,v));
  btn(a,l,"Start Work",()->{msg(a,start(a));refresh.run();});
  Spinner platform=new Spinner(a);platform.setAdapter(new ArrayAdapter<String>(a,android.R.layout.simple_spinner_dropdown_item,new String[]{"Grab","Bolt","Private"}));l.addView(platform);
  EditText zone=field(a,l,"โซนรับงาน (ไม่บันทึกชื่อผู้โดยสาร)","",false);zone.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(64)});
  btn(a,l,"Accept Job / ไปรับ",()->{msg(a,transition(a,2,String.valueOf(platform.getSelectedItem()),zone.getText().toString().trim(),0,0));refresh.run();});
  btn(a,l,"Picked Up / รับแล้ว",()->{msg(a,transition(a,3,"","",0,0));refresh.run();});
  btn(a,l,"Cancel Pickup",()->{JSONObject x=active(a);if(x!=null&&x.optInt("phase")==2)msg(a,transition(a,1,"","",0,0));else msg(a,"ใช้ได้เฉพาะช่วงไปรับ");refresh.run();});
  EditText fare=field(a,l,"ค่าโดยสารรวม (บาท)","0",true),fees=field(a,l,"ค่าธรรมเนียม/ทางด่วนที่จ่ายเอง (บาท)","0",true);
  btn(a,l,"Complete Trip",()->{JSONObject x=active(a);if(x!=null&&x.optInt("phase")==3){msg(a,transition(a,1,"","",val(fare),val(fees)));}else msg(a,"ยังไม่มีเที่ยวที่รับผู้โดยสารแล้ว");refresh.run();});
  btn(a,l,"Break",()->{msg(a,transition(a,4,"","",0,0));refresh.run();});
  btn(a,l,"Resume Work",()->{JSONObject x=active(a);if(x!=null&&x.optInt("phase")==4)msg(a,transition(a,1,"","",0,0));else msg(a,"ยังไม่ได้พัก");refresh.run();});
  btn(a,l,"End Work",()->{msg(a,transition(a,0,"","",0,0));refresh.run();});
  btn(a,l,"Refresh",refresh);
  btn(a,l,"Job Calculator",()->calculator(a));
  btn(a,l,"Daily / Monthly / Zone history",()->history(a));
  btn(a,l,"ตั้งเป้าหมาย / ต้นทุนคงที่ต่อช่วงงาน",()->settings(a));
  btn(a,l,"Export Driver JSON (local)",()->export(a));
  TextView note=new TextView(a);note.setText("GPS: เปิด Daily Driving Timeline แยกต่างหากเพื่อบันทึกเส้นทาง • ไม่อ่านแอป Grab/Bolt • ไม่มีการรับงานอัตโนมัติ • เวลาที่ขาดข้อมูลไม่นับ • ตั้งค่าขณะจอด");l.addView(note);
  new AlertDialog.Builder(a).setTitle("NAT Driver Work Center").setView(sc).setPositiveButton("ปิด",null).show();
 }
 private static void calculator(Activity a){
  LinearLayout l=new LinearLayout(a);l.setOrientation(1);
  EditText fare=field(a,l,"ค่าโดยสาร","",true),pickup=field(a,l,"ระยะไปรับ km","",true),trip=field(a,l,"ระยะไปส่ง km","",true),mins=field(a,l,"เวลารวม นาที","",true),rate=field(a,l,"สมมติ kWh/100km","16",true),tariff=field(a,l,"บาท/kWh",String.valueOf(NatV33Telemetry.tariff(a)),true),fee=field(a,l,"ค่าธรรมเนียม","0",true);
  TextView out=new TextView(a);l.addView(out);btn(a,l,"คำนวณ",()->{double p=val(pickup),t=val(trip),f=val(fare),m=val(mins),e=NatDriverRules.energy(p+t,val(rate));double profit=NatDriverRules.amount(p)&&NatDriverRules.amount(t)?NatDriverRules.profit(f,val(fee),e,val(tariff),0):Double.NaN;out.setText("กำไรขั้นต้น "+money(profit)+"\nค่าไฟประมาณ "+money(e*val(tariff))+"\nต่อชั่วโมง "+money(m>0?profit*60/m:Double.NaN)+"\nรถเปล่า "+fmt(NatDriverRules.dead(p,p+t))+"%\nไม่รวมต้นทุนคงที่");});
  ScrollView sc=new ScrollView(a);sc.addView(l);new AlertDialog.Builder(a).setTitle("Smart Job Calculator").setView(sc).setPositiveButton("ปิด",null).show();
 }
 private static void settings(Activity a){LinearLayout l=new LinearLayout(a);l.setOrientation(1);EditText target=field(a,l,"เป้าหมายต่อช่วงงาน",String.valueOf(pref(a).getFloat("target",2000)),true),fixed=field(a,l,"ต้นทุนคงที่ต่อช่วงงาน",String.valueOf(pref(a).getFloat("fixed",0)),true);new AlertDialog.Builder(a).setTitle("ใช้กับช่วงงานถัดไป").setView(l).setNegativeButton("ยกเลิก",null).setPositiveButton("บันทึก",(d,w)->{if(NatDriverRules.amount(val(target))&&NatDriverRules.amount(val(fixed)))pref(a).edit().putFloat("target",(float)val(target)).putFloat("fixed",(float)val(fixed)).apply();else msg(a,"ข้อมูลไม่ถูกต้อง");}).show();}
 private static synchronized String historyText(Context c){
  JSONArray h=array(data(c),"history");String month=date(System.currentTimeMillis()).substring(0,7);double income=0,fees=0,km=0;long observed=0;int count=0;Map<String,double[]> zones=new TreeMap<>();StringBuilder recent=new StringBuilder();
  for(int i=h.length()-1;i>=0;i--){JSONObject a=h.optJSONObject(i);if(a==null)continue;if(i>=h.length()-10)recent.append("\n").append(a.optString("day")).append("\n").append(report(a)).append("\n");if(!a.optString("day").startsWith(month))continue;income+=num(a,"fare");fees+=num(a,"fees");km+=num(a,"km");observed+=(long)num(a,"observed_ms");count++;JSONArray trips=array(a,"trips");for(int k=0;k<trips.length();k++){JSONObject t=trips.optJSONObject(k);if(t==null)continue;String z=t.optString("zone").trim();if(z.isEmpty())continue;double[] q=zones.get(z);if(q==null){q=new double[3];zones.put(z,q);}q[0]++;q[1]+=num(t,"fare")-num(t,"fees");q[2]+=num(t,"ms");}}
  StringBuilder b=new StringBuilder(month+" • "+count+" ช่วงงาน (นับตามวันเริ่ม)\nรายได้ "+money(income)+"\nหลังค่าธรรมเนียม "+money(income-fees)+"\nระยะที่บันทึก "+fmt(km)+" km\nเวลา "+NatV36Daily.duration(observed)+"\n\nโซนจากประวัติ (ไม่ใช่ demand สด)\n");
  ArrayList<Map.Entry<String,double[]>> list=new ArrayList<>(zones.entrySet());list.sort((x,y)->Double.compare(y.getValue()[1]/y.getValue()[0],x.getValue()[1]/x.getValue()[0]));for(Map.Entry<String,double[]> e:list){double[] q=e.getValue();b.append(e.getKey()).append(" • ").append((int)q[0]).append(" เที่ยว • เฉลี่ย ").append(money(q[1]/q[0])).append("\n");}
  return b.append("\n10 ช่วงล่าสุด\n").append(recent).toString();
 }
 private static void history(Activity a){TextView t=new TextView(a);t.setText(historyText(a));t.setPadding(16,12,16,12);ScrollView sc=new ScrollView(a);sc.addView(t);new AlertDialog.Builder(a).setTitle("Driver History / Smart Zone").setView(sc).setPositiveButton("ปิด",null).show();}
 private static synchronized void export(Activity a){try{java.io.File dir=a.getExternalFilesDir("nat_driver");if(dir==null)dir=a.getFilesDir();if(!dir.exists()&&!dir.mkdirs())throw new java.io.IOException();java.io.File file=new java.io.File(dir,"nat-driver-"+System.currentTimeMillis()+".json");try(java.io.Writer w=new java.io.OutputStreamWriter(new java.io.FileOutputStream(file),java.nio.charset.StandardCharsets.UTF_8)){JSONObject o=new JSONObject();put(o,"schema","nat_driver_work_v1");put(o,"ledger",data(a));w.write(o.toString(2));}msg(a,"บันทึก "+file.getAbsolutePath());}catch(Exception ex){msg(a,"Export ไม่สำเร็จ");}}
}
