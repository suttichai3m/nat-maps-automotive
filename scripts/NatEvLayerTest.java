package app.morphe.extension.mapsautomotive.patches;
public final class NatEvLayerTest {
 static int n;static void check(boolean b){if(!b)throw new AssertionError("case "+(n+1));n++;}
 public static void main(String[] args){
 check(NatMinimalEvRules.explicitShow("Show EV charging stations"));check(NatMinimalEvRules.explicitShow("แสดงสถานีชาร์จ"));
 check(!NatMinimalEvRules.explicitShow("Show nearby charging stations"));check(!NatMinimalEvRules.explicitShow("Charging stations"));
 check(!NatMinimalEvRules.explicitShow("Search"));check(!NatMinimalEvRules.explicitHide("Hide route"));
 check(NatMinimalEvRules.layerAction(true,false,1,0)==1);check(NatMinimalEvRules.layerAction(true,false,0,1)==-1);
 check(NatMinimalEvRules.layerAction(true,true,1,0)==0);check(NatMinimalEvRules.layerAction(false,false,1,0)==0);
 check(NatMinimalEvRules.layerAction(true,false,0,0)==0);check(NatMinimalEvRules.layerAction(true,false,1,1)==0);
 check(NatMinimalEvRules.layerAction(true,false,2,0)==0);check(NatMinimalEvRules.layerAction(true,false,0,2)==0);
 check(NatToolbarLayout.chooseV37Ev(true,false,0,0,1600,900,48,7,12,true,s->true).count==4);
 check(NatToolbarLayout.chooseV37Ev(true,false,0,0,1600,900,48,7,12,true,s->s.count<=3).count==3);
 check(NatToolbarLayout.chooseV37Ev(true,true,0,0,1600,900,48,7,12,true,s->true).count==7);
 check(NatToolbarLayout.chooseV37Ev(true,false,0,0,1600,900,48,7,12,true,s->false)==null);
 System.out.println("PASS "+n+" EV layer selection cases");
 }
}
