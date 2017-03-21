package star.usbdevicescontroller.util;import android.app.AlarmManager;import android.app.PendingIntent;import android.content.BroadcastReceiver;import android.content.Context;import android.content.Intent;import android.content.pm.PackageInfo;import android.content.pm.PackageManager;import android.content.pm.PackageManager.NameNotFoundException;import android.os.Build;import android.os.Environment;import android.util.Log;import java.io.File;import java.io.FileOutputStream;import java.io.PrintWriter;import java.io.StringWriter;import java.io.Writer;import java.lang.Thread.UncaughtExceptionHandler;import java.lang.reflect.Field;import java.text.DateFormat;import java.text.SimpleDateFormat;import java.util.Calendar;import java.util.Date;import java.util.GregorianCalendar;import java.util.HashMap;import java.util.Map;/** * UncaughtException处理类,当程序发生Uncaught异常的时候,有该类来接管程序,并记录发送错误报告.   */public class CrashHandler implements UncaughtExceptionHandler {    public static final String TAG = "CrashHandler";    private static final String CRACHPATH = "/sdcard/crash/usb/";    // CrashHandler 实例      private static class CrashHolder {        private static CrashHandler INSTANCE = new CrashHandler();    }    /** 保证只有一个 CrashHandler 实例 */    private CrashHandler() {    }    /** 获取 CrashHandler 实例 ,单例模式 */    public static CrashHandler getInstance() {        return CrashHolder.INSTANCE;    }    // 程序的 Context 对象      private Context mContext;    // 系统默认的 UncaughtException 处理类      private UncaughtExceptionHandler mDefaultHandler;    // 用来存储设备信息和异常信息      private Map<String, String> infos = new HashMap<String, String>();    // 用于格式化日期,作为日志文件名的一部分      private DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");    /**     * 初始化      *     * @param context     */    public void init(Context context) {        mContext = context;        // 获取系统默认的 UncaughtException 处理器          mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();        // 设置该 CrashHandler 为程序的默认处理器          Thread.setDefaultUncaughtExceptionHandler(this);    }    /**     * 当 UncaughtException 发生时会转入该函数来处理      */    @Override    public void uncaughtException(Thread thread, Throwable ex) {        if (!handleException(ex) && mDefaultHandler != null) {            // 如果用户没有处理则让系统默认的异常处理器来处理              mDefaultHandler.uncaughtException(thread, ex);        } else {            try {                Thread.sleep(3000);            } catch (InterruptedException e) {  // 如果自己处理了异常，则不会弹出错误对话框，则需要手动退出app                e.printStackTrace();            }            android.os.Process.killProcess(android.os.Process.myPid());            System.exit(10);        }    }    /**     * 自定义错误处理，收集错误信息，发送错误报告等操作均在此完成      *     * @param ex     * @return true：如果处理了该异常信息,不再向上抛异常；否则返回 false      */    private boolean handleException(Throwable ex) {        if (ex == null) {            return false;        }        Log.e(TAG,"handle crash");        // 收集设备参数信息          collectDeviceInfo(mContext);        // 保存日志文件          saveCrashInfo2File(ex);        //退出程序                                                  AlarmManager mgr = (AlarmManager)mContext.getSystemService(Context.ALARM_SERVICE);        Calendar calendar = new GregorianCalendar();        calendar.add(Calendar.SECOND, 1);        mgr.set(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(),                PendingIntent.getBroadcast(mContext, 0, new Intent(mContext,                        StartAppReceiver.class), 0)); // 300MS后重启应用            android.os.Process.killProcess(android.os.Process.myPid());        return true;    }    /**     * 启动程序广播接收器     *     */    public class StartAppReceiver extends BroadcastReceiver {        @Override        public void onReceive(Context context, Intent intent) {            context.startActivity(context.getPackageManager()                    .getLaunchIntentForPackage(context.getPackageName()));        }    }    /**     * 收集设备参数信息      * @param ctx     */    public void collectDeviceInfo(Context ctx) {        try {            PackageManager pm = ctx.getPackageManager();            PackageInfo pi = pm.getPackageInfo(ctx.getPackageName(), PackageManager.GET_ACTIVITIES);            if (pi != null) {                String versionName = pi.versionName == null ? "null" : pi.versionName;                String versionCode = pi.versionCode + "";                infos.put("versionName", versionName);                infos.put("versionCode", versionCode);            }        } catch (NameNotFoundException e) {            Log.e(TAG, "an e occured when collect package i", e);        }        Field[] fields = Build.class.getDeclaredFields();        for (Field field : fields) {            try {                field.setAccessible(true);                infos.put(field.getName(), field.get(null).toString());                Log.d(TAG, field.getName() + " : " + field.get(null));            } catch (Exception e) {                Log.e(TAG, "an e occured when collect crash i", e);            }        }    }    /**     * 保存错误信息到文件中      *     * @param ex     * @return  返回文件名称,便于将文件传送到服务器     */    private String saveCrashInfo2File(Throwable ex) {        StringBuffer sb = new StringBuffer();        for (Map.Entry<String, String> entry : infos.entrySet()) {            String key = entry.getKey();            String value = entry.getValue();            sb.append(key + "=" + value + "\n");        }        Writer writer = new StringWriter();        PrintWriter printWriter = new PrintWriter(writer);        ex.printStackTrace(printWriter);        Throwable cause = ex.getCause();        while (cause != null) {            cause.printStackTrace(printWriter);            cause = cause.getCause();        }        printWriter.close();        String result = writer.toString();        sb.append(result);        try {            long timestamp = System.currentTimeMillis();            String time = formatter.format(new Date());            String fileName = "crash-" + time + "-" + timestamp + ".log";            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {                String path = CRACHPATH;                File dir = new File(path);                if (!dir.exists()) {                    dir.mkdirs();                }                FileOutputStream fos = new FileOutputStream(path + fileName);                fos.write(sb.toString().getBytes());                fos.close();            }            return fileName;        } catch (Exception e) {            Log.e(TAG, "an e occured while writing file...", e);        }        return null;    }}