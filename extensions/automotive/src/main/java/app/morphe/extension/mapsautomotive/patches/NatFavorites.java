package app.morphe.extension.mapsautomotive.patches;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.InputFilter;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.Gravity;
import android.widget.*;

/** User-owned local favorites. Opens Maps route preview/search; never starts navigation or changes route automatically. */
final class NatFavorites {
 private static final String[] NAMES={"บ้าน","ที่ทำงาน","สุวรรณภูมิ","ดอนเมือง","พัทยา","หัวหิน","สถานที่โปรด 7","สถานที่โปรด 8"};
 private static final String[] DEST={"","","ท่าอากาศยานสุวรรณภูมิ สมุทรปราการ","ท่าอากาศยานดอนเมือง กรุงเทพมหานคร","พัทยา ชลบุรี","หัวหิน ประจวบคีรีขันธ์","",""};
 private final Activity a;
 private final SharedPreferences prefs;
 private final boolean dark;
 private AlertDialog dialog;
 private LinearLayout body;
 private NatFavorites(Activity a){this.a=a;prefs=a.getSharedPreferences("nat_favorites_v1",0);dark=ColorScheme.nightColors(a);}
 static void show(Activity a){
  if(a==null||a.isDestroyed()||a.isFinishing()||!a.hasWindowFocus()||!SafeMode.on(a,SafeMode.BYD_BATTERY))return;
  new NatFavorites(a).open();
 }
 private int dp(int n){return Math.round(n*a.getResources().getDisplayMetrics().density);}
 private int fg(){return dark?0xffedf4f7:0xff263238;}
 private String name(int i){return prefs.getString("name_"+i,NAMES[i]);}
 private String dest(int i){return prefs.getString("dest_"+i,DEST[i]);}
 private void open(){
  ScrollView scroll=new ScrollView(a);body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(10),dp(8),dp(10),dp(8));body.setBackgroundColor(dark?0xff101b29:0xfff4f8fa);scroll.addView(body);
  dialog=new AlertDialog.Builder(a).setTitle("★ Favorite Destinations").setView(scroll).setNegativeButton("ปิด",null).create();
  render();dialog.show();
  Window w=dialog.getWindow();
  if(w!=null){
   w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
   View decor=a.getWindow().getDecorView();
   int width=decor.getWidth(),height=decor.getHeight();
   if(width>0&&height>0){
    w.setLayout(Math.min(dp(420),Math.max(dp(180),(int)(width*.75)-dp(24))),Math.min(dp(510),Math.max(dp(180),height-dp(90))));
    WindowManager.LayoutParams lp=w.getAttributes();lp.gravity=Gravity.LEFT|Gravity.CENTER_VERTICAL;lp.x=dp(12);w.setAttributes(lp);
   }
  }
 }
 private TextView text(String s,int size){TextView t=new TextView(a);t.setText(s);t.setTextSize(size);t.setTextColor(fg());return t;}
 private Button button(String s){Button b=new Button(a);b.setAllCaps(false);b.setText(s);b.setMinHeight(dp(48));return b;}
 private void render(){
  body.removeAllViews();
  body.addView(text("เลือกสถานที่เพื่อเปิดเส้นทางใน Maps • ตั้งค่าขณะจอดรถ",13));
  for(int i=0;i<NatFavoriteRules.SLOTS;i++){
   final int slot=i;
   LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);
   Button go=button(name(i)+(dest(i).isEmpty()?" • ตั้งค่า":""));row.addView(go,new LinearLayout.LayoutParams(0,-2,1));
   go.setOnClickListener(v->{if(dest(slot).isEmpty())edit(slot);else preview(slot);});
   Button change=button("แก้ไข");change.setContentDescription("แก้ไข "+name(i));row.addView(change,new LinearLayout.LayoutParams(-2,-2));change.setOnClickListener(v->edit(slot));body.addView(row);
  }
 }
 private void edit(int i){
  LinearLayout fields=new LinearLayout(a);fields.setOrientation(LinearLayout.VERTICAL);fields.setPadding(dp(16),dp(4),dp(16),dp(4));
  EditText label=new EditText(a);label.setSingleLine(true);label.setHint("ชื่อปุ่ม");label.setFilters(new InputFilter[]{new InputFilter.LengthFilter(48)});label.setText(name(i));fields.addView(label);
  EditText target=new EditText(a);target.setSingleLine(true);target.setHint("ชื่อสถานที่ ที่อยู่ หรือพิกัด");target.setFilters(new InputFilter[]{new InputFilter.LengthFilter(256)});target.setText(dest(i));fields.addView(target);
  AlertDialog editor=new AlertDialog.Builder(a).setTitle("ตั้งค่าสถานที่โปรด").setView(fields).setNegativeButton("ยกเลิก",null)
   .setNeutralButton("ล้าง",(d,w)->{prefs.edit().putString("name_"+i,NAMES[i]).putString("dest_"+i,"").apply();render();})
   .setPositiveButton("บันทึก",null).create();
  editor.setOnShowListener(d->editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
   String n=NatFavoriteRules.name(label.getText().toString()),q=NatFavoriteRules.destination(target.getText().toString());
   if(n==null){label.setError("ระบุชื่อ");return;}if(q==null){target.setError("ระบุปลายทาง");return;}
   prefs.edit().putString("name_"+i,n).putString("dest_"+i,q).apply();editor.dismiss();render();
  }));editor.show();
 }
 private void preview(int i){
  final String n=name(i),q=dest(i);
  if(NatFavoriteRules.destination(q)==null)return;
  new AlertDialog.Builder(a).setTitle(n).setMessage("ปลายทาง: "+q+"\nเปิดหน้าตรวจสอบเส้นทางใน Google Maps? หากกำลังนำทาง อาจเปลี่ยนเส้นทางเดิม")
   .setNegativeButton("ยกเลิก",null).setPositiveButton("ดูเส้นทาง",(d,w)->{dialog.dismiss();openMaps(q);}).show();
 }
 private void openMaps(String q){
  if(a.isFinishing()||a.isDestroyed())return;
  String url=NatFavoriteRules.directions(q),geo=NatFavoriteRules.search(q);if(url==null||geo==null)return;
  // Only this installed Maps package. No browser, external app, force stop or auto-start navigation.
  Intent route=new Intent(Intent.ACTION_VIEW,Uri.parse(url)).setPackage(a.getPackageName());
  Intent search=new Intent(Intent.ACTION_VIEW,Uri.parse(geo)).setPackage(a.getPackageName());
  try{if(route.resolveActivity(a.getPackageManager())!=null){a.startActivity(route);return;}}catch(RuntimeException ignored){}
  try{if(search.resolveActivity(a.getPackageManager())!=null){a.startActivity(search);return;}}catch(RuntimeException ignored){}
  Toast.makeText(a,"Maps เวอร์ชันนี้ไม่รองรับการเปิดปลายทางจากปุ่มลัด",Toast.LENGTH_LONG).show();
 }
}
