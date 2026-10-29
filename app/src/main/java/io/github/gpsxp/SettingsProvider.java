package io.github.gpsxp;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/** Public read-only settings bridge; only the module's own private UI can write. */
public final class SettingsProvider extends ContentProvider {
    public static final Uri URI = Uri.parse("content://io.github.gpsxp.settings/config");
    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("location", Context.MODE_PRIVATE);
    }
    static Coordinates read(Context context) {
        SharedPreferences p = prefs(context);
        return new Coordinates(p.getBoolean("enabled", true),
                Double.parseDouble(p.getString("longitude", Double.toString(Coordinates.DEFAULT_LONGITUDE))),
                Double.parseDouble(p.getString("latitude", Double.toString(Coordinates.DEFAULT_LATITUDE))));
    }
    @Override public boolean onCreate() { return true; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        if (!URI.equals(uri)) throw new IllegalArgumentException("Unknown URI");
        Coordinates c = read(getContext());
        MatrixCursor result = new MatrixCursor(new String[]{"enabled", "longitude", "latitude"});
        result.addRow(new Object[]{c.enabled ? 1 : 0, c.longitude, c.latitude});
        return result;
    }
    @Override public String getType(Uri uri) { return "vnd.android.cursor.item/vnd.gpsxp.config"; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new SecurityException("Read only"); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new SecurityException("Read only"); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new SecurityException("Read only"); }
}
