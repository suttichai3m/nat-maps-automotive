package app.morphe.extension.mapsautomotive.patches;
/** Bounded UI recovery only; not a verified navigation lifecycle. */
final class NatCardContinuity {
    static boolean keep(long elapsedMs) { return elapsedMs >= 0 && elapsedMs <= 2500; }
}
