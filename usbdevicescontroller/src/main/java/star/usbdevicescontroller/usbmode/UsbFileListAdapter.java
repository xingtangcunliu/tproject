package star.usbdevicescontroller.usbmode;

import android.content.Context;
import android.hardware.usb.UsbDeviceConnection;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.github.mjdev.libaums.fs.UsbFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import star.usbdevicescontroller.R;

public class UsbFileListAdapter extends ArrayAdapter<UsbFile> {

    private List<UsbFile> usbFiles;
    private UsbFile currentDir;
    private LayoutInflater inflater;
    private Context mContext;
    private UsbDeviceConnection usbDeviceConnection;

    // 对UsbFile中的UsbFile进行比较，把文件夹放在列表的前面
    private Comparator<UsbFile> comparator = new Comparator<UsbFile>() {

        @Override
        public int compare(UsbFile lhs, UsbFile rhs) {

            if (lhs.isDirectory() && !rhs.isDirectory()) {
                return -1;
            }

            if (rhs.isDirectory() && !lhs.isDirectory()) {
                return 1;
            }

            return lhs.getName().compareToIgnoreCase(rhs.getName());
        }
    };

    public UsbFileListAdapter(Context context) {
        super(context, R.layout.item_activity_usb_list);
        mContext = context;
    }

    public UsbFileListAdapter(UsbDeviceConnection usbDeviceConnection, Context context, UsbFile dir) throws IOException {
        super(context, R.layout.item_activity_usb_list);
        this.currentDir = dir;
        usbFiles = new ArrayList();
        this.usbDeviceConnection = usbDeviceConnection;

        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        refresh(); // ------> 1
    }

    // 读取海当前目录的文件夹及文件信息，并更新列表
    public void refresh() throws IOException {

//        usbFiles = Arrays.asList(currentDir.listFiles()); // ------> 1
        usbFiles = Arrays.asList(currentDir.listFiles(usbDeviceConnection)); // ------> 1
        Collections.sort(usbFiles, comparator);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return usbFiles.size();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view;
        if (convertView != null) {
            view = convertView;
        } else {
            view = inflater.inflate(R.layout.item_activity_usb_list, parent, false);
        }

        TextView typeText = (TextView) view.findViewById(R.id.item_activity_usb_list_tvtype);
        TextView nameText = (TextView) view.findViewById(R.id.item_activity_usb_list_tvname);
        UsbFile file = usbFiles.get(position);
        if (file.isDirectory()) {
            typeText.setText(R.string.directory);
        } else {
            typeText.setText(R.string.file);
        }
        nameText.setText(file.getName());

        return view;
    }

    @Override
    public UsbFile getItem(int position) {
        return usbFiles.get(position);
    }

    // 获取当前正在显示的目录
    public UsbFile getCurrentDir() {
        return currentDir;
    }

}