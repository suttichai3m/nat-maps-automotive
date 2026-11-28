package app.morphe.extension.mapsautomotive.patches;
public class NatEvPlanRulesTest{
 static int n;static void check(boolean b){if(!b)throw new AssertionError("case "+(n+1));n++;}static void near(double a,double b){check(Math.abs(a-b)<1e-8);}
 public static void main(String[] args){
  NatEvPlanRules.Result r=NatEvPlanRules.plan(80,82.56,150,16,150,16,15);near(r.oneKwh,24);near(r.totalKwh,48);check(Math.round(r.outbound)==51);check(Math.round(r.back)==22);near(r.needed,0);
  r=NatEvPlanRules.plan(80,61.44,150,16,200,20,15);near(r.totalKwh,64);check(r.back<0&&r.needed>0);check(r.outbound>0);
  try{NatEvPlanRules.plan(110,82,1,16,1,16,15);throw new AssertionError();}catch(IllegalArgumentException e){n++;}
  try{NatEvPlanRules.plan(80,0,1,16,1,16,15);throw new AssertionError();}catch(IllegalArgumentException e){n++;}
  try{NatEvPlanRules.plan(80,82,1,Double.NaN,1,16,15);throw new AssertionError();}catch(IllegalArgumentException e){n++;}
  check(NatEvPlanRules.band(20,30)==0);check(NatEvPlanRules.band(40,79)==1);check(NatEvPlanRules.band(80,90)==2);check(NatEvPlanRules.band(30,90)==-1);check(NatEvPlanRules.band(0,0)==-1);
  near(NatEvPlanRules.learned(100,16,100),16);check(Double.isNaN(NatEvPlanRules.learned(19,3,100)));check(Double.isNaN(NatEvPlanRules.learned(100,16,99)));check(Double.isNaN(NatEvPlanRules.learned(100,-16,100)));check(Double.isNaN(NatEvPlanRules.learned(100,70,100)));
  near(NatEvPlanRules.pressure(1,265,384),38.4);near(NatEvPlanRules.pressure(2,265,265),265/6.894757293);check(Double.isNaN(NatEvPlanRules.pressure(1,265,-1)));check(Double.isNaN(NatEvPlanRules.pressure(0,0,384)));
  check("BKK".equals(NatEvPlanRules.airport("bkk")));check("DMK".equals(NatEvPlanRules.airport("ดอนเมือง")));check(NatEvPlanRules.airport("สาทร")==null);
  System.out.println("PASS "+n+" EV planner, learning and calibration cases");
 }
}
