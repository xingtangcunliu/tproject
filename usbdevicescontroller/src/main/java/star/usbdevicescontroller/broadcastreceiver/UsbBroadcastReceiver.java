package star.usbdevicescontroller.broadcastreceiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;

import star.usbdevicescontroller.eventbus.CmdReceiveEvent;

public class UsbBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String actionName = intent.getAction();

        if(actionName.equals("android.hardware.usb.action.USB_STATE")) {
            if (intent.getExtras().getBoolean("connected")){
                EventBus.getDefault().post(new CmdReceiveEvent("111111"));
//                Toast.makeText(context, "usb device inject", Toast.LENGTH_LONG).show();
            }else{
//                Toast.makeText(context, "usb device eject", Toast.LENGTH_LONG).show();
            }
        }
    }
}
