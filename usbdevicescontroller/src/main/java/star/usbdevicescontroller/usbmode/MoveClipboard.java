package star.usbdevicescontroller.usbmode;


import com.github.mjdev.libaums.fs.UsbFile;

public class MoveClipboard {

    private static MoveClipboard instance;
    private UsbFile file;

    private MoveClipboard() {

    }

    /**
     *
     * @return The global used instance.
     */
    public static synchronized MoveClipboard getInstance() {
        if (instance == null)
            instance = new MoveClipboard();

        return instance;
    }

    /**
     *
     * @return The file saved in the clipboard.
     */
    public synchronized UsbFile getFile() {
        return file;
    }

    /**
     * Sets the file in the clipboard.
     *
     * @param file
     *            The file which shall be moved.
     */
    public synchronized void setFile(UsbFile file) {
        this.file = file;
    }

}
