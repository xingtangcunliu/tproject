package star.usbdevicescontroller.eventbus;

/**
 * Created by bingo on 2016/9/9.
 */
public class CmdReceiveEvent {
    private String msg;

    public CmdReceiveEvent(String msg) {
        this.msg = msg;
    }

    public String getMsg() {
        return msg;
    }
}
