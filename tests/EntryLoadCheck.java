/** Run with the SDK's stub android.jar: early Android calls throw "Stub!" here. */
public final class EntryLoadCheck {
    public static void main(String[] args) throws Exception {
        Class.forName("io.github.gpsxp.LocationHook");
        System.out.println("PASS: Xposed entry initializes without an Android Looper");
    }
}
