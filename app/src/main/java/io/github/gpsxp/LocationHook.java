package io.github.gpsxp;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** App-process hooks only. Configuration is a snapshot until process restart. */
public final class LocationHook implements IXposedHookLoadPackage {
    private static volatile Coordinates config;
    private static volatile Context context;
    private static final List<Subscription> subscriptions = new ArrayList<>();
    // Entry classes may be instantiated in zygote, before any application Looper exists.
    private static Handler main;

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p) {
        if (p.packageName.equals("io.github.gpsxp") || p.packageName.equals("android")
                || p.packageName.equals("com.android.systemui")) return;
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (context != null) return;
                main = new Handler(Looper.getMainLooper());
                context = ((Context) param.args[0]).getApplicationContext();
                if (context == null) context = (Context) param.args[0];
                try (Cursor cursor = context.getContentResolver().query(SettingsProvider.URI, null, null, null, null)) {
                    if (cursor == null || !cursor.moveToFirst()) throw new IllegalStateException("No settings");
                    config = new Coordinates(cursor.getInt(0) != 0, cursor.getDouble(1), cursor.getDouble(2));
                    XposedBridge.log("GPS XP: settings loaded for " + p.packageName + ", enabled=" + config.enabled);
                } catch (Throwable e) {
                    // Never replace real location with unrequested defaults when the bridge fails.
                    XposedBridge.log("GPS XP: settings unavailable for " + p.packageName + "; hooks inactive: " + e);
                }
            }
        });
        hookGetter("getLatitude", true);
        hookGetter("getLongitude", false);
        hookMockFlag("isFromMockProvider");
        hookMockFlag("isMock");
        XposedBridge.hookAllMethods(LocationManager.class, "getLastKnownLocation", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (active() && providerSupported(param.args)) param.setResult(point(provider(param.args)));
            }
        });
        XC_MethodHook updates = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (!active() || !providerSupported(param.args)) return;
                LocationListener listener = find(param.args, LocationListener.class);
                // PendingIntent registrations remain with Android; never silently swallow them.
                if (listener == null) return;
                Executor executor = find(param.args, Executor.class);
                Looper looper = find(param.args, Looper.class);
                Handler handler = new Handler(looper == null ? Looper.getMainLooper() : looper);
                long interval = 1000L;
                int maxUpdates = ((Method) param.method).getName().equals("requestSingleUpdate") ? 1 : Integer.MAX_VALUE;
                long duration = Long.MAX_VALUE;
                boolean distanceFiltered = false;
                for (Object arg : param.args) {
                    if (arg instanceof Long) { interval = Math.max(1000L, (Long) arg); break; }
                }
                for (Object arg : param.args) {
                    if (arg instanceof Float && (Float) arg > 0f) distanceFiltered = true;
                    if (arg != null && arg.getClass().getName().equals("android.location.LocationRequest")) {
                        interval = Math.max(1000L, number(arg, "getIntervalMillis", interval));
                        maxUpdates = (int) Math.min(maxUpdates, number(arg, "getMaxUpdates", maxUpdates));
                        duration = number(arg, "getDurationMillis", duration);
                        distanceFiltered = numberDouble(arg, "getMinUpdateDistanceMeters", 0) > 0;
                    }
                }
                Subscription s = new Subscription(param.thisObject, listener, executor, handler,
                        provider(param.args), interval, maxUpdates, duration, distanceFiltered);
                synchronized (subscriptions) {
                    cancel(param.thisObject, listener);
                    subscriptions.add(s);
                }
                // Suppress the real registration so callbacks cannot mix real and simulated points.
                param.setResult(null);
                if (duration != Long.MAX_VALUE) main.postDelayed(s.expire, duration);
                main.post(s);
            }
        };
        XposedBridge.hookAllMethods(LocationManager.class, "requestLocationUpdates", updates);
        XposedBridge.hookAllMethods(LocationManager.class, "requestSingleUpdate", updates);
        XposedBridge.hookAllMethods(LocationManager.class, "removeUpdates", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                LocationListener listener = find(param.args, LocationListener.class);
                if (listener != null) synchronized (subscriptions) { cancel(param.thisObject, listener); }
                // Also let Android remove any pre-existing real subscription.
            }
        });
        XposedBridge.hookAllMethods(LocationManager.class, "getCurrentLocation", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (!active() || !providerSupported(param.args)) return;
                Consumer<?> consumer = find(param.args, Consumer.class);
                Executor executor = find(param.args, Executor.class);
                CancellationSignal signal = find(param.args, CancellationSignal.class);
                if (consumer == null || executor == null) return;
                String provider = provider(param.args);
                param.setResult(null);
                main.post(() -> {
                    if (signal != null && signal.isCanceled()) return;
                    try { executor.execute(() -> {
                        if (signal == null || !signal.isCanceled()) {
                            @SuppressWarnings("unchecked") Consumer<Location> target = (Consumer<Location>) consumer;
                            target.accept(point(provider));
                        }
                    }); } catch (RuntimeException e) { XposedBridge.log("GPS XP: executor rejected current location: " + e); }
                });
            }
        });
    }

    private static void hookGetter(String name, boolean latitude) {
        XposedBridge.hookAllMethods(Location.class, name, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (active()) param.setResult(latitude ? config.latitude : config.longitude);
            }
        });
    }
    private static boolean active() {
        Context c = context;
        return config != null && config.enabled && c != null
                && (c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED);
    }
    private static void hookMockFlag(String method) {
        // hookAllMethods returns an empty set on Android versions without this API.
        XposedBridge.hookAllMethods(Location.class, method, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (active()) param.setResult(false);
            }
        });
    }
    private static Location point(String provider) {
        Coordinates c = config;
        Location point = new Location(provider);
        point.setLongitude(c.longitude); point.setLatitude(c.latitude);
        point.setAccuracy(5f); point.setAltitude(0d); point.setSpeed(0f); point.setBearing(0f);
        point.setTime(System.currentTimeMillis()); point.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        // A fresh Location has no mock flag; do not add one to generated fixes.
        return point;
    }
    private static boolean providerSupported(Object[] args) {
        for (Object arg : args) if (arg instanceof String) {
            return arg.equals("gps") || arg.equals("network") || arg.equals("passive") || arg.equals("fused");
        }
        return true;
    }
    private static String provider(Object[] args) {
        for (Object arg : args) if (arg instanceof String) return (String) arg;
        return LocationManager.GPS_PROVIDER;
    }
    private static <T> T find(Object[] args, Class<T> type) {
        for (Object arg : args) if (type.isInstance(arg)) return type.cast(arg);
        return null;
    }
    private static long number(Object object, String method, long fallback) {
        try { return ((Number) XposedHelpers.callMethod(object, method)).longValue(); }
        catch (Throwable ignored) { return fallback; }
    }
    private static double numberDouble(Object object, String method, double fallback) {
        try { return ((Number) XposedHelpers.callMethod(object, method)).doubleValue(); }
        catch (Throwable ignored) { return fallback; }
    }
    private static void cancel(Object manager, LocationListener listener) {
        for (int i = subscriptions.size() - 1; i >= 0; i--) {
            Subscription s = subscriptions.get(i);
            if (s.manager == manager && s.listener == listener) {
                s.cancelled = true; main.removeCallbacks(s); main.removeCallbacks(s.expire); subscriptions.remove(i);
            }
        }
    }
    private static final class Subscription implements Runnable {
        final Object manager;
        final LocationListener listener;
        final Executor executor;
        final Handler handler;
        final String provider;
        final long interval, started = SystemClock.elapsedRealtime(), duration;
        final int maxUpdates;
        final boolean distanceFiltered;
        final Runnable expire = this::finish;
        int delivered;
        volatile boolean cancelled;
        Subscription(Object manager, LocationListener listener, Executor executor, Handler handler,
                     String provider, long interval, int maxUpdates, long duration, boolean distanceFiltered) {
            this.manager = manager; this.listener = listener; this.executor = executor; this.handler = handler;
            this.provider = provider; this.interval = interval; this.maxUpdates = maxUpdates;
            this.duration = duration; this.distanceFiltered = distanceFiltered;
        }
        void finish() {
            cancelled = true;
            main.removeCallbacks(this);
            main.removeCallbacks(expire);
            synchronized (subscriptions) { subscriptions.remove(this); }
        }
        @Override public void run() {
            if (cancelled) return;
            if (!active() || SystemClock.elapsedRealtime() - started >= duration) { finish(); return; }
            Runnable callback = () -> {
                if (cancelled) return;
                if (!active()) { finish(); return; }
                if (SystemClock.elapsedRealtime() - started >= duration) { finish(); return; }
                try { listener.onLocationChanged(point(provider)); }
                catch (RuntimeException e) { XposedBridge.log("GPS XP: listener failed: " + e); finish(); return; }
                if (++delivered >= maxUpdates) { finish(); return; }
                // With a fixed point, positive minDistance permits only the initial fix.
                if (!cancelled && !distanceFiltered) main.postDelayed(this, interval);
            };
            try {
                if (executor != null) executor.execute(callback);
                else if (!handler.post(callback)) finish();
            } catch (RuntimeException e) { XposedBridge.log("GPS XP: rejected callback: " + e); finish(); }
        }
    }
}
