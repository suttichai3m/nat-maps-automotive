package app.morphe.extension.mapsautomotive.patches;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Locale;

/** Only exported launcher activities. No SDK actuator writes, no permission bypass. */
final class NatV36Shortcuts {
 private static boolean ready(Activity a){return a!=null&&!a.isFinishing()&&!a.isDestroyed()&&a.hasWindowFocus();}
 private static ArrayList<ResolveInfo> apps(Activity a,boolean snow){
  ArrayList<ResolveInfo> out=new ArrayList<>();
  PackageManager pm=a.getPackageManager();
  for(ResolveInfo r:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)){
   if(r.activityInfo==null||!r.activityInfo.exported||!r.activityInfo.enabled||r.activityInfo.packageName.equals(a.getPackageName()))continue;
   String label=String.valueOf(r.loadLabel(pm)).trim().toLowerCase(Locale.ROOT);
   boolean match=snow?(label.equals("snow assistant")||label.equals("snow ai")||label.equals("snow-ai")):
     r.activityInfo.packageName.startsWith("com.byd.")&&(r.activityInfo.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)!=0&&(label.contains("vehicle")||label.contains("car setting")||label.contains("ตั้งค่ารถ")||label.contains("ควบคุมรถ")||label.contains("รถยนต์"));
   if(match)out.add(r);
  }return out;
 }
 private static void launch(Activity a,ResolveInfo r){try{
  if(a.isDestroyed()||a.isFinishing())return;
  Intent i=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(new ComponentName(r.activityInfo.packageName,r.activityInfo.name));a.startActivity(i);
 }catch(RuntimeException e){Toast.makeText(a,"เปิดแอปไม่ได้",Toast.LENGTH_SHORT).show();}}
 private static void select(Activity a,boolean snow){
  ArrayList<ResolveInfo> list=apps(a,snow);
  if(list.isEmpty()){new AlertDialog.Builder(a).setTitle(snow?"Snow AI":"BYD Vehicle Control").setMessage("ไม่พบแอปที่เปิดได้จาก launcher • เปิดจากหน้าหลัก BYD ตามปกติ").setPositiveButton("ปิด",null).show();return;}
  if(snow&&list.size()==1){launch(a,list.get(0));return;}
  String[] labels=new String[list.size()];for(int i=0;i<labels.length;i++)labels[i]=list.get(i).loadLabel(a.getPackageManager())+" ("+list.get(i).activityInfo.packageName+")";
  new AlertDialog.Builder(a).setTitle(snow?"เลือก Snow AI":"เลือกเมนูรถ BYD").setItems(labels,(d,i)->launch(a,list.get(i))).setNegativeButton("ยกเลิก",null).show();
 }
 static void snow(Activity a){if(ready(a))select(a,true);}
 static void control(Activity a){
  if(!ready(a))return;
  new AlertDialog.Builder(a).setTitle("Vehicle Control")
   .setMessage("ใช้เมื่อจอดรถ\n\nล็อก / ปลดล็อก 4 ประตู\nกระจกไฟฟ้าทั้ง 4 บาน\nพับ / กางกระจกมองข้าง\nไฟอ่านหนังสือหน้า / หลัง\n\nคำสั่งโดยตรงยังไม่เปิดใช้งาน: ต้องยืนยันสิทธิ์ SDK, เกียร์ P และผลตอบกลับจากรถก่อน\nสามารถเปิดเมนู BYD หรือ Snow AI ที่ติดตั้งไว้ได้")
   .setPositiveButton("เปิดเมนู BYD",(d,w)->select(a,false))
   .setNeutralButton("Snow AI",(d,w)->select(a,true)).setNegativeButton("ปิด",null).show();
 }
}
