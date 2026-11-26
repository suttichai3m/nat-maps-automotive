package app.morphe.extension.mapsautomotive.patches;
final class NatThemeSchedule {
    static boolean validTimes(int night, int day) {
        return night >= 0 && night < 1440 && day >= 0 && day < 1440 && night != day;
    }
    static boolean nightAt(int minute, int night, int day) {
        if (minute < 0 || minute >= 1440 || !validTimes(night, day)) return false;
        return night < day ? minute >= night && minute < day : minute >= night || minute < day;
    }
}
