package app.morphe.extension.mapsautomotive.patches;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.*;
import java.util.Locale;
import org.json.JSONObject;

/** One explicit Smart EV entry point. Never resizes, reparents or replaces Maps or the dashboard. */
final class NatSmartEvMenu {
 private static final Handler MAIN=new Handler(Looper.getMainLooper());
 private final Activity a;
 private final boolean dark;
 private final NatPrediction.Snapshot route;
 private final Integer nativeArrival;
 private final double nativeKm;
 private final SharedPreferences prefs;
 private AlertDialog dialog;
 private LinearLayout body;
 private Runnable afterClose;
 private final int fg,muted,surface,card,accent;
 private NatSmartEvMenu(Activity a,View root){
  this.a=a;dark=ColorScheme.nightColors(a);
  NatPrediction.Snapshot f=NatPrediction.freshSnapshot();
  route=NatNavigationCard.matchesForecast(root,f)?f:null;
  nativeArrival=NatNavigationCard.verifiedSoc(root);nativeKm=NatNavigationCard.verifiedKm(root);
  prefs=a.getSharedPreferences("nat_v34_smart_ev",Context.MODE_PRIVATE);
  fg=dark?0xffedf4f7:0xff22333c;muted=dark?0xffb3c6d2:0xff4e6470;
  surface=dark?0xff101b29:0xfff4f8fa;card=dark?0xff1c3041:0xffffffff;
  accent=dark?0xff8bd2ff:0xff15549a;
 }
 static void show(Activity a){
  if(a==null||a.isFinishing()||a.isDestroyed()||!a.hasWindowFocus()||!NatSplitScreen.active(a)||!SafeMode.on(a,SafeMode.BYD_BATTERY))return;
  View root=a.getWindow().peekDecorView();if(root==null)return;
  new NatSmartEvMenu(a,root).open();
 }
 private int dp(int n){return Math.round(n*a.getResources().getDisplayMetrics().density);}
 private GradientDrawable bg(int c,int edge,int radius){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(radius));if(edge!=0)d.setStroke(dp(1),edge);return d;}
 private TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(a);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);return t;}
 private void line(String s){TextView t=text(s,13,muted,false);t.setPadding(dp(4),dp(5),dp(4),dp(5));body.addView(t);}
 private void action(String title,Runnable run){
  Button b=new Button(a);b.setAllCaps(false);b.setText(title);b.setTextSize(15);b.setTextColor(fg);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setPadding(dp(14),dp(6),dp(12),dp(6));b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(dark?0x33ffffff:0x22000000),bg(card,0,9),null));
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(5);body.addView(b,lp);
  b.setOnClickListener(v->{try{run.run();}catch(RuntimeException e){ReportLog.w("NatSmartEV","Optional page unavailable",e);Toast.makeText(a,"ไม่สามารถเปิดเมนูนี้ได้",Toast.LENGTH_SHORT).show();}});
 }
 private void heading(String s){body.removeAllViews();TextView t=text(s,20,fg,true);t.setPadding(dp(4),dp(4),dp(4),dp(10));body.addView(t);}
 private void metric(String label,String value){
  LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(10),dp(8),dp(10),dp(8));row.setBackground(bg(card,0,8));
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(4);body.addView(row,lp);
  row.addView(text(label,13,muted,false),new LinearLayout.LayoutParams(0,-2,1));
  TextView v=text(value,17,accent,true);v.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);row.addView(v,new LinearLayout.LayoutParams(0,-2,1));
 }
 private void open(){
  ScrollView scroll=new ScrollView(a);scroll.setFillViewport(false);body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(14),dp(12),dp(14),dp(12));body.setBackground(bg(surface,0,12));scroll.addView(body,new ScrollView.LayoutParams(-1,-2));
  dialog=new AlertDialog.Builder(a).setView(scroll).create();
  dialog.setOnDismissListener(d->{Runnable task=afterClose;afterClose=null;if(task!=null)runAfterFocus(task,8);});
  home();dialog.show();
  Window w=dialog.getWindow();if(w!=null){
   w.setBackgroundDrawableResource(android.R.color.transparent);w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
   View decor=a.getWindow().getDecorView();
   int maxW=Math.max(dp(180),(int)(decor.getWidth()*.75)-dp(24));
   int maxH=Math.max(dp(180),decor.getHeight()-dp(84));
   w.setLayout(Math.min(dp(370),maxW),Math.min(dp(490),maxH));
   WindowManager.LayoutParams lp=w.getAttributes();lp.gravity=Gravity.LEFT|Gravity.BOTTOM;lp.x=dp(10);lp.y=dp(62);w.setAttributes(lp);
  }
 }
 private void closeThen(Runnable r){afterClose=r;dialog.dismiss();}
 private void runAfterFocus(Runnable r,int tries){
  MAIN.postDelayed(()->{if(a.isFinishing()||a.isDestroyed()||!NatSplitScreen.active(a)||!SafeMode.on(a,SafeMode.BYD_BATTERY))return;
   if(!a.hasWindowFocus()){if(tries>0)runAfterFocus(r,tries-1);return;}
   try{r.run();}catch(RuntimeException e){ReportLog.w("NatSmartEV","Linked page unavailable",e);Toast.makeText(a,"ไม่สามารถเปิดเมนูนี้ได้",Toast.LENGTH_SHORT).show();}
  },100);
 }
 private void home(){
  heading("Smart EV  •  NAT V4.0 Candidate");
  line("เลือกฟังก์ชัน • รายละเอียดควรเปิดดูเมื่อจอดรถ");
  action("Grab / Bolt / Private • Driver Mode",()->closeThen(()->NatDriverWork.show(a)));
  action("Favorite Apps Manager",()->closeThen(()->NatV36Shortcuts.favoriteApps(a)));
  action("Monthly EV Intelligence",()->closeThen(()->NatMonthlyEv.show(a)));
  action("Smart Resume / Driving Mode",()->closeThen(()->NatSplitScreen.personalize(a)));
  action("★  Favorite Destinations • สถานที่โปรด",()->closeThen(()->NatFavorites.show(a)));
  action("⚡  Smart Charging Assistant",this::charging);
  action("◷  Daily Driving Timeline",()->closeThen(()->NatTripTimeline.show(a)));
  action("▥  Energy Intelligence",this::energy);
  action("＋  Vehicle Health Monitor",this::health);
  action("❄  Snow AI",()->closeThen(()->NatV36Shortcuts.snow(a)));
  action("OverDrive",()->closeThen(()->NatV36Shortcuts.control(a)));
  action("ตั้งค่าปุ่ม OverDrive",()->closeThen(()->NatV36Shortcuts.configureOverDrive(a)));
  action("Charging Monitor / History",this::chargingHistory);
  action("Service Reminder",this::service);
  action("Custom Dashboard",()->closeThen(()->NatSplitScreen.customize(a)));
  action("Send to Car • สถานะการรับเส้นทาง",()->closeThen(()->SendToCarStatus.show(a,a.getWindow().peekDecorView())));
  action("ปิด / Close",()->dialog.dismiss());
 }
 private String num(double v,String unit){return Double.isFinite(v)?String.format(Locale.US,"%.1f %s",v,unit):"—";}
 private String money(double v){return Double.isFinite(v)?String.format(Locale.US,"฿%.2f",v):"—";}
 private boolean currentRoute(){return route!=null&&route==NatPrediction.freshSnapshot();}
 private void charging(){
  heading("Smart Charging Assistant");
  // Captured only before the dialog took window focus. No use of an unmatched or expired forecast.
  boolean valid=currentRoute();
  double reserve=prefs.getFloat("reserve",15f);if(!Double.isFinite(reserve)||reserve<0||reserve>100)reserve=15;
  double power=prefs.getFloat("assumed_kw",50f);if(!Double.isFinite(power)||power<=0||power>350)power=50;
  Integer arrival=valid?Integer.valueOf(route.google):nativeArrival;
  if(arrival==null)metric("แบตปลายทาง","—");else metric("แบตปลายทาง (ตอนเปิด)",arrival+"%");
  metric("ระยะทาง (ตอนเปิด)",num(valid?route.km:nativeKm,"km"));
  double back=valid&&route.charges==0?NatFavoriteRules.roundTrip(route.soc,route.google):Double.NaN;
  metric("แบตกลับ (สมมติใช้พลังงานเท่าขาไป)",num(back,"%"));
  line("ขากลับเป็นค่าประมาณจากขาไป ไม่ใช่เส้นทางย้อนกลับที่ Maps คำนวณ");
  metric("แบตสำรองเป้าหมาย",num(reserve,"%"));
  final TextView result=text("",15,fg,true);body.addView(result);
  LinearLayout controls=new LinearLayout(a);body.addView(controls);
  EditText reserveField=numberField(controls,"สำรอง %",reserve),powerField=numberField(controls,"สมมติ kW",power);
  Runnable calc=()->{
   try{
    double rr=Double.parseDouble(reserveField.getText().toString()),kw=Double.parseDouble(powerField.getText().toString());
    if(!Double.isFinite(rr)||rr<0||rr>100||!Double.isFinite(kw)||kw<=0||kw>350)throw new IllegalArgumentException();
    prefs.edit().putFloat("reserve",(float)rr).putFloat("assumed_kw",(float)kw).apply();
    if(!currentRoute()){result.setText("ไม่มี Forecast ที่ยืนยันได้ • ปิดและเปิดใหม่เมื่อมีเส้นทาง");return;}
    double kwh=NatDailyMath.reserveEnergy(route.capacity,route.google,rr);
    if(route.charges>0){result.setText("มีแผนชาร์จจาก Maps อยู่แล้ว "+route.charges+" จุด\n"+route.plan+"\nตรวจสอบ SOC หลังเพิ่มจุดแวะ");return;}
    String status=route.google<0?"พลังงานไม่พอถึงปลายทาง":route.google<rr?"ต่ำกว่าแบตสำรองที่ตั้งไว้":"ถึงเป้าหมายแบตสำรองตาม Forecast";
    result.setText(status+"\nพลังงานเพิ่มขั้นต่ำ "+num(kwh,"kWh")+"\nเวลาชาร์จสมมติ "+num(NatDailyMath.chargeMinutes(kwh,kw),"นาที")+"\nค่าไฟประมาณ "+money(NatDailyMath.cost(kwh,NatV33Telemetry.tariff(a))));
   }catch(RuntimeException e){result.setText("ตรวจสอบแบตสำรอง 0–100% และกำลังชาร์จ 0–350 kW");}
  };
  calc.run();action("คำนวณ / บันทึกเป้าหมาย",calc);
  line("ค่าประมาณ ณ เวลาเปิด • ไม่รวมการสูญเสียและช่วงลดกำลังชาร์จ • ไม่ยืนยันว่ามีสถานีว่าง");
  line(NatMinimalEv.layerStatus(a));
  action("EV Charging Full Map • Native layer",()->closeThen(()->NatMinimalEv.splitAction(a,0)));
  action("ค้นหาสถานีชาร์จ (Maps) • ทางเลือกเดิม",()->closeThen(()->NatMinimalEv.splitAction(a,5)));
  action("แผนการชาร์จเดิม",()->closeThen(()->NatMinimalEv.splitAction(a,2)));
  action("‹ กลับเมนู",this::home);
 }
 private EditText numberField(LinearLayout row,String label,double value){
  LinearLayout col=new LinearLayout(a);col.setOrientation(LinearLayout.VERTICAL);col.setPadding(dp(3),0,dp(3),0);row.addView(col,new LinearLayout.LayoutParams(0,-2,1));col.addView(text(label,12,muted,false));
  EditText e=new EditText(a);e.setTextColor(fg);e.setSingleLine(true);e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);e.setText(String.format(Locale.US,"%.1f",value));col.addView(e);return e;
 }
 private void energy(){
  heading("Energy Intelligence");
  NatV33Telemetry.Today t=NatV33Telemetry.today();boolean detail=t!=null&&t.detailPairs>0;
  double price=NatV33Telemetry.tariff(a),net=t==null?Double.NaN:t.kwh;
  double covered=detail?t.observedKm:Double.NaN,observed=detail?Math.max(0,t.observedNet):Double.NaN;
  NatV36Daily.Summary day=NatV36Daily.read(a);
  metric("เวลาขับที่บันทึก",day==null?"—":NatV36Daily.duration(day.moving));
  metric("เวลาหยุดที่บันทึก",day==null?"—":NatV36Daily.duration(day.stopped));
  metric("ระยะทางวันนี้",num(t==null?Double.NaN:t.km,"km"));metric("พลังงานสุทธิที่บันทึก",num(net,"kWh"));
  metric("ค่าไฟ / kWh",money(price));metric("ค่าไฟวันนี้ (ประมาณ)",money(NatDailyMath.cost(net,price)));
  metric("ระยะทางที่มีคู่ข้อมูล V3.4",num(covered,"km"));metric("พลังงานลดลงขณะขับ",num(detail?t.positive:Double.NaN,"kWh"));
  metric("พลังงานเพิ่มขณะขับ",num(detail?t.increase:Double.NaN,"kWh"));
  metric("สิ้นเปลืองช่วงที่บันทึก",num(NatDailyMath.forecastRate(observed,covered),"kWh/100 km"));
  metric("ต้นทุนช่วงที่บันทึก",money(NatDailyMath.perKm(NatDailyMath.cost(observed,price),covered))+" / กม.");
  line("ข้อมูลบางช่วงเท่านั้น • พลังงานแบตเพิ่มไม่ใช่ค่า REGEN ที่ยืนยัน • รายละเอียดเริ่มสะสมตั้งแต่ V3.4");
  action("รีเฟรชสรุป",this::energy);action("ดูประวัติ / Export",()->closeThen(()->NatTripTimeline.show(a)));action("‹ กลับเมนู",this::home);
 }
 private void chargingHistory(){
  heading("Charging Monitor / History");
  NatV36Daily.Energy e=NatV36Daily.energy();
  metric("SOC / พลังงานคงเหลือ",e==null?"—":num(e.soc,"%")+" / "+num(e.kwh,"kWh"));
  metric("กำลังชาร์จจริง / เวลาที่เหลือ","ยังไม่ยืนยัน SDK");
  line("บันทึกช่วงชาร์จด้วยตนเองเมื่อจอดรถ • ค่าพลังงานเพิ่มสุทธิจาก BMS ไม่ใช่ไฟที่สถานีคิดเงิน");
  line(NatV36Daily.chargeStatus(a));
  action("เริ่มบันทึกช่วงชาร์จ",()->{line(NatV36Daily.beginCharge(a));});
  action("จบบันทึกช่วงชาร์จ",()->{line(NatV36Daily.endCharge(a));});
  action("ยกเลิกช่วงชาร์จ",()->new AlertDialog.Builder(a).setTitle("ยกเลิกช่วงที่กำลังบันทึก?").setNegativeButton("กลับ",null).setPositiveButton("ยืนยัน",(d,w)->{NatV36Daily.cancelCharge(a);chargingHistory();}).show());
  line(NatV36Daily.history(a));
  action("รีเฟรช",this::chargingHistory);action("‹ กลับเมนู",this::home);
 }
 private void service(){
  heading("Service Reminder");
  NatV33Telemetry.Slow s=NatV33Telemetry.slow();
  metric("ODO ล่าสุด",num(s==null?Double.NaN:s.odo,"km"));
  double target=prefs.getFloat("service_odo",0f);
  metric("ระยะที่ตั้งไว้",target>0?num(target,"km"):"—");
  metric("เหลือถึงนัด",target>0&&s!=null&&Double.isFinite(s.odo)?num(target-s.odo,"km"):"—");
  LinearLayout controls=new LinearLayout(a);body.addView(controls);
  EditText input=numberField(controls,"กำหนด ODO นัดถัดไป",target);
  action("บันทึกระยะเข้าศูนย์",()->{try{double v=Double.parseDouble(input.getText().toString());if(!Double.isFinite(v)||v<=0||v>2000000)throw new IllegalArgumentException();prefs.edit().putFloat("service_odo",(float)v).apply();service();}catch(RuntimeException ex){input.setError("กรอกเลขไมล์ที่ถูกต้อง");}});
  line("ระยะที่ผู้ใช้กำหนดเอง • ไม่ใช่กำหนดการจาก BYD");action("‹ กลับเมนู",this::home);
 }
 private String jsonValue(JSONObject j,String key,String suffix){return j==null||j.isNull(key)?"—":j.optString(key,"—")+suffix;}
 private void health(){
  heading("Vehicle Health Monitor");
  JSONObject j=null;try{j=BydBatteryPatch.natHealthSnapshot();}catch(Exception ignored){}
  JSONObject temp=j==null?null:j.optJSONObject("temp_c");
  long age=j==null?-1:j.optLong("soh_read_age_s",-1),ta=j==null?-1:j.optLong("temp_read_age_s",-1);
  boolean validHealth=age>=0&&age<=3600,validTemp=ta>=0&&ta<=180;
  metric("สุขภาพแบตเตอรี่ SOH",jsonValue(validHealth?j:null,"soh_percent","%"));
  metric("อายุข้อมูล SOH",age<0?"—":age+" s");
  metric("อุณหภูมิแบตเฉลี่ย",jsonValue(validTemp?temp:null,"average"," °C"));
  metric("สูงสุด / ต่ำสุด",jsonValue(validTemp?temp:null,"highest","°C")+" / "+jsonValue(validTemp?temp:null,"lowest","°C"));
  metric("อายุข้อมูลอุณหภูมิ",ta<0?"—":ta+" s");
  NatVehicleExtras.Values e=NatVehicleExtras.read();
  metric("ยางหน้า L / R (PSI)",e==null?"—":e.front);metric("ยางหลัง L / R (PSI)",e==null?"—":e.rear);
  line(NatVehicleExtras.tyreDiagnostics());
  metric("12V","ยังไม่รองรับ");
  action("TPMS Diagnostics",()->new AlertDialog.Builder(a).setTitle("TPMS • SDK status").setMessage(NatVehicleExtras.tyreDiagnostics()).setPositiveButton("ปิด",null).show());
  line("อ่านจากข้อมูลสุขภาพแบตเดิมของ BYD • ไม่ใช่การวินิจฉัยความปลอดภัยของรถ • ไม่แสดงค่าที่หมดอายุ");
  action("รีเฟรชข้อมูล",this::health);action("System Diagnostics",()->closeThen(()->NatMinimalEv.splitAction(a,4)));action("‹ กลับเมนู",this::home);
 }
}
