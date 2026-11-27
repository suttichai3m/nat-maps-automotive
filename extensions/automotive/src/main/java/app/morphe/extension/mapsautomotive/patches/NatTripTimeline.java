package app.morphe.extension.mapsautomotive.patches;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationListener;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** Opt-in private GPS history. No network calls, no paid API, no background service. */
final class NatTripTimeline {
    private static final String PREF="nat_trip_timeline", ENABLED="enabled";
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private static final long GAP=300000, MAX_AGE=30000;
    private static final int MAX_POINTS=100000;
    private static Store db;
    private static LocationManager manager;
    private static boolean listening;
    private static final android.os.Handler WATCH=new android.os.Handler(Looper.getMainLooper());
    private static volatile long lastSample=-1;
    private static final Runnable WATCHDOG=()->{synchronized(NatTripTimeline.class){if(listening && SystemClock.elapsedRealtime()-lastSample>35000){stop();setStatus("Vehicle data stale — GPS paused");}}};
    private static long lastMoving=-1, lastFix=-1;
    private static volatile String status="Waiting for vehicle";
    private static void setStatus(String value){if(!value.equals(status)){status=value;ReportLog.i("NatTimeline",value);}}
    private static volatile String liveEnergy="Battery power estimate unavailable";
    private static int previousWh=-1; private static long previousEnergyAt=-1;
    private static final LocationListener listener=new LocationListener(){
        public void onLocationChanged(Location l){}
        public void onStatusChanged(String p,int s,Bundle b){}
        public void onProviderEnabled(String p){}
        public void onProviderDisabled(String p){}
    };
    private static boolean enabled(Context c){return c.getSharedPreferences(PREF,0).getBoolean(ENABLED,false);}
    private static synchronized Store store(Context c){
        if(db==null) db=new Store(c.getApplicationContext()==null?c:c.getApplicationContext());
        return db;
    }
    private static final class Store extends SQLiteOpenHelper {
        Store(Context c){super(c,"nat_private_timeline.db",null,1);}
        public void onCreate(SQLiteDatabase d){
            d.execSQL("CREATE TABLE points(id INTEGER PRIMARY KEY, wall INTEGER NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL, accuracy REAL NOT NULL, wh INTEGER, soc REAL, odo INTEGER)");
            d.execSQL("CREATE INDEX points_wall ON points(wall)");
        }
        public void onUpgrade(SQLiteDatabase d,int a,int b){}
    }
    private static void stop(){
        if(listening && manager!=null) try{manager.removeUpdates(listener);}catch(RuntimeException ignored){}
        listening=false; lastFix=-1; previousWh=-1; previousEnergyAt=-1; liveEnergy="Battery power estimate unavailable"; WATCH.removeCallbacks(WATCHDOG);
    }
    /** Existing BYD worker, approximately every 10 seconds. Only moving samples are stored. */
    static synchronized void sample(Context c,BydDriveLog.Sample s){
        if(!enabled(c)){stop();lastMoving=-1;setStatus("Off");return;}
        long now=SystemClock.elapsedRealtime(); lastSample=now;
        double kw=NatTripMath.averageKw(previousWh,s.energyWh,now-previousEnergyAt);
        liveEnergy=Double.isFinite(kw)?String.format(Locale.US,"Recent battery change rate: %.1f kW (estimated average, not motor power)",kw):"Battery power estimate unavailable";
        previousWh=s.energyWh;previousEnergyAt=now;
        WATCH.removeCallbacks(WATCHDOG); WATCH.postDelayed(WATCHDOG,36000);
        if(!NatStabilityMath.moving(s.speedKmh)){
            if(!Double.isFinite(s.speedKmh)||s.speedKmh>250||lastMoving<0||now-lastMoving>=60000)stop();
            setStatus("Parked or speed unavailable");return;
        }
        lastMoving=now;
        try{
            if(manager==null)manager=(LocationManager)c.getSystemService(Context.LOCATION_SERVICE);
            if(manager==null){status="GPS unavailable";return;}
            if(!listening){
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER,10000,5,listener,Looper.getMainLooper());
                listening=true;
            }
            Location f=manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if(f==null){setStatus("Waiting for GPS");return;}
            long fix=f.getElapsedRealtimeNanos()/1000000;
            if(fix<=0||now-fix<0||now-fix>MAX_AGE||!f.hasAccuracy()||f.getAccuracy()>50||f.getAccuracy()<0){setStatus("Waiting for accurate GPS");return;}
            if(fix<=lastFix)return;
            double lat=f.getLatitude(),lon=f.getLongitude();
            if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)return;
            ContentValues v=new ContentValues();
            v.put("wall",s.wall);v.put("lat",lat);v.put("lon",lon);v.put("accuracy",f.getAccuracy());
            if(s.energyWh>=0)v.put("wh",s.energyWh);
            if(Double.isFinite(s.soc))v.put("soc",s.soc);
            if(s.mileage01km>=0)v.put("odo",s.mileage01km);
            SQLiteDatabase d=store(c).getWritableDatabase();
            d.insertOrThrow("points",null,v);
            lastFix=fix;setStatus("Recording GPS");
            d.execSQL("DELETE FROM points WHERE id <= (SELECT MAX(id)-"+MAX_POINTS+" FROM points) OR wall < ?",new Object[]{System.currentTimeMillis()-90L*86400000});
        }catch(SecurityException e){stop();setStatus("Location permission required");}
        catch(RuntimeException e){stop();setStatus("GPS or storage unavailable");}
    }
    private static final class Point {
        long time; double lat,lon,soc=Double.NaN; int wh=-1,odo=-1;
        Point(Cursor c){time=c.getLong(0);lat=c.getDouble(1);lon=c.getDouble(2);if(!c.isNull(3))wh=c.getInt(3);if(!c.isNull(4))odo=c.getInt(4);if(!c.isNull(5))soc=c.getDouble(5);}
    }
    private static ArrayList<Point> points(Context c,long start,long end){
        ArrayList<Point> out=new ArrayList<>();
        try(Cursor r=store(c).getReadableDatabase().rawQuery("SELECT wall,lat,lon,wh,odo,soc FROM points WHERE wall>=? AND wall<? ORDER BY wall,id",new String[]{Long.toString(start),Long.toString(end)})){
            while(r.moveToNext())out.add(new Point(r));
        }
        return out;
    }
    private static boolean linked(Point a,Point b){return NatTripMath.linked(a.time,b.time);}
    private static double km(Point a,Point b){
        if(!NatTripMath.contiguous(a.time,b.time))return 0;
        double hours=(b.time-a.time)/3600000.0;
        if(a.odo>=0&&b.odo>=a.odo){double k=(b.odo-a.odo)/10.0;if(k<=hours*250+0.1)return k;}
        float[] r=new float[1];Location.distanceBetween(a.lat,a.lon,b.lat,b.lon,r);
        return r[0]/1000.0<=hours*250?r[0]/1000.0:0;
    }
    private static final class RouteView extends View {
        final ArrayList<Point> points;final Paint paint=new Paint(3);int cursor=-1;
        void seek(int n){cursor=n;invalidate();}
        RouteView(Context c,ArrayList<Point> p){super(c);points=p;setContentDescription("Recorded GPS route schematic");}
        protected void onDraw(Canvas canvas){
            super.onDraw(canvas);canvas.drawColor(0xff172b36);
            if(points.isEmpty()||getWidth()<=40||getHeight()<=40)return;
            double minX=180,maxX=-180,minY=90,maxY=-90;
            for(Point p:points){minX=Math.min(minX,p.lon);maxX=Math.max(maxX,p.lon);minY=Math.min(minY,p.lat);maxY=Math.max(maxY,p.lat);}
            double dx=Math.max(.0001,maxX-minX),dy=Math.max(.0001,maxY-minY);
            double scale=Math.min((getWidth()-40)/dx,(getHeight()-40)/dy);
            float prevX=0,prevY=0;Point previous=null;
            paint.setStrokeWidth(4);paint.setColor(0xff58d4b1);
            for(Point p:points){
                float x=(float)(20+(p.lon-minX)*scale),y=(float)(getHeight()-20-(p.lat-minY)*scale);
                if(previous!=null&&NatTripMath.contiguous(previous.time,p.time))canvas.drawLine(prevX,prevY,x,y,paint);
                else canvas.drawCircle(x,y,5,paint);
                previous=p;prevX=x;prevY=y;
            }
            paint.setColor(0xfffbbf24);canvas.drawCircle(prevX,prevY,6,paint);
            if(cursor>=0&&cursor<points.size()){Point p=points.get(cursor);paint.setColor(0xff60a5fa);canvas.drawCircle((float)(20+(p.lon-minX)*scale),(float)(getHeight()-20-(p.lat-minY)*scale),9,paint);}
        }
    }
    private static long day(int offset){Calendar c=Calendar.getInstance();c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);c.add(Calendar.DATE,offset);return c.getTimeInMillis();}
    private static String fmt(long t,String pattern){return new SimpleDateFormat(pattern,Locale.getDefault()).format(new Date(t));}
    static void show(Activity a){
        if(a==null||a.isFinishing())return;
        Context c=a;LinearLayout body=new LinearLayout(c);body.setOrientation(1);body.setPadding(20,16,20,16);
        ScrollView scroll=new ScrollView(c);scroll.addView(body);
        Switch toggle=new Switch(c);toggle.setText("Enable private GPS recording / บันทึก GPS");toggle.setChecked(enabled(c));body.addView(toggle);
        TextView note=new TextView(c);note.setText("Local only • moving vehicle • GPS permission required • 90 days / 100,000 points maximum. Use while parked.");body.addView(note);
        TextView summary=new TextView(c);body.addView(summary);
        TextView live=new TextView(c);body.addView(live);
        LinearLayout route=new LinearLayout(c);route.setOrientation(1);body.addView(route);
        LinearLayout rows=new LinearLayout(c);rows.setOrientation(1);body.addView(rows);
        final int[] offset={0};final int[] generation={0}; final int[] period={1};
        Spinner span=new Spinner(c);span.setAdapter(new ArrayAdapter<String>(c,android.R.layout.simple_spinner_dropdown_item,new String[]{"Daily / รายวัน","7 days / 7 วัน","30 days / 30 วัน"}));body.addView(span);
        Button prediction=new Button(c);prediction.setText("Energy prediction / วางแผนพลังงาน");prediction.setOnClickListener(v->NatPrediction.show(a));body.addView(prediction);
        Button charging=new Button(c);charging.setText("EV Charging / วางแผนชาร์จ");charging.setOnClickListener(v->NatChargingPlanner.show(a));body.addView(charging);
        Runnable refresh=()->{
            final int request=++generation[0];long start=day(offset[0]-period[0]+1),end=day(offset[0]+1);
            summary.setText("Loading…");
            IO.execute(()->{
                try{
                    ArrayList<Point> data=points(c,start,end);
                    double distance=0;long moving=0;int trips=0;NatTripMath.Energy energy=new NatTripMath.Energy();
                    ArrayList<String> labels=new ArrayList<>();Point first=null,prev=null;
                    double tripKm=0;
                    for(Point p:data){
                        if(prev==null||!linked(prev,p)){
                            if(first!=null)labels.add(fmt(first.time,"HH:mm")+" – "+fmt(prev.time,"HH:mm")+"   "+String.format(Locale.US,"%.1f km",tripKm));
                            trips++;first=p;tripKm=0;
                        }else{
                            double k=km(prev,p);distance+=k;tripKm+=k;if(NatTripMath.contiguous(prev.time,p.time))moving+=p.time-prev.time;
                            energy.add(prev.wh,p.wh,p.time-prev.time,k);
                        }
                        prev=p;
                    }
                    if(first!=null)labels.add(fmt(first.time,"HH:mm")+" – "+fmt(prev.time,"HH:mm")+"   "+String.format(Locale.US,"%.1f km",tripKm));
                    final String text=fmt(start,"yyyy-MM-dd")+" – "+fmt(end-1,"yyyy-MM-dd")+" • "+status+"\n"+String.format(Locale.US,"%.1f km  |  %d min  |  %d segments",distance,moving/60000,trips)+"\n"+(energy.pairs>0?String.format(Locale.US,"Observed net battery change: %.2f kWh\nDecrease %.2f / increase %.2f kWh\nEnergy-covered distance: %.1f km",energy.net(),energy.down,energy.up,energy.km)+(energy.km>0?String.format(Locale.US,"\nObserved net: %.1f kWh/100km",energy.net()*100/energy.km):"")+"\nPartial samples; increases are not verified regen.":"Energy unavailable");
                    a.runOnUiThread(()->{if(a.isFinishing()||request!=generation[0])return;summary.setText(text);route.removeAllViews();rows.removeAllViews();
                        RouteView map=new RouteView(c,data);route.addView(map,new LinearLayout.LayoutParams(-1,(int)(220*c.getResources().getDisplayMetrics().density)));
                        TextView replay=new TextView(c);replay.setText("Replay / เลื่อนดูเวลา");route.addView(replay);
                        SeekBar seek=new SeekBar(c);seek.setMax(Math.max(0,data.size()-1));seek.setEnabled(data.size()>1);route.addView(seek);
                        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){} public void onStopTrackingTouch(SeekBar b){} public void onProgressChanged(SeekBar b,int n,boolean user){if(n<data.size()){Point p=data.get(n);map.seek(n);replay.setText(fmt(p.time,"yyyy-MM-dd HH:mm:ss")+(Double.isFinite(p.soc)?String.format(Locale.US," • SOC %.1f%%",p.soc):""));}}});
                        for(String label:labels){TextView line=new TextView(c);line.setText(label);line.setPadding(0,8,0,8);rows.addView(line);}
                    });
                }catch(RuntimeException e){a.runOnUiThread(()->{if(request==generation[0])summary.setText("History unavailable");});}
            });
        };
        toggle.setOnCheckedChangeListener((v,on)->{
            c.getSharedPreferences(PREF,0).edit().putBoolean(ENABLED,on).apply();
            if(!on){synchronized(NatTripTimeline.class){stop();lastMoving=-1;setStatus("Off");}}
            refresh.run();
        });
        span.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){} public void onItemSelected(android.widget.AdapterView<?> p,View v,int position,long id){period[0]=new int[]{1,7,30}[position];refresh.run();}});
        LinearLayout nav=new LinearLayout(c);
        Button back=new Button(c);back.setText("Previous day");back.setOnClickListener(v->{if(offset[0]>-89)offset[0]--;refresh.run();});nav.addView(back,new LinearLayout.LayoutParams(0,-2,1));
        Button next=new Button(c);next.setText("Next day");next.setOnClickListener(v->{if(offset[0]<0)offset[0]++;refresh.run();});nav.addView(next,new LinearLayout.LayoutParams(0,-2,1));body.addView(nav);
        Button export=new Button(c);export.setText("Export selected period CSV + GPX + JSON");body.addView(export);
        export.setOnClickListener(v->{long start=day(offset[0]-period[0]+1),end=day(offset[0]+1);IO.execute(()->{
            String result;
            try{
                File dir=c.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS);
                if(dir==null)throw new IOException("storage");
                ArrayList<Point> data=points(c,start,end);
                String prefix="nat-timeline-"+fmt(start,"yyyyMMdd")+"-"+fmt(end-1,"yyyyMMdd");
                File f=new File(dir,prefix+".csv");
                try(PrintWriter w=new PrintWriter(new OutputStreamWriter(new FileOutputStream(f),java.nio.charset.StandardCharsets.UTF_8))){
                    w.println("wall_ms,latitude,longitude,battery_wh,odometer_0.1km,soc");
                    for(Point p:data)w.println(p.time+","+p.lat+","+p.lon+","+(p.wh<0?"":p.wh)+","+(p.odo<0?"":p.odo)+","+(Double.isFinite(p.soc)?p.soc:""));
                    if(w.checkError())throw new IOException("write");
                }
                File gpx=new File(dir,prefix+".gpx");
                try(PrintWriter w=new PrintWriter(new OutputStreamWriter(new FileOutputStream(gpx),java.nio.charset.StandardCharsets.UTF_8))){
                    w.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?><gpx version=\"1.1\" creator=\"NAT Timeline\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><name>NAT recorded route</name>");
                    SimpleDateFormat utc=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",Locale.US);utc.setTimeZone(TimeZone.getTimeZone("UTC"));
                    Point prev=null;for(Point p:data){if(prev==null||!NatTripMath.contiguous(prev.time,p.time)){if(prev!=null)w.println("</trkseg>");w.println("<trkseg>");}w.println("<trkpt lat=\""+p.lat+"\" lon=\""+p.lon+"\"><time>"+utc.format(new Date(p.time))+"</time></trkpt>");prev=p;}
                    if(prev!=null)w.println("</trkseg>");w.println("</trk></gpx>");if(w.checkError())throw new IOException("GPX write");
                }
                File json=new File(dir,prefix+".json");
                try(PrintWriter w=new PrintWriter(new OutputStreamWriter(new FileOutputStream(json),java.nio.charset.StandardCharsets.UTF_8))){
                    w.print("[");boolean first=true;for(Point p:data){if(!first)w.print(",");first=false;w.print("{\"wall_ms\":"+p.time+",\"lat\":"+p.lat+",\"lon\":"+p.lon+",\"battery_wh\":"+(p.wh<0?"null":Integer.toString(p.wh))+",\"soc\":"+(Double.isFinite(p.soc)?Double.toString(p.soc):"null")+"}");}w.println("]");if(w.checkError())throw new IOException("JSON write");
                }
                result="Exported CSV, GPX, JSON: "+dir.getAbsolutePath();
            }catch(Exception e){result="Export failed";}
            final String msg=result;a.runOnUiThread(()->new AlertDialog.Builder(a).setMessage(msg).setPositiveButton("OK",null).show());
        });});
        Button erase=new Button(c);erase.setText("Delete all GPS history");body.addView(erase);
        erase.setOnClickListener(v->new AlertDialog.Builder(a).setMessage("Delete all private GPS history? Existing exported copies are not deleted.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{
            toggle.setChecked(false);toggle.setEnabled(false);IO.execute(()->{try{store(c).getWritableDatabase().delete("points",null,null);a.runOnUiThread(()->{toggle.setEnabled(true);refresh.run();});}catch(RuntimeException e){a.runOnUiThread(()->{toggle.setEnabled(true);summary.setText("Delete failed");});}});
        }).show());
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle("NAT V2.0 • Trip Timeline").setView(scroll).setPositiveButton("Close",null).create();dialog.setOnDismissListener(d->generation[0]++);dialog.show();
        Runnable update=new Runnable(){public void run(){if(!dialog.isShowing()||a.isDestroyed())return;live.setText(enabled(c)&&SystemClock.elapsedRealtime()-lastSample<=35000?liveEnergy:"Battery power estimate unavailable");WATCH.postDelayed(this,2000);}};update.run();
        refresh.run();
    }
}
