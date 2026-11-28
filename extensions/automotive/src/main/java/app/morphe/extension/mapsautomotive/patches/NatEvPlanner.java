package app.morphe.extension.mapsautomotive.patches;
import android.app.*;
import android.graphics.*;
import android.view.View;
import android.widget.*;
import android.text.InputType;
import android.content.Context;
import java.util.Locale;
/** Offline what-if planner. Values never written to car, Maps forecast or vehicle model. */
final class NatEvPlanner {
 private static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
 private static TextView label(LinearLayout l,String s){TextView t=new TextView(l.getContext());t.setText(s);l.addView(t);return t;}
 private static final class Slider{
  final SeekBar bar;final TextView title;final String name;final double scale;final int low;
  Slider(LinearLayout l,String name,int min,int max,int value,double scale){this.name=name;this.scale=scale;low=min;title=label(l,"");bar=new SeekBar(l.getContext());bar.setMax(max-min);bar.setProgress(Math.max(0,Math.min(max-min,value-min)));l.addView(bar);update();}
  double value(){return (bar.getProgress()+low)*scale;}
  void set(double x){bar.setProgress((int)Math.round(x/scale)-low);update();}
  void update(){title.setText(name+": "+String.format(Locale.US,scale==1?"%.0f":"%.1f",value()));}
  void watch(Runnable r){bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}public void onProgressChanged(SeekBar b,int v,boolean u){update();r.run();}});}
 }
 private static final class Battery extends View{
  final Paint p=new Paint(3);final boolean dark;final String name;double value=Double.NaN,reserve=15;
  Battery(Context c,String n,boolean dark){super(c);name=n;this.dark=dark;}
  void show(double v,double r){value=v;reserve=r;setContentDescription(name+" "+String.format(Locale.US,"%.0f%%",v));invalidate();}
  protected void onDraw(Canvas c){float w=getWidth(),h=getHeight();if(w<20||h<20)return;float m=w*.06f;RectF outer=new RectF(m,h*.10f,w*.87f,h*.73f);int fg=dark?0xffedf4f7:0xff16212b;int accent=value<reserve?0xffd35c55:0xff269c78;
   p.setColor(fg);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(2,w*.016f));c.drawRoundRect(outer,w*.06f,w*.06f,p);p.setStyle(Paint.Style.FILL);c.drawRoundRect(w*.90f,h*.31f,w*.96f,h*.52f,w*.02f,w*.02f,p);
   if(Double.isFinite(value)){float ratio=(float)Math.max(0,Math.min(100,value))/100;float inset=w*.025f;RectF fill=new RectF(outer.left+inset,outer.top+inset,outer.left+inset+(outer.width()-2*inset)*ratio,outer.bottom-inset);p.setColor(accent);if(ratio>0)c.drawRoundRect(fill,w*.025f,w*.025f,p);}
   p.setColor(fg);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(Math.min(w*.22f,h*.31f));c.drawText(Double.isFinite(value)?String.format(Locale.US,"%.0f%%",value):"—",outer.centerX(),h*.47f,p);p.setTypeface(Typeface.DEFAULT);p.setTextSize(Math.min(w*.075f,h*.12f));c.drawText(name,w*.5f,h*.91f,p);
  }
 }
 static void show(Activity a){
  if(a==null||a.isDestroyed()||a.isFinishing()||!a.hasWindowFocus())return;
  final NatPrediction.Snapshot f=NatPrediction.freshSnapshot();final boolean matched=f!=null&&NatNavigationCard.matchesForecast(a.getWindow().peekDecorView(),f);final NatV36Daily.Energy live=NatV36Daily.energy();
  LinearLayout l=new LinearLayout(a);l.setOrientation(1);l.setPadding(dp(a,14),dp(a,8),dp(a,14),dp(a,8));
  label(l,"SIMULATION • ไม่เปลี่ยนข้อมูลรถหรือ Google Maps • วางแผนขณะจอด");
  RadioGroup caps=new RadioGroup(a);caps.setOrientation(0);RadioButton large=new RadioButton(a),small=new RadioButton(a),custom=new RadioButton(a);large.setId(View.generateViewId());small.setId(View.generateViewId());custom.setId(View.generateViewId());large.setText("82.56 kWh");small.setText("61.44 kWh");custom.setText("กำหนดเอง");caps.addView(large);caps.addView(small);caps.addView(custom);l.addView(caps);caps.check(large.getId());
  EditText capacity=new EditText(a);capacity.setSingleLine(true);capacity.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);capacity.setText("82.56");capacity.setHint("ความจุใช้งานจริง kWh");l.addView(capacity);capacity.setEnabled(false);
  Slider soc=new Slider(l,"แบตเริ่มต้น %",0,100,80,1),out=new Slider(l,"ระยะขาไป km",0,1000,150,1),rate=new Slider(l,"ขาไป kWh/100km",10,100,32,.5),back=new Slider(l,"ระยะขากลับ km",0,1000,150,1),returnRate=new Slider(l,"ขากลับ kWh/100km",10,100,32,.5),reserve=new Slider(l,"แบตสำรอง %",0,100,15,1);
  TextView source=label(l,"ตัวอย่าง 80% • ขาไป/กลับ 150 km • 16 kWh/100km");
  Button actual=new Button(a);actual.setText("ใช้ข้อมูลรถ / เส้นทาง Maps ที่ยืนยันได้");l.addView(actual);
  Spinner bands=new Spinner(a);bands.setAdapter(new ArrayAdapter<String>(a,android.R.layout.simple_spinner_dropdown_item,new String[]{"เมือง <40 km/h","ผสม 40–79 km/h","ทางไกล ≥80 km/h"}));l.addView(bands);TextView learned=label(l,NatEvLearning.info(a,0));Button apply=new Button(a);apply.setText("ใช้ค่าอัตราสิ้นเปลืองจากประวัติ");l.addView(apply);
  LinearLayout row=new LinearLayout(a);l.addView(row);boolean dark=ColorScheme.nightColors(a);Battery b1=new Battery(a,"แบตปลายทาง",dark),b2=new Battery(a,"แบตเมื่อกลับ",dark);row.addView(b1,new LinearLayout.LayoutParams(0,dp(a,130),1));row.addView(b2,new LinearLayout.LayoutParams(0,dp(a,130),1));TextView result=label(l,"");result.setTextSize(17);
  Runnable calc=()->{try{NatEvPlanRules.Result x=NatEvPlanRules.plan(soc.value(),Double.parseDouble(capacity.getText().toString().trim()),out.value(),rate.value(),back.value(),returnRate.value(),reserve.value());b1.show(x.outbound,reserve.value());b2.show(x.back,reserve.value());result.setText(String.format(Locale.US,"ขาไป %.1f kWh\nไป–กลับ %.1f kWh\nพลังงานเพิ่มขั้นต่ำ %.1f kWh\n%s",x.oneKwh,x.totalKwh,x.needed,x.outbound<0?"พลังงานไม่พอถึงปลายทาง":x.back<0?"พลังงานไม่พอขากลับ":Math.min(x.outbound,x.back)<reserve.value()?"ต่ำกว่าแบตสำรอง • วางแผนชาร์จ":"เพียงพอตามสมมติฐาน"));}catch(RuntimeException ex){b1.show(Double.NaN,reserve.value());b2.show(Double.NaN,reserve.value());result.setText("ตรวจสอบความจุและค่าที่กรอก");}};
  for(Slider s:new Slider[]{soc,out,rate,back,returnRate,reserve})s.watch(calc);
  caps.setOnCheckedChangeListener((g,id)->{capacity.setEnabled(id==custom.getId());if(id==large.getId())capacity.setText("82.56");if(id==small.getId())capacity.setText("61.44");calc.run();});
  capacity.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){calc.run();}public void afterTextChanged(android.text.Editable e){}});
  actual.setOnClickListener(v->{boolean got=false;if(live!=null&&NatEvPlanRules.bounded(live.soc,0,100)){soc.set(live.soc);got=true;}if(matched&&NatEvPlanRules.bounded(f.capacity,5,150)&&NatEvPlanRules.bounded(f.km,0,1000)){caps.check(custom.getId());capacity.setText(String.format(Locale.US,"%.2f",f.capacity));out.set(f.km);source.setText("Maps ขาไป ณ ตอนเปิด: "+f.google+"% • "+f.charges+" จุดชาร์จ • ขากลับกรอกระยะจาก Maps เอง");got=true;}else source.setText(got?"ใช้ SOC รถ ณ ตอนเปิด • ระยะและความจุยังเป็นค่าที่เลือก":"ไม่มีข้อมูลสดที่ยืนยันได้");calc.run();});
  bands.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int i,long id){learned.setText(NatEvLearning.info(a,i));}});
  apply.setOnClickListener(v->{double r=NatEvLearning.rate(a,bands.getSelectedItemPosition());if(Double.isFinite(r)){rate.set(r);returnRate.set(r);source.setText("ใช้ค่าเฉลี่ยจากช่วงความเร็วที่บันทึก • ไม่ใช่การพยากรณ์จราจร");calc.run();}else Toast.makeText(a,"ต้องมีข้อมูลอย่างน้อย 20 km และ 100 คู่ตัวอย่าง",Toast.LENGTH_SHORT).show();});
  label(l,"ขากลับใช้ระยะและอัตราสิ้นเปลืองที่กรอกแยก • ไม่เรียกเส้นทางย้อนกลับอัตโนมัติ • ไม่รวมการชาร์จระหว่างทางหรือการสูญเสีย • ไม่รับประกันระยะทาง");
  calc.run();ScrollView sc=new ScrollView(a);sc.addView(l);new AlertDialog.Builder(a).setTitle("Smart EV Prediction Simulator").setView(sc).setPositiveButton("ปิด",null).show();
 }
}
