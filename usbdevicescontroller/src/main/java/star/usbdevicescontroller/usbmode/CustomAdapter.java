package star.usbdevicescontroller.usbmode;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.github.mjdev.libaums.UsbMassStorageDevice;

import java.util.ArrayList;
import java.util.List;

import star.usbdevicescontroller.R;

public class CustomAdapter extends RecyclerView.Adapter<CustomAdapter.MyViewHolder> {

    private static int counter = 1;
    private ArrayList<UsbMassStorageDevice> storageDevices;

    public CustomAdapter() {
        storageDevices = new ArrayList<>();
    }

    public static class MyViewHolder extends RecyclerView.ViewHolder {

        Button button;

        public MyViewHolder(View itemView) {
            super(itemView);
            this.button = (Button) itemView.findViewById(R.id.iadl_devicename);

//            itemView.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View v) {
//
//                }
//            });
        }
    }

    public void setStorageDevices(List<UsbMassStorageDevice> devices) {
        storageDevices.clear();
        for(int i=0; i<devices.size(); i++) {
//            if (devices.get(i).getPartitions().size() > 0) {
                this.storageDevices.add(devices.get(i));
//            }
        }
        notifyDataSetChanged();
    }

    @Override
    public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_activity_device_list, parent, false);

        MyViewHolder myViewHolder = new MyViewHolder(view);
        return myViewHolder;
    }

    @Override
    public void onBindViewHolder(final MyViewHolder holder, final int position) {

        Button button = holder.button;
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(listener != null) {
                    listener.onClick(storageDevices.get(position), position);
                }
            }
        });

//        UsbMassStorageDevice device = storageDevices.get(position);
//        List<Partition> partitions = device.getPartitions();

//        if(partitions.size() > 0) {
//            Partition partition = partitions.get(0);
//            FileSystem currentFs = partition.getFileSystem();
//            button.setText("Device:" + currentFs.getVolumeLabel());
//        } else {
            button.setText("Device:" + counter++);
//        }
    }

    @Override
    public int getItemCount() {
        return storageDevices.size();
    }

    private OnItemClickListener listener;
    public void setListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public interface OnItemClickListener {
        void onClick(UsbMassStorageDevice device, int position);
    }
}
