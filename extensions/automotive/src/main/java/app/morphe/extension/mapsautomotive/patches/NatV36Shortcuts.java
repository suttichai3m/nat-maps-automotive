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
 private static ArrayList<ResolveInfo> overdrive(Activity a,boolean all){
  ArrayList<ResolveInfo> out=new ArrayList<>();PackageManager pm=a.getPackageManager();
  for(ResolveInfo r:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)){
   if(r.activityInfo==null||!r.activityInfo.exported||!r.activityInfo.enabled||r.activityInfo.packageName.equals(a.getPackageName()))continue;
   String label=String.valueOf(r.loadLabel(pm)).trim().toLowerCase(Locale.ROOT).replace(" ","");
   if(all||label.equals("overdrive")||label.equals("overdrive+")||label.equals("overdriveplus"))out.add(r);
  }return out;
 }
 private static String key(ResolveInfo r){return new ComponentName(r.activityInfo.packageName,r.activityInfo.name).flattenToString();}
 private static void chooseOverDrive(Activity a,ArrayList<ResolveInfo> list){
  if(list.isEmpty()){Toast.makeText(a,"ไม่พบแอปที่เปิดได้ • ตรวจสอบ OverDrive บนรถ",Toast.LENGTH_LONG).show();return;}
  String[] labels=new String[list.size()];for(int i=0;i<labels.length;i++)labels[i]=list.get(i).loadLabel(a.getPackageManager())+" ("+list.get(i).activityInfo.packageName+")";
  new AlertDialog.Builder(a).setTitle("เลือกแอป OverDrive • ตั้งค่าขณะจอด").setItems(labels,(d,i)->{ResolveInfo item=list.get(i);a.getSharedPreferences("nat_app_shortcuts",0).edit().putString("overdrive",key(item)).apply();launch(a,item);}).setNegativeButton("ยกเลิก",null).show();
 }
 static void favoriteApps(Activity a){
  if(!ready(a))return;
  android.content.SharedPreferences p=a.getSharedPreferences("nat_app_shortcuts",0);
  ArrayList<ResolveInfo> list=overdrive(a,true);
  String[] labels=new String[4];
  for(int i=0;i<labels.length;i++){String saved=p.getString("favorite_"+i,"");labels[i]="Slot "+(i+1)+" • ตั้งค่า";for(ResolveInfo item:list)if(key(item).equals(saved)){labels[i]="Slot "+(i+1)+" • "+item.loadLabel(a.getPackageManager());break;}}
  new AlertDialog.Builder(a).setTitle("Favorite Apps • 4 slots").setItems(labels,(d,slot)->{
   ResolveInfo found=null;for(ResolveInfo item:list)if(key(item).equals(p.getString("favorite_"+slot,"")))found=item;
   final ResolveInfo selected=found;
   new AlertDialog.Builder(a).setTitle(labels[slot]).setItems(new String[]{"เปิดแอป","เลือกแอปใหม่","ล้างปุ่ม"},(dd,choice)->{
    if(choice==0){if(selected!=null)launch(a,selected);else Toast.makeText(a,"ยังไม่ได้เลือกแอป",Toast.LENGTH_SHORT).show();}
    if(choice==2)p.edit().remove("favorite_"+slot).apply();
    if(choice==1){String[] names=new String[list.size()];for(int i=0;i<names.length;i++)names[i]=list.get(i).loadLabel(a.getPackageManager())+" ("+list.get(i).activityInfo.packageName+")";
     new AlertDialog.Builder(a).setTitle("เลือกแอป • ขณะจอด").setItems(names,(ddd,i)->p.edit().putString("favorite_"+slot,key(list.get(i))).apply()).setNegativeButton("ยกเลิก",null).show();}
   }).setNegativeButton("ปิด",null).show();
  }).setNegativeButton("ปิด",null).show();
 }
 static void configureOverDrive(Activity a){if(ready(a))chooseOverDrive(a,overdrive(a,true));}
 static void control(Activity a){
  if(!ready(a))return;
  String saved=a.getSharedPreferences("nat_app_shortcuts",0).getString("overdrive","");
  for(ResolveInfo item:overdrive(a,true))if(key(item).equals(saved)){launch(a,item);return;}
  ArrayList<ResolveInfo> matches=overdrive(a,false);
  if(matches.size()==1){launch(a,matches.get(0));return;}
  if(!matches.isEmpty()){chooseOverDrive(a,matches);return;}
  new AlertDialog.Builder(a).setTitle("OverDrive").setMessage("ไม่พบชื่อแอปโดยตรง • เลือกแอปที่ติดตั้งอยู่หนึ่งครั้ง").setPositiveButton("เลือกแอป",(d,w)->chooseOverDrive(a,overdrive(a,true))).setNegativeButton("ปิด",null).show();
 }

}
