package app.morphe.extension.mapsautomotive.patches;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.os.StatFs;
import android.view.View;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** On-demand, read-only diagnostics. No location coordinates stored; no network requests. */
final class NatSystemDiagnostics {
    private static final int LIMIT=30, MAX_SAT=256;
    private static final long INTERVAL=2000, PSS_INTERVAL=10000, GNSS_AGE=10000;
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private static java.lang.ref.WeakReference<Session> current=new java.lang.ref.WeakReference<>(null);
    static void show(Activity a){
        if(a==null||a.isFinishing()||a.isDestroyed())return;
        Session prior=current.get();
        if(prior!=null&&!prior.closed){
            if(!prior.host.isFinishing()&&!prior.host.isDestroyed()&&prior.dialog!=null&&prior.dialog.isShowing())return;
            prior.closed=true;prior.stop();
            if(prior.dialog!=null)try{prior.dialog.dismiss();}catch(RuntimeException ignored){}
            current.clear();
        }
        Session session=new Session(a);current=new java.lang.ref.WeakReference<>(session);
        try{session.show();}catch(RuntimeException e){
            session.closed=true;session.stop();
            if(session.dialog!=null)try{session.dialog.dismiss();}catch(RuntimeException ignored){}
            if(current.get()==session)current.clear();
            ReportLog.w("NatDiagnostics","Diagnostics unavailable",e);Toast.makeText(a,"Diagnostics unavailable",Toast.LENGTH_SHORT).show();
        }
    }
    private static String fmt(double n,String unit){return Double.isFinite(n)?String.format(Locale.US,"%.1f %s",n,unit):"—";}
    private static Object number(double n){return Double.isFinite(n)?n:JSONObject.NULL;}
    private static Object count(int n){return n>=0?n:JSONObject.NULL;}
    private static String age(long n){return n<0?"—":(n/1000)+" s";}
    private static final class Sat {
        final int id,type;final float az,el,cn;final boolean used;
        Sat(GnssStatus s,int i){id=s.getSvid(i);type=s.getConstellationType(i);az=s.getAzimuthDegrees(i);el=s.getElevationDegrees(i);cn=s.getCn0DbHz(i);used=s.usedInFix(i);}
        JSONObject json() throws Exception{return new JSONObject().put("svid",id).put("constellation",type).put("azimuth_deg",number(az)).put("elevation_deg",number(el)).put("cn0_dbhz",number(cn>=0?cn:Double.NaN)).put("used_in_fix",used);}
    }
    private static final class Sample {
        long wall,at,pssAt=-1;double cpu=Double.NaN,pss=Double.NaN,total=Double.NaN,available=Double.NaN,storage=Double.NaN;boolean low;String network="unknown";
        JSONObject json() throws Exception{return new JSONObject().put("wall_ms",wall).put("elapsed_ms",at).put("cpu_pct_all_cores",number(cpu)).put("pss_mib",number(pss)).put("pss_age_ms",number(NatDiagnosticMath.age(at,pssAt)<0?Double.NaN:NatDiagnosticMath.age(at,pssAt))).put("system_total_mib",number(total)).put("system_available_mib",number(available)).put("system_low_memory",Double.isFinite(total)?low:JSONObject.NULL).put("data_available_mib",number(storage)).put("network",network);}
    }
    private static final class Session {
        final Activity host;final Context app;final String uiAtOpen;final ArrayList<Sample> history=new ArrayList<>();
        final ArrayList<Sat> sats=new ArrayList<>();
        AlertDialog dialog;TextView resources,gps,byd,notes,historyText,cpuMetric,pssMetric,ramMetric,gpsMetric,satMetric,accuracyMetric,monitorStatus;Plot sky,graph;Button pause;ProgressBar memoryBar;
        boolean dark;int fg,muted,surface,cardColor,accent;
        LocationManager lm;boolean running,requested,registered,busy,closed;int generation;
        boolean permission;Boolean enabled;long fixAt=-1,gnssAt=-1,previousAt=-1,previousCpu=-1,pssAt=-1;double accuracy=Double.NaN,pss=Double.NaN;boolean hasAccuracy,fixCached;
        int satCount=-1,usedCount=-1;Sample last;String gpsStatus="UNKNOWN";JSONObject bydSnapshot;
        final LocationListener location=new LocationListener(){
            @Override public void onLocationChanged(Location l){if(running){acceptFix(l,false);renderGps();}}
            @Override public void onStatusChanged(String p,int s,Bundle b){}
            @Override public void onProviderEnabled(String p){if(running)renderGps();}
            @Override public void onProviderDisabled(String p){if(running)renderGps();}
        };
        final GnssStatus.Callback gnss=new GnssStatus.Callback(){
            @Override public void onSatelliteStatusChanged(GnssStatus status){
                if(!running)return;sats.clear();satCount=status.getSatelliteCount();usedCount=0;
                for(int i=0;i<satCount;i++){if(status.usedInFix(i))usedCount++;if(i<MAX_SAT)sats.add(new Sat(status,i));}
                gnssAt=SystemClock.elapsedRealtime();renderGps();
            }
            @Override public void onStopped(){if(running){gnssAt=-1;sats.clear();satCount=usedCount=-1;renderGps();}}
        };
        final Runnable tick=new Runnable(){public void run(){
            if(!running||closed||host.isFinishing()||host.isDestroyed()||dialog==null||!dialog.isShowing()){stop();return;}
            // Pause on background / lost focus, including external windows. Resume is explicit.
            if(dialog.getWindow()==null||!dialog.getWindow().getDecorView().hasWindowFocus()){stop();return;}
            sample();renderGps();MAIN.postDelayed(this,INTERVAL);
        }};
        Session(Activity a){host=a;app=a.getApplicationContext();uiAtOpen=NatMinimalEv.uiStatus(a);}
        TextView text(LinearLayout box,String s,int sp){TextView t=new TextView(host);t.setText(s);t.setTextSize(sp);t.setTextColor(fg);t.setPadding(0,dp(4),0,dp(4));box.addView(t);return t;}
        int dp(int v){return Math.round(v*host.getResources().getDisplayMetrics().density);}
        Button button(LinearLayout box,String name,Runnable action){Button b=new Button(host);b.setText(name);b.setTextColor(fg);b.setAllCaps(false);b.setMinHeight(dp(48));box.addView(b);b.setOnClickListener(v->{try{action.run();}catch(RuntimeException e){stop();ReportLog.w("NatDiagnostics","Control unavailable",e);}});return b;}
        android.graphics.drawable.GradientDrawable background(int color,int radius){
            android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;
        }
        LinearLayout card(LinearLayout parent){
            LinearLayout l=new LinearLayout(host);l.setOrientation(1);l.setPadding(dp(14),dp(12),dp(14),dp(12));l.setBackground(background(cardColor,14));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(10);parent.addView(l,lp);return l;
        }
        LinearLayout row(LinearLayout parent){
            LinearLayout l=new LinearLayout(host);l.setOrientation(0);l.setGravity(android.view.Gravity.CENTER_VERTICAL);parent.addView(l,new LinearLayout.LayoutParams(-1,-2));return l;
        }
        TextView metric(LinearLayout row,String label){
            LinearLayout cell=new LinearLayout(host);cell.setOrientation(1);cell.setPadding(dp(5),dp(4),dp(5),dp(8));row.addView(cell,new LinearLayout.LayoutParams(0,-2,1));
            TextView cap=text(cell,label,12);cap.setTextColor(muted);
            TextView v=text(cell,"—",27);v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);return v;
        }
        void heading(LinearLayout box,String label){TextView t=text(box,label,18);t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);}
        void show(){
            dark=ColorScheme.nightColors(host);fg=dark?0xffedf4f7:0xff263238;muted=dark?0xffbacbd3:0xff546a74;
            surface=dark?0xff14232b:0xffedf2ef;cardColor=dark?0xff20303a:0xffffffff;accent=dark?0xff74dba8:0xff137548;
            LinearLayout body=new LinearLayout(host);body.setOrientation(1);body.setPadding(dp(16),dp(12),dp(16),dp(12));body.setBackgroundColor(surface);
            LinearLayout header=card(body);heading(header,"NAT SYSTEM DIAGNOSTICS");monitorStatus=text(header,"Waiting for sample",14);monitorStatus.setTextColor(accent);
            text(header,"V3.0 • Maps process including NAT • Use while parked",12).setTextColor(muted);
            LinearLayout resourcesCard=card(body);heading(resourcesCard,"01  Performance");
            LinearLayout metrics=row(resourcesCard);cpuMetric=metric(metrics,"APP CPU");pssMetric=metric(metrics,"APP RAM / PSS");
            ramMetric=metric(row(resourcesCard),"SYSTEM RAM USED");
            memoryBar=new ProgressBar(host,null,android.R.attr.progressBarStyleHorizontal);memoryBar.setMax(100);memoryBar.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));memoryBar.setVisibility(View.GONE);resourcesCard.addView(memoryBar,new LinearLayout.LayoutParams(-1,dp(10)));
            resources=text(resourcesCard,"Waiting for sample",13);resources.setTextColor(muted);
            LinearLayout historyCard=card(body);heading(historyCard,"02  Performance history • 60 s");
            graph=new Plot(host,false,this);historyCard.addView(graph,new LinearLayout.LayoutParams(-1,dp(150)));historyText=text(historyCard,"—",13);
            LinearLayout gpsCard=card(body);heading(gpsCard,"03  GPS / GNSS");
            gpsMetric=metric(row(gpsCard),"POSITION STATUS");gpsMetric.setTextSize(21);
            LinearLayout gpsMetrics=row(gpsCard);satMetric=metric(gpsMetrics,"SATELLITES SEEN / USED");accuracyMetric=metric(gpsMetrics,"LAST FIX ACCURACY");
            gps=text(gpsCard,"Waiting for GPS",13);gps.setTextColor(muted);sky=new Plot(host,true,this);gpsCard.addView(sky,new LinearLayout.LayoutParams(-1,dp(230)));
            text(gpsCard,"Green = used in fix • Gray = not used • C/N₀ dB-Hz",12).setTextColor(muted);
            LinearLayout carCard=card(body);heading(carCard,"04  BYD SDK / Maps forecast");byd=text(carCard,"Waiting for sample",15);
            LinearLayout ui=card(body);heading(ui,"05  UI recovery • last map scan");text(ui,uiAtOpen.replace(" | ","\n"),14);
            LinearLayout actions=card(body);heading(actions,"06  Diagnostic report");
            notes=text(actions,"Use while parked • read-only • no coordinates saved • no upload • paused when closed",12);
            pause=button(actions,"Pause",()->{if(running)stop();else start();});
            button(actions,"Copy Diagnostic JSON",()->{try{android.content.ClipboardManager c=host.getSystemService(android.content.ClipboardManager.class);if(c==null)throw new IllegalStateException();c.setPrimaryClip(android.content.ClipData.newPlainText("NAT diagnostic",report().toString(2)));Toast.makeText(host,"Copied",Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(host,"Copy failed",Toast.LENGTH_SHORT).show();}});
            button(actions,"Export Diagnostic TXT + JSON",this::export);
            ScrollView scroll=new ScrollView(host);scroll.setFillViewport(true);scroll.addView(body);
            dialog=new AlertDialog.Builder(host,dark?android.R.style.Theme_DeviceDefault_Dialog_Alert:android.R.style.Theme_DeviceDefault_Light_Dialog_Alert).setTitle("NAT V3.0 • System Diagnostics").setView(scroll).setPositiveButton("Close",null).create();
            dialog.setOnDismissListener(d->{closed=true;stop();if(current.get()==this)current.clear();});
            dialog.show();if(dialog.getWindow()!=null){int width=host.getWindow().getDecorView().getWidth();if(width>0)dialog.getWindow().setLayout(Math.min(width,dp(760)),android.view.ViewGroup.LayoutParams.WRAP_CONTENT);}
            start();
        }
        void start(){
            if(closed||running)return;running=true;generation++;previousAt=previousCpu=-1;pssAt=-1;pss=Double.NaN;last=null;history.clear();fixAt=gnssAt=-1;accuracy=Double.NaN;hasAccuracy=false;fixCached=false;sats.clear();satCount=usedCount=-1;pause.setText("Pause");bydSnapshot=null;resources.setText("Waiting for sample");byd.setText("Waiting for sample");historyText.setText("—");graph.invalidate();
            try{permission=host.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;lm=app.getSystemService(LocationManager.class);enabled=lm==null?null:lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
                if(permission&&Boolean.TRUE.equals(enabled)&&lm!=null){
                    try{lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,2000,0,location,Looper.getMainLooper());requested=true;}catch(RuntimeException e){requested=false;}
                    try{registered=lm.registerGnssStatusCallback(gnss,MAIN);}catch(RuntimeException e){registered=false;}
                    try{acceptFix(lm.getLastKnownLocation(LocationManager.GPS_PROVIDER),true);}catch(RuntimeException ignored){}
                }
            }catch(RuntimeException e){enabled=null;}
            for(TextView m:new TextView[]{cpuMetric,pssMetric,ramMetric,gpsMetric,satMetric,accuracyMetric})m.setText("—");memoryBar.setVisibility(View.GONE);monitorStatus.setText("● MONITORING");monitorStatus.setTextColor(accent);notes.setText("Monitoring • GPS diagnostic requests only while this page is open");renderGps();MAIN.postDelayed(tick,500);
        }
        void stop(){
            running=false;generation++;MAIN.removeCallbacks(tick);
            if(lm!=null){if(requested)try{lm.removeUpdates(location);}catch(RuntimeException ignored){}if(registered)try{lm.unregisterGnssStatusCallback(gnss);}catch(RuntimeException ignored){}}
            requested=registered=false;
            if(pause!=null)pause.setText("Resume");if(notes!=null)notes.setText("Paused • displayed values are last samples, not live");if(sky!=null)sky.invalidate();if(monitorStatus!=null){monitorStatus.setText("PAUSED • last samples");monitorStatus.setTextColor(muted);}if(gpsMetric!=null)gpsMetric.setText("PAUSED");
        }
        void acceptFix(Location l,boolean cached){
            if(l==null||!LocationManager.GPS_PROVIDER.equals(l.getProvider()))return;
            long t=l.getElapsedRealtimeNanos()/1000000;long now=SystemClock.elapsedRealtime();if(t<=0||t>now||t<fixAt)return;
            fixAt=t;hasAccuracy=l.hasAccuracy();accuracy=hasAccuracy?l.getAccuracy():Double.NaN;fixCached=cached;
        }
        void sample(){
            if(busy)return;busy=true;final int g=generation;final long prevAt=previousAt,prevCpu=previousCpu,oldPssAt=pssAt;final double oldPss=pss;
            IO.execute(()->{
                Sample s=new Sample();s.wall=System.currentTimeMillis();s.at=SystemClock.elapsedRealtime();long cpuNow=-1;
                try{cpuNow=Process.getElapsedCpuTime();s.cpu=NatDiagnosticMath.cpu(cpuNow-prevCpu,s.at-prevAt,Runtime.getRuntime().availableProcessors());if(prevAt<0||prevCpu<0)s.cpu=Double.NaN;}catch(RuntimeException ignored){}
                s.pss=oldPss;s.pssAt=oldPssAt;
                if(!NatDiagnosticMath.fresh(s.at,oldPssAt,PSS_INTERVAL-1)){
                    s.pss=Double.NaN;s.pssAt=-1;try{Debug.MemoryInfo m=new Debug.MemoryInfo();Debug.getMemoryInfo(m);int kb=m.getTotalPss();if(kb>0){s.pss=kb/1024.0;s.pssAt=s.at;}}catch(RuntimeException ignored){}
                }
                try{ActivityManager am=app.getSystemService(ActivityManager.class);ActivityManager.MemoryInfo m=new ActivityManager.MemoryInfo();if(am!=null){am.getMemoryInfo(m);if(Double.isFinite(NatDiagnosticMath.usedMemory(m.totalMem,m.availMem))){s.total=m.totalMem/1048576.0;s.available=m.availMem/1048576.0;s.low=m.lowMemory;}}}catch(RuntimeException ignored){}
                try{s.storage=new StatFs(app.getFilesDir().getAbsolutePath()).getAvailableBytes()/1048576.0;}catch(RuntimeException ignored){}
                try{ReportDiagnostics.NetworkState n=ReportDiagnostics.readNetwork(app);if(n!=null)s.network=n.type+(n.validated?" / validated":" / not validated");}catch(RuntimeException ignored){}
                JSONObject bs=null;try{bs=BydBatteryPatch.diagnosticSnapshot();}catch(Exception ignored){}
                final long cpuFinal=cpuNow;final JSONObject state=bs;
                MAIN.post(()->{busy=false;if(!running||closed||g!=generation)return;previousAt=s.at;previousCpu=cpuFinal;pss=s.pss;pssAt=s.pssAt;last=s;bydSnapshot=state;history.add(s);while(history.size()>LIMIT||(!history.isEmpty()&&s.at-history.get(0).at>60000))history.remove(0);renderResources();});
            });
        }
        void renderResources(){
            if(last==null)return;Sample s=last;cpuMetric.setText(fmt(s.cpu,"%"));pssMetric.setText(fmt(s.pss,"MiB"));ramMetric.setText(Double.isFinite(s.total)?fmt((s.total-s.available)/1024,"GiB")+" / "+fmt(s.total/1024,"GiB"):"—");double percent=NatDiagnosticMath.memoryPercent(s.total,s.available);memoryBar.setVisibility(Double.isFinite(percent)?View.VISIBLE:View.GONE);if(Double.isFinite(percent))memoryBar.setProgress((int)Math.round(percent));
            resources.setText("App CPU: "+fmt(s.cpu,"%")+" (all logical cores normalized)\nApp RAM PSS: "+fmt(s.pss,"MiB")+" • sample age "+age(NatDiagnosticMath.age(SystemClock.elapsedRealtime(),s.pssAt))+"\nSystem RAM: "+fmt(s.total-s.available,"MiB")+" used / "+fmt(s.total,"MiB")+" total\nAvailable: "+fmt(s.available,"MiB")+" • memory pressure: "+(!Double.isFinite(s.total)?"—":s.low?"LOW MEMORY":"normal")+"\nData storage free: "+fmt(s.storage,"MiB")+"\nNetwork: "+s.network);
            double sum=0,max=0;int n=0;for(Sample x:history)if(Double.isFinite(x.cpu)){sum+=x.cpu;max=Math.max(max,x.cpu);n++;}
            historyText.setText("CPU mean / peak: "+fmt(n==0?Double.NaN:sum/n,"%")+" / "+fmt(n==0?Double.NaN:max,"%")+" • "+history.size()+" samples");
            if(bydSnapshot==null)byd.setText("BYD status unavailable");else byd.setText("Source: "+bydSnapshot.optString("source","unknown")+"\nBattery feed: "+bydSnapshot.optString("status","unknown")+"\nLast sample: "+bydSnapshot.optString("sample_age_ms","—")+" ms\nForecast: "+bydSnapshot.optString("forecast","unknown"));
            graph.invalidate();
        }
        void renderGps(){
            if(!running)return;long now=SystemClock.elapsedRealtime();
            try{permission=host.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;enabled=lm==null?null:lm.isProviderEnabled(LocationManager.GPS_PROVIDER);}catch(RuntimeException e){enabled=null;}
            boolean f=NatDiagnosticMath.fresh(now,gnssAt,GNSS_AGE)&&permission&&Boolean.TRUE.equals(enabled);long a=NatDiagnosticMath.age(now,fixAt);
            gpsStatus=NatDiagnosticMath.gps(permission,enabled,requested,a,hasAccuracy,accuracy,f,satCount,usedCount);gpsMetric.setText(gpsStatus);gpsMetric.setTextColor("FIX".equals(gpsStatus)?accent:fg);satMetric.setText(f?satCount+" / "+usedCount:"—");accuracyMetric.setText(fmt(accuracy,"m"));
            gps.setText(gpsStatus+"\nPermission: "+(permission?"fine location granted":"not granted")+" • GPS provider: "+enabled+"\nGNSS callback: "+(registered?"registered":"unavailable")+"\nSatellites seen / used: "+(f?satCount+" / "+usedCount:"— / —")+"\nLast GPS fix: "+age(a)+(fixCached?" (cached)":"")+"\nAccuracy of last fix: "+fmt(accuracy,"m")+"\nGNSS data age: "+age(NatDiagnosticMath.age(now,gnssAt)));
            sky.invalidate();
        }
        JSONObject report() throws Exception{
            long now=SystemClock.elapsedRealtime();JSONArray hist=new JSONArray(),sat=new JSONArray();for(Sample s:history)hist.put(s.json());
            boolean f=running&&permission&&Boolean.TRUE.equals(enabled)&&NatDiagnosticMath.fresh(now,gnssAt,GNSS_AGE);
            if(f)for(Sat s:sats)sat.put(s.json());
            return new JSONObject().put("schema","nat-diagnostics-2.0.3").put("app_version","3.0").put("captured_wall_ms",System.currentTimeMillis()).put("monitoring",running).put("process_scope","Maps process including NAT; no other processes").put("cpu_normalization","all logical cores").put("history",hist).put("ui_at_open",uiAtOpen).put("byd",bydSnapshot==null?JSONObject.NULL:bydSnapshot).put("gps",new JSONObject().put("status",running?gpsStatus:"PAUSED").put("last_observed_status",gpsStatus).put("permission",permission).put("provider_enabled",enabled==null?JSONObject.NULL:enabled).put("request_active",requested).put("callback_registered",registered).put("fix_age_ms",number(fixAt<0?Double.NaN:NatDiagnosticMath.age(now,fixAt))).put("fix_cached",fixCached).put("accuracy_m",number(accuracy)).put("gnss_age_ms",number(gnssAt<0?Double.NaN:NatDiagnosticMath.age(now,gnssAt))).put("seen",count(f?satCount:-1)).put("used",count(f?usedCount:-1)).put("satellites",sat));
        }
        void export(){
            final String json,txt;try{json=report().toString(2);txt="NAT V3.0 System Diagnostics\n"+resources.getText()+"\n"+gps.getText()+"\n"+byd.getText()+"\n"+notes.getText()+"\n\n"+json;}catch(Exception e){Toast.makeText(host,"Export unavailable",Toast.LENGTH_SHORT).show();return;}
            IO.execute(()->{String result;try{
                File dir=app.getExternalFilesDir("nat_diagnostics");if(dir==null)dir=new File(app.getFilesDir(),"nat_diagnostics");if(!dir.isDirectory()&&!dir.mkdirs())throw new java.io.IOException();
                String base="NAT-diagnostic-"+System.currentTimeMillis();write(new File(dir,base+".json"),json);write(new File(dir,base+".txt"),txt);result="Exported TXT + JSON: "+dir.getAbsolutePath()+"/"+base;
            }catch(Exception e){result="Export failed";}final String message=result;MAIN.post(()->{if(!closed){notes.setText(message);Toast.makeText(host,message,Toast.LENGTH_LONG).show();}});});
        }
    }
    private static void write(File f,String s)throws Exception{try(FileOutputStream out=new FileOutputStream(f)){out.write(s.getBytes(StandardCharsets.UTF_8));}}
    private static final class Plot extends View {
        final boolean sky;final Session s;final Paint p=new Paint(3);
        Plot(Context c,boolean sky,Session s){super(c);this.sky=sky;this.s=s;setContentDescription(sky?"GNSS sky view and signal strength":"CPU and PSS history");}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);float w=getWidth(),h=getHeight();if(w<20||h<20)return;c.drawColor(0xff20303a);p.setColor(0xffbacbd3);p.setTextSize(12*getResources().getDisplayMetrics().scaledDensity);
            if(sky){
                float cx=w*.25f,cy=h*.50f,r=Math.max(1,Math.min(w*.21f,h*.38f));p.setStyle(Paint.Style.STROKE);
                for(int i=1;i<=3;i++)c.drawCircle(cx,cy,r*i/3,p);c.drawLine(cx-r,cy,cx+r,cy,p);c.drawLine(cx,cy-r,cx,cy+r,p);p.setStyle(Paint.Style.FILL);c.drawText("N",cx-5,cy-r-5,p);c.drawText("S",cx-5,cy+r+15,p);c.drawText("E",cx+r+5,cy,p);c.drawText("W",cx-r-15,cy,p);
                if(!s.running||!s.permission||!Boolean.TRUE.equals(s.enabled)||!NatDiagnosticMath.fresh(SystemClock.elapsedRealtime(),s.gnssAt,GNSS_AGE)){c.drawText("No recent GNSS data",w*.51f,h*.5f,p);return;}
                int n=s.sats.size();float left=w*.52f,bottom=h*.83f,bw=(w-left-8)/Math.max(1,n);
                for(int i=0;i<n;i++){Sat a=s.sats.get(i);p.setColor(a.used?0xff20ae7b:0xff83919b);if(NatDiagnosticMath.angle(a.az,a.el)){double rad=Math.toRadians(a.az);float rr=(float)(r*NatDiagnosticMath.skyRadius(a.el));c.drawCircle(cx+(float)Math.sin(rad)*rr,cy-(float)Math.cos(rad)*rr,4*getResources().getDisplayMetrics().density,p);}
                    if(Float.isFinite(a.cn)&&a.cn>=0){float y=bottom-Math.min(60,a.cn)/60*(h*.62f);c.drawRect(left+i*bw,y,left+i*bw+Math.max(.5f,bw-1),bottom,p);}
                }
                p.setColor(0xffbacbd3);c.drawText("C/N₀ 0–60 dB-Hz",left,h*.13f,p);c.drawText("Satellite signals",left,h*.95f,p);
            }else{
                p.setColor(0xffbacbd3);c.drawText("CPU % (green)   PSS MiB (blue, auto scale)",8,17,p);
                double max=1;for(Sample x:s.history)if(Double.isFinite(x.pss))max=Math.max(max,x.pss);
                long end=s.history.isEmpty()?0:s.history.get(s.history.size()-1).at;
                for(int k=0;k<2;k++){p.setColor(k==0?0xff20ae7b:0xff4285f4);p.setStrokeWidth(2);float px=0,py=0;boolean prev=false;
                    for(Sample x:s.history){double v=k==0?x.cpu/100:x.pss/max;if(!Double.isFinite(v)){prev=false;continue;}float xx=8+(w-16)*Math.max(0,1-(end-x.at)/60000f),yy=h-10-(h-34)*(float)v;if(prev)c.drawLine(px,py,xx,yy,p);else c.drawCircle(xx,yy,2,p);px=xx;py=yy;prev=true;}
                }
            }
        }
    }
}
