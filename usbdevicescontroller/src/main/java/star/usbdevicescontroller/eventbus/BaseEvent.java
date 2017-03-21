package star.usbdevicescontroller.eventbus;

/**
 * Created by Star on 2016/8/8.
 */
public class BaseEvent {
    // Event Tag，所有事件都有唯一一个Event Tag，而且必须指定事件的Tag，EventBusPoster通过Tag判断要Post什么类型的对象
    public static final String EVENT_TAG_MAPSTARTWRITE = "EVENT_TAG_MAPSTARTWRITE"; // 启动写经纬度日志服务
    public static final String EVENT_TAG_MARKVIDEO = "EVENT_TAG_MARKVIDEO"; // 录像过程中发生标记该录像文件请求
    public static final String EVENT_TAG_KEYPRESS = "EVENT_TAG_KEYPRESS";
    public static final String EVENT_TAG_BACKBTNPRESS = "EVENT_TAG_BACKBTNPRESS"; // 标题栏上的返回按钮被按下
    public static final String EVENT_TAG_MAPSTOPWRITE = "EVENT_TAG_MAPSTOPWRITE";
    public static final String EVENT_TAG_SCREENONOFF = "EVENT_TAG_SCREENONOFF";

    public static final String EVENT_TAG_ONLINELIST = "EVENT_TAG_ONLINELIST";
    public static final String EVENT_TAG_RECEIVEMESSAGE = "EVENT_TAG_RECEIVEMESSAGE";
    public static final String EVENT_TAG_SENDMESSAGE = "EVENT_TAG_SENDMESSAGE";
    public static final String EVENT_TAG_USBEJECT = "EVENT_TAG_USBEJECT";
    public static final String EVENT_TAG_REFRESHMESSAGE = "EVENT_TAG_REFRESHMESSAGE";

    public static final String EVENT_TAG_USERREGISTER = "EVENT_TAG_USERREGISTER";
    public static final String EVENT_TAG_REFRESHPHONEINFO = "EVENT_TAG_REFRESHPHONEINFO";

    public static final String EVENT_TAG_LANGUAGE = "EVENT_TAG_LANGUAGE";

    private String eventName; // 预留字段

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }
}
