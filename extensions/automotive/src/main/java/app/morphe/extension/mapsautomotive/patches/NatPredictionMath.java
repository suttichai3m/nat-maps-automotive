package app.morphe.extension.mapsautomotive.patches;
final class NatPredictionMath {
    static double arrival(double soc, double km, double rate, double capacity) {
        if (!Double.isFinite(soc) || soc < 0 || soc > 100 || !Double.isFinite(km) || km < 0 || km > 10000 || !Double.isFinite(rate) || rate < 5 || rate > 50 || !Double.isFinite(capacity) || capacity < 5 || capacity > 150) throw new IllegalArgumentException("Invalid input");
        return soc - km * rate / capacity;
    }
}
