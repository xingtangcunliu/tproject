package star.usbdevicescontroller.eventbus;

import org.greenrobot.eventbus.EventBus;

/**
 * Created by Star on 2016/8/10.
 */
public class EventBusPoster {

    /**
     * Post普通事件，普通事件是指事件的消息类型一样的事件类型，如USB通信的事件，
     * 这个方法也主要为了USB通信事件准备的
     */
    public static void postNormalEvent(EventBody eventBody) {
        BaseEvent event = createEvent(eventBody);
        if(event == null) {
            return;
        }
        EventBus.getDefault().post(event);
    }

    private static BaseEvent createEvent(EventBody eventBody) {
        if(BaseEvent.EVENT_TAG_USBEJECT.equals(eventBody.getEventTag())) {
            // return new UsbEjectEvent(eventBody.getEventMsg());
        }
        return null;
    }
}
