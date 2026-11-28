package app.morphe.extension.mapsautomotive.patches;
final class NatReadableRules {
 static double estimate(double snapshotSoc,int snapshotArrival,double snapshotKm,double cap,int routeArrival,double routeKm,double liveSoc){
  if(!NatEvPlanRules.bounded(snapshotSoc,0,100)||!NatEvPlanRules.bounded(liveSoc,0,100)||!NatEvPlanRules.bounded(cap,5,150)||!NatEvPlanRules.bounded(snapshotKm,.1,10000)||!NatEvPlanRules.bounded(routeKm,.1,10000)||snapshotArrival>100||routeArrival>100||snapshotArrival< -100||routeArrival< -100)return Double.NaN;
  // Small differences in asynchronously refreshed UI fields only. No route or charger inference.
  if(Math.abs(snapshotArrival-routeArrival)>1||Math.abs(snapshotKm-routeKm)>Math.max(.2,snapshotKm*.01)||Math.abs(snapshotSoc-liveSoc)>2)return Double.NaN;
  double energy=cap*(liveSoc-routeArrival)/100.0;return energy>=0?energy:Double.NaN;
 }
}
