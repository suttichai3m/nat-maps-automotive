package app.morphe.extension.mapsautomotive.patches;
public final class NatV36RulesTest{
 static int n;static void check(boolean b){if(!b)throw new AssertionError("case "+(n+1));n++;}
 public static void main(String[] args){
 check(NatV36Rules.interval(10000,20,30)==1);check(NatV36Rules.interval(10000,0,0)==2);check(NatV36Rules.interval(10000,0,20)==0);check(NatV36Rules.interval(31000,20,20)==0);check(NatV36Rules.interval(10000,Double.NaN,20)==0);check(NatV36Rules.interval(0,10,10)==0);
 check(NatV36Rules.chargeDelta(20,40,3600000)==20);check(Double.isNaN(NatV36Rules.chargeDelta(40,20,1000)));check(Double.isNaN(NatV36Rules.chargeDelta(20,40,0)));check(Double.isNaN(NatV36Rules.chargeDelta(20,40,86400001)));
 NatToolbarLayout.Slot route=NatToolbarLayout.chooseV36(true,true,0,0,1600,900,48,7,12,true,s->true);check(route!=null&&route.count==7);
 NatToolbarLayout.Slot idle=NatToolbarLayout.chooseV36(true,false,0,0,1600,900,48,7,12,true,s->true);check(idle!=null&&idle.count==3);
 check(NatToolbarLayout.chooseV36(true,true,0,0,1600,900,48,7,12,true,s->s.count<=6).count==6);
 check(NatToolbarLayout.chooseV36(true,true,0,0,1600,900,48,7,12,true,s->false)==null);
 System.out.println("PASS "+n+" V3.6 rules");
 }
}
