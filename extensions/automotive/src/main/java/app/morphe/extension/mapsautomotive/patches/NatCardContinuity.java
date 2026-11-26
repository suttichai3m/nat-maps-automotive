package app.morphe.extension.mapsautomotive.patches;
/** Bounded UI recovery only; not a verified navigation lifecycle. */
final class NatCardContinuity {
    static final long MAX_MS = 6000;
    static boolean keep(long elapsedMs) { return elapsedMs >= 0 && elapsedMs <= MAX_MS; }
}
