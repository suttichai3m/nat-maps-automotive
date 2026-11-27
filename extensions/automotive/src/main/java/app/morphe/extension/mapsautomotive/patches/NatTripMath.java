package app.morphe.extension.mapsautomotive.patches;
final class NatTripMath {
    static boolean linked(long a,long b){return b>a&&b-a<=300000;}
    static final class Energy {
        double down,up,km; int pairs;
        void add(int a,int b,long ms,double distance){
            // Only close, plausible battery observations. Never call increases regenerative energy.
            if(a<0||b<0||ms<=0||ms>30000||!Double.isFinite(distance)||distance<0)return;
            long delta=(long)a-b;
            if(Math.abs(delta)>250000.0*ms/3600000.0+200)return;
            down+=Math.max(0,delta)/1000.0;up+=Math.max(0,-delta)/1000.0;km+=distance;pairs++;
        }
        double net(){return down-up;}
    }
    static double charge(double capacity,double soc,double target){
        if(!Double.isFinite(capacity)||capacity<=0||capacity>250||!Double.isFinite(soc)||!Double.isFinite(target)||soc<0||soc>100||target<soc||target>100)throw new IllegalArgumentException();
        return capacity*(target-soc)/100;
    }
}
