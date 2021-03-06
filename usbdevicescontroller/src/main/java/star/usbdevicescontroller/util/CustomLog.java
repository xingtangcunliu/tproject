package star.usbdevicescontroller.util;

import android.util.Log;

/**
 * Created by StormShadow on 2015/6/8.
 * Knowledge is power.
 */
public class CustomLog {

    private static final String PREFIX = "-> ";
    private static final Boolean DEBUG_ON = true;
    private static final Boolean ERROR_ON = true;
    private static final Boolean INFO_ON = true;
    private static final Boolean WARN_ON = true;

    public static void debug(final String key, final String msg) {
        if(DEBUG_ON) {
            Log.e(key, PREFIX + msg);
        }
    }

    public static void error(final String key, final String msg) {
        if(ERROR_ON) {
            Log.e(key, PREFIX + msg);
        }
    }

    public static void info(final String key, final String msg) {
        if(INFO_ON) {
            Log.i(key, PREFIX + msg);
        }
    }

    public static void warn(final String key, final String msg) {
        if(WARN_ON) {
            Log.w(key, PREFIX + msg);
        }
    }
}
