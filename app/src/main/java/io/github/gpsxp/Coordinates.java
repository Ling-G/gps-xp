package io.github.gpsxp;

/** Values are always longitude, latitude; no implicit coordinate-system conversion. */
public final class Coordinates {
    public static final double DEFAULT_LONGITUDE = 121.42404117992659;
    public static final double DEFAULT_LATITUDE = 29.66665962827546;
    public final boolean enabled;
    public final double longitude, latitude;

    public Coordinates(boolean enabled, double longitude, double latitude) {
        if (Double.isNaN(longitude) || Double.isInfinite(longitude) || longitude < -180 || longitude > 180
                || Double.isNaN(latitude) || Double.isInfinite(latitude) || latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("经度范围为 -180～180，纬度范围为 -90～90，且必须是有限数字。");
        }
        this.enabled = enabled;
        this.longitude = longitude;
        this.latitude = latitude;
    }
}
