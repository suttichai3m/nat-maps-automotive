package app.morphe.extension.mapsautomotive.patches;
import android.content.*;
import android.app.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.text.SimpleDateFormat;
/** Archives existing observed daily values only, no invented missing days. */
final class NatMonthlyEv {
 private static SharedPreferences pref(Context c){return c.getSharedPreferences("nat_monthly_ev_v1",0);}
 static synchronized void archive(Context c,SharedPreferences old,String next){
  String day=old.getString("day","");if(!day.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")||day.equals(next))return;
  try{JSONObject o=new JSONObject();o.put("km",old.getFloat("km",0));o.put("observed_km",old.getFloat("v34_observed_km",0));o.put("net",old.getFloat("v34_observed_net",0));o.put("pairs",old.getInt("v34_pairs",0));o.put("tariff_at_archive",NatV33Telemetry.tariff(c));pref(c).edit().putString(day,o.toString()).apply();}catch(Exception ignored){}
 }
 private static String month(){SimpleDateFormat f=new SimpleDateFormat("yyyy-MM",Locale.US);f.setTimeZone(TimeZone.getTimeZone("Asia/Bangkok"));return f.format(new Date());}
 static synchronized String summary(Context c,String month){
  double km=0,covered=0,kwh=0,cost=0;int days=0,energyDays=0;
  for(Map.Entry<String,?> e:pref(c).getAll().entrySet()){if(!e.getKey().startsWith(month+"-")||!(e.getValue() instanceof String))continue;try{JSONObject o=new JSONObject((String)e.getValue());km+=o.getDouble("km");days++;if(o.optInt("pairs")>0){double net=Math.max(0,o.getDouble("net"));covered+=o.getDouble("observed_km");kwh+=net;cost+=net*o.getDouble("tariff_at_archive");energyDays++;}}catch(Exception ignored){}}
  NatV33Telemetry.Today t=NatV33Telemetry.today();if(t!=null&&t.day.startsWith(month+"-")){days++;km+=t.km;if(t.detailPairs>0){double net=Math.max(0,t.observedNet);covered+=t.observedKm;kwh+=net;cost+=net*NatV33Telemetry.tariff(c);energyDays++;}}
  return month+"\nวันที่มีข้อมูล "+days+" • วันที่มีพลังงาน "+energyDays+"\nระยะทาง "+(days>0?String.format(Locale.US,"%.1f km",km):"—")+"\nพลังงานสุทธิ "+(energyDays>0?String.format(Locale.US,"%.1f kWh",kwh):"—")+"\nค่าไฟประมาณ "+(energyDays>0?String.format(Locale.US,"฿%.2f",cost):"—")+"\nอัตราสิ้นเปลืองช่วงที่วัด "+(covered>0?String.format(Locale.US,"%.1f kWh/100km",100*kwh/covered):"—")+"\nต้นทุนช่วงที่วัด "+(covered>0?String.format(Locale.US,"฿%.2f/km",cost/covered):"—")+"\nเฉพาะข้อมูลที่บันทึกตั้งแต่ติดตั้ง • อัตราค่าไฟ ณ วันเก็บสรุป";
 }
 static void show(Activity a){LinearLayout l=new LinearLayout(a);l.setOrientation(1);EditText e=new EditText(a);e.setSingleLine(true);e.setText(month());l.addView(e);TextView t=new TextView(a);t.setText(summary(a,month()));l.addView(t);Button b=new Button(a);b.setText("ดูเดือน YYYY-MM");l.addView(b);b.setOnClickListener(v->{String m=e.getText().toString().trim();if(m.matches("[0-9]{4}-(0[1-9]|1[0-2])"))t.setText(summary(a,m)+"\n\nเดือนก่อนหน้า\n"+summary(a,java.time.YearMonth.parse(m).minusMonths(1).toString()));else e.setError("YYYY-MM");});new AlertDialog.Builder(a).setTitle("Monthly EV Intelligence").setView(l).setPositiveButton("ปิด",null).show();}
}
