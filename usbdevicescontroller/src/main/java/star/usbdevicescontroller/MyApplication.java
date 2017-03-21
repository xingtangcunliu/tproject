package star.usbdevicescontroller;

import android.app.Application;

import star.usbdevicescontroller.util.CrashHandler;

/**
 * Created by Star on 2017/3/7.
 */
public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        //异常处理初始化
        CrashHandler handler = CrashHandler.getInstance();
        handler.init(getApplicationContext());
    }
}
