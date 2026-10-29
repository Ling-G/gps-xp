import io.github.gpsxp.Coordinates;

/** Dependency-free checks of coordinate order, precision, bounds and invalid input. */
public final class CoordinatesCheck {
    private static int checks;
    private static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("Coordinate check " + checks);
    }
    private static void rejects(double lon, double lat) {
        try { new Coordinates(true, lon, lat); }
        catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError("Accepted invalid coordinates: " + lon + ", " + lat);
    }
    public static void main(String[] args) {
        Coordinates c = new Coordinates(true, Coordinates.DEFAULT_LONGITUDE, Coordinates.DEFAULT_LATITUDE);
        check(c.longitude == Double.parseDouble("121.42404117992659"));
        check(c.latitude == Double.parseDouble("29.66665962827546"));
        check(c.enabled);
        check(!new Coordinates(false, 0, 0).enabled);
        check(new Coordinates(true, -180, -90).longitude == -180);
        check(new Coordinates(true, 180, 90).latitude == 90);
        rejects(180.000001, 0); rejects(-180.000001, 0);
        rejects(0, 90.000001); rejects(0, -90.000001);
        rejects(Double.NaN, 0); rejects(0, Double.NaN);
        rejects(Double.POSITIVE_INFINITY, 0); rejects(Double.NEGATIVE_INFINITY, 0);
        rejects(0, Double.POSITIVE_INFINITY); rejects(0, Double.NEGATIVE_INFINITY);
        rejects(Coordinates.DEFAULT_LATITUDE, Coordinates.DEFAULT_LONGITUDE);
        System.out.println("PASS: " + checks + " coordinate checks");
    }
}
