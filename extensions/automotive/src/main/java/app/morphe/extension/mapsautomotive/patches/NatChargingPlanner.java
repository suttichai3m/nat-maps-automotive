package app.morphe.extension.mapsautomotive.patches;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.widget.*;
import android.text.InputType;
import java.util.Locale;
/** Offline charging calculator and user-initiated native map search. No new API. */
final class NatChargingPlanner {
    private static EditText field(LinearLayout l,String title,String value){
        TextView label=new TextView(l.getContext());label.setText(title);l.addView(label);
        EditText e=new EditText(l.getContext());e.setSingleLine(true);e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);e.setText(value);l.addView(e);return e;
    }
    private static double val(EditText e){return Double.parseDouble(e.getText().toString().trim().replace(',','.'));}
    static void show(Activity a){
        LinearLayout l=new LinearLayout(a);l.setOrientation(1);l.setPadding(20,16,20,16);
        TextView note=new TextView(a);note.setText("Offline charging estimate • no live connector availability • use while parked");l.addView(note);
        EditText cap=field(l,"Usable capacity kWh / ความจุ","82.56"),soc=field(l,"Arrival SOC %","20"),target=field(l,"Target SOC %","80"),power=field(l,"Average charging power kW","85"),price=field(l,"Price THB/kWh","6.5");
        TextView result=new TextView(a);l.addView(result);
        Button calc=new Button(a);calc.setText("Calculate / คำนวณ");l.addView(calc);
        calc.setOnClickListener(v->{try{double k=NatTripMath.charge(val(cap),val(soc),val(target)),p=val(power),r=val(price);if(!Double.isFinite(p)||p<=0||!Double.isFinite(r)||r<0)throw new IllegalArgumentException();result.setText(String.format(Locale.US,"%.1f kWh • %.0f min • THB %.2f\nExcludes loss, queue and charging taper",k,k/p*60,k*r));}catch(RuntimeException e){result.setText("Check input / ตรวจข้อมูล");}});
        Button search=new Button(a);search.setText("Search EV charging in Maps");l.addView(search);
        search.setOnClickListener(v->{try{Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse("geo:0,0?q="+Uri.encode("EV charging station")));i.setPackage(a.getPackageName());a.startActivity(i);}catch(RuntimeException e){result.setText("Native search unavailable; use Maps search manually.");}});
        ScrollView scroll=new ScrollView(a);scroll.addView(l);new AlertDialog.Builder(a).setTitle("NAT EV Charging Planner").setView(scroll).setPositiveButton("Close",null).show();
    }
}
