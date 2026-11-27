package app.morphe.extension.mapsautomotive.patches;

/** Read-only battery observation. No calibrated motor or regeneration signal is assumed. */
final class NatLiveEnergy {
    private long at=-1, sampleAt=-1;
    private int wh=-1, odo=-1, pairs=0, distancePairs=0;
    private double speed=Double.NaN, soc=Double.NaN, net=0, increase=0, coveredNet=0, km=0, power=Double.NaN;
    private boolean guided;
    static boolean fresh(long now,long stamp){return stamp>=0&&now>=stamp&&now-stamp<=30000;}
    static boolean moving(double s){return Double.isFinite(s)&&s>1&&s<=250;}
    static double validSoc(double s){return Double.isFinite(s)&&s>=0&&s<=100?s:Double.NaN;}
    void endRoute(){guided=false;at=-1;wh=odo=-1;speed=Double.NaN;net=increase=coveredNet=km=0;pairs=distancePairs=0;power=Double.NaN;}
    void accept(long now,double currentSoc,int energyWh,int mileage01km,double speedKmh,boolean route){
        if(sampleAt>=0&&now<=sampleAt)return;
        sampleAt=now;soc=validSoc(currentSoc);
        if(!route){endRoute();return;}
        if(!guided){endRoute();guided=true;}
        long dt=now-at;long delta=(long)wh-energyWh;
        power=Double.NaN;
        if(wh>=0&&energyWh>=0&&dt>=5000&&dt<=30000&&moving(speed)&&moving(speedKmh)){
            double kw=delta*3600.0/dt;
            if(Math.abs(kw)<=250){
                power=kw;net+=delta/1000.0;increase+=Math.max(0,-delta)/1000.0;pairs++;
                long units=(long)mileage01km-odo;double d=units/10.0;
                if(odo>=0&&mileage01km>=0&&units>=0&&d<=250*dt/3600000.0+.2){
                    coveredNet+=delta/1000.0;km+=d;distancePairs++;
                }
            }
        }
        at=now;wh=energyWh;odo=mileage01km;speed=speedKmh;
    }
    static final class Reading {
        final double soc,power,net,increase,rate,km; final int pairs;
        Reading(double soc,double power,double net,double increase,double rate,double km,int pairs){this.soc=soc;this.power=power;this.net=net;this.increase=increase;this.rate=rate;this.km=km;this.pairs=pairs;}
    }
    Reading read(long now,boolean route){
        boolean recent=fresh(now,sampleAt), valid=recent&&route&&guided&&pairs>0;
        return new Reading(recent?soc:Double.NaN,valid?power:Double.NaN,valid?net:Double.NaN,valid?increase:Double.NaN,
                valid&&distancePairs>0&&km>0&&coveredNet>=0?coveredNet*100/km:Double.NaN,valid&&distancePairs>0?km:Double.NaN,valid?pairs:0);
    }
}
