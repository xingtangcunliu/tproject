package star.usbdevicescontroller.usbmode;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.provider.OpenableColumns;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.magnusja.libaums.javafs.JavaFsFileSystemCreator;
import com.github.mjdev.libaums.CustomLog;
import com.github.mjdev.libaums.UsbMassStorageDevice;
import com.github.mjdev.libaums.fs.FileSystem;
import com.github.mjdev.libaums.fs.FileSystemFactory;
import com.github.mjdev.libaums.fs.UsbFile;
import com.github.mjdev.libaums.fs.UsbFileStreamFactory;
import com.github.mjdev.libaums.partition.Partition;
import com.github.mjdev.libaums.server.http.UsbFileHttpServerService;
import com.github.mjdev.libaums.server.http.server.AsyncHttpServer;
import com.github.mjdev.libaums.usb.UsbCommunicationFactory;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Timer;
import java.util.TimerTask;

import star.usbdevicescontroller.R;

public class UsbActivity extends AppCompatActivity {
    private static final String TAG = UsbActivity.class.getSimpleName();

    // 请求与UsbDevice通信权限的字符串Action string
    private static final String ACTION_USB_PERMISSION = "ACTION_USB_PERMISSION";
    private static final int COPY_STORAGE_PROVIDER_RESULT = 0;
    private static final int OPEN_STORAGE_PROVIDER_RESULT = 1;
    private static final int REQUEST_EXT_STORAGE_WRITE_PERM = 0;

    static {
//        UsbCommunicationFactory.setUnderlyingUsbCommunication(UsbCommunicationFactory.UnderlyingUsbCommunication.USB_REQUEST_ASYNC);
        FileSystemFactory.registerFileSystem(new JavaFsFileSystemCreator());
    }

    // TODO these are devices
    private List<UsbMassStorageDevice> storageDevices;
    private FileSystem currentFs;

    private ListView listView;
    private UsbFileListAdapter usbFileListAdapter; // package

    private TextView textView, textViewSpeed;
    private RecyclerView recyclerView;
    private CustomAdapter customAdapter;

    private Deque<UsbFile> dirs = new ArrayDeque();
    private Intent serviceIntent = null;
    private UsbFileHttpServerService serverService;
    private BroadcastReceiver usbReceiver;
    private ServiceConnection serviceConnection;
    private AdapterView.OnItemClickListener onItemClickListener;

    private UsbDeviceConnection curDevConnection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initBrReceiverSerConItemClickedListener();

        storageDevices = new ArrayList<>();
        serviceIntent = new Intent(this, UsbFileHttpServerService.class);
        setContentView(R.layout.activity_usb);
        listView = (ListView) findViewById(R.id.activity_usb_listview);

        textView = (TextView) findViewById(R.id.activity_usb_devicename);
        textViewSpeed = (TextView) findViewById(R.id.activity_usb_speed);

        recyclerView = (RecyclerView) findViewById(R.id.activity_usb_recview);
        recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));

        customAdapter = new CustomAdapter();
        customAdapter.setListener(new CustomAdapter.OnItemClickListener() {
            @Override
            public void onClick(UsbMassStorageDevice device, int position) {
                textView.setText("Select Device " + position);
                curDevConnection = device.getDeviceConnection();

                FileSystem fileSystem = device.getPartitions().get(0).getFileSystem();
                UsbFile usbFile = fileSystem.getRootDirectory();
                currentFs = device.getPartitions().get(0).getFileSystem();

                ActionBar actionBar = getSupportActionBar();
                if(actionBar != null) {
                    actionBar.setTitle(fileSystem.getVolumeLabel());
                }

                try {
                    listView.setAdapter(usbFileListAdapter = new UsbFileListAdapter(device.getDeviceConnection(), UsbActivity.this, usbFile));
                } catch (IOException e) {
                    textView.setText("reSelect fail");
                    e.printStackTrace();
                }
            }
        });
        recyclerView.setAdapter(customAdapter);

        listView.setOnItemClickListener(onItemClickListener);
        registerForContextMenu(listView);

        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usbReceiver, filter);

        discoverDevice();
    }

    /**
     * 查找是否有MassStorage设备接入，如果有则初始化它
     */
    private void discoverDevice() {
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        UsbMassStorageDevice[] devices = UsbMassStorageDevice.getMassStorageDevices(this);

        if (devices.length == 0) {
            ActionBar actionBar = getSupportActionBar();
            if(actionBar != null) {
                actionBar.setTitle(R.string.usb_no_devices);
            }
            listView.setAdapter(null);
            return;
        }

        // List<UsbMassStorageDevice> storageDevices;
        // add all founded usb device to the recycler view
        for(int i=0; i<devices.length; i++) {
//            if(i == 1) continue;
            storageDevices.add(devices[i]);
        }
        UsbDevice usbDevice = getIntent().getParcelableExtra(UsbManager.EXTRA_DEVICE);

        if (usbDevice != null && usbManager.hasPermission(usbDevice)) {
            setupDevice();
        } else {
            // 先请求操作权限
            for(int i=0; i< storageDevices.size(); i++) {
                PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
                usbManager.requestPermission(storageDevices.get(i).getUsbDevice(), permissionIntent);
            }
        }
    }

    /**
     * 初始化设备，并显示其根目录的文件
     */
    private void setupDevice() {
        try {
            long sTime = System.currentTimeMillis();
            for(int i=0; i<storageDevices.size(); i++) {
                storageDevices.get(i).init();
            }

            long eTime = System.currentTimeMillis();
            textViewSpeed.setText("Init all devices, devices count = " + storageDevices.size() + ", use time " + (eTime - sTime) + "ms");

            UsbMassStorageDevice curUsbDevice = storageDevices.get(0);
            currentFs = curUsbDevice.getPartitions().get(0).getFileSystem();
            customAdapter.setStorageDevices(storageDevices);
            curDevConnection = curUsbDevice.getDeviceConnection();

            // we always use the first partition of the usbMassStorageDevice0
            List<Partition> partitions = curUsbDevice.getPartitions();
            Partition partition = partitions.get(0);

//            FileSystem fileSystem = curUsbDevice.getPartitions().get(0).getFileSystem();
            FileSystem fileSystem = partition.getFileSystem(); // Fat32FileSystem, it contain FatDirectory and FAT
            UsbFile root = fileSystem.getRootDirectory(); // FatDirectory contain FAT

            ActionBar actionBar = getSupportActionBar();
            if(actionBar != null) {
                actionBar.setTitle(fileSystem.getVolumeLabel());
            }

            listView.setAdapter(usbFileListAdapter = new UsbFileListAdapter(curUsbDevice.getDeviceConnection(), this, root));
        } catch (IOException e) {
            Log.e("lion", "------------------------> setUpDevice fail");
            Log.e(TAG, "e setting up usbMassStorageDevice", e);
        }
    }

    private void initBrReceiverSerConItemClickedListener() {
        onItemClickListener = new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long rowId) {
                UsbFile entry = usbFileListAdapter.getItem(position);
                try {
                if (entry.isDirectory()) {

                    dirs.push(usbFileListAdapter.getCurrentDir());
                    listView.setAdapter(usbFileListAdapter = new UsbFileListAdapter(curDevConnection, UsbActivity.this, entry)); // TODO multiple devices cause IOException

                } else {

                    if (ContextCompat.checkSelfPermission(UsbActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

                        if (ActivityCompat.shouldShowRequestPermissionRationale(UsbActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                            Toast.makeText(UsbActivity.this, R.string.request_write_storage_perm, Toast.LENGTH_LONG).show();
                        } else {
                            ActivityCompat.requestPermissions(UsbActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_EXT_STORAGE_WRITE_PERM);
                        }
                        return;
                    }

                    CopyTaskParam copyTaskParam = new CopyTaskParam();
                    copyTaskParam.from = entry;

                    // ------ TODO set the file path at here
                    File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/usbfileman/cache");

                    boolean ret = file.mkdirs();
                    int index = entry.getName().lastIndexOf(".") > 0 ? entry.getName().lastIndexOf(".") : entry.getName().length();
                    String prefix = entry.getName().substring(0, index);
                    String ext = entry.getName().substring(index);
                    // prefix must be at least 3 characters
                    if(prefix.length() < 3) {
                        prefix += "pad";
                    }
                        copyTaskParam.to = File.createTempFile(prefix, ext, file);
                        new CopyTask().execute(copyTaskParam);
                }
                }
                catch (IOException e) {
                    Log.e(TAG, "e staring to copy!", e);
                }
            }
        };

        usbReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                String action = intent.getAction();
                if (ACTION_USB_PERMISSION.equals(action)) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {

                        if (device != null) {
                            setupDevice();
                        }
                    }
                } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    // 检查接入的设备是否是MassStorage设备
                    if (device != null) {
                        discoverDevice();
                    }
                } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (device != null) {
                        for(int i=0; i<storageDevices.size(); i++) {
                            storageDevices.get(i).close();
                        }
                        // 检查是否有其它设备
                        discoverDevice();
                    }
                }
            }
        };

        serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.d(TAG, "on service connected " + name);
                UsbFileHttpServerService.ServiceBinder binder = (UsbFileHttpServerService.ServiceBinder) service;
                serverService = binder.getService();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.d(TAG, "on service disconnected " + name);
                serverService = null;
            }
        };
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.usb_main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MoveClipboard cl = MoveClipboard.getInstance();
        menu.findItem(R.id.paste).setEnabled(cl.getFile() != null);
        menu.findItem(R.id.stop_http_server).setEnabled(serverService != null && serverService.isServerRunning());
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.create_file:
                new NewFileDialog().show(getFragmentManager(), getString(R.string.usb_new_file));
                return true;
            case R.id.create_dir:
                new NewDirDialog().show(getFragmentManager(), getString(R.string.usb_new_directory));
                return true;
            case R.id.create_big_file:
                createBigFile();
                return true;
            case R.id.paste:
                move();
                return true;
            case R.id.stop_http_server:
                if(serverService != null) {
                    serverService.stopServer();
                }
                return true;
            case R.id.run_tests:
                startActivity(new Intent(this, LibAumsTest.class));
                return true;
            case R.id.open_storage_provider:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    for(int i=0; i<storageDevices.size(); i++) {
                        storageDevices.get(i).close();
                    }

                    Intent intent = new Intent();
                    intent.setAction(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    String[] extraMimeTypes = {"image/*", "video/*"};
                    intent.putExtra(Intent.EXTRA_MIME_TYPES, extraMimeTypes);
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    startActivityForResult(intent, OPEN_STORAGE_PROVIDER_RESULT);
                }
                return true;
            case R.id.copy_from_storage_provider:

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    Intent intent = new Intent();
                    intent.setAction(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");

                    startActivityForResult(intent, COPY_STORAGE_PROVIDER_RESULT);
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }

    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.usb_context, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        final UsbFile entry = usbFileListAdapter.getItem((int) info.id);
        switch (item.getItemId()) {
            case R.id.delete_item:
                try {
                    entry.delete();
                    usbFileListAdapter.refresh();
                } catch (IOException e) {
                    Log.e(TAG, "e deleting!", e);
                }
                return true;
            case R.id.rename_item:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.usb_rename);
                builder.setMessage(getString(R.string.usb_enter_filename));
                final EditText input = new EditText(this);
                input.setText(entry.getName());
                builder.setView(input);

                builder.setPositiveButton(R.string.usb_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        try {
                            entry.setName(input.getText().toString());
                            usbFileListAdapter.refresh();
                        } catch (IOException e) {
                            Log.e(TAG, "e renaming!", e);
                        }
                    }

                });

                builder.setNegativeButton(R.string.usb_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        dialog.dismiss();
                    }
                });
                builder.setCancelable(false);
                builder.create().show();
                return true;
            case R.id.move_item:
                MoveClipboard cl = MoveClipboard.getInstance();
                cl.setFile(entry);
                return true;
            case R.id.start_http_server:
                startHttpServer(entry);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    private void startHttpServer(final UsbFile file) {

        if(serverService == null) {
            Toast.makeText(UsbActivity.this, "serverService == null!", Toast.LENGTH_LONG).show();
            return;
        }

        if(serverService.isServerRunning()) {
            Log.d(TAG, "Stopping existing server service");
            serverService.stopServer();
        }

        // 启动server
        try {
            serverService.startServer(file, new AsyncHttpServer(8000));
            Toast.makeText(UsbActivity.this, "HTTP server up and running", Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Log.e(TAG, "Error starting HTTP server", e);
            Toast.makeText(UsbActivity.this, "Could not start HTTP server", Toast.LENGTH_LONG).show();
        }

        if(file.isDirectory()) {
            // 只有是文件的时候才进行后面的操作
            return;
        }

        Intent myIntent = new Intent(Intent.ACTION_VIEW);
        myIntent.setData(Uri.parse(serverService.getServer().getBaseUrl() + file.getName()));
        try {
            startActivity(myIntent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(UsbActivity.this, R.string.usb_cannot_openfile, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_EXT_STORAGE_WRITE_PERM: {
                // 如果请求已撤销，那么Result Arrays就为空
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    Toast.makeText(this, R.string.permission_granted, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_LONG).show();
                }
            } // REQUEST_EXT_STORAGE_WRITE_PERM
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != Activity.RESULT_OK) {
            Log.w(TAG, "Activity result is not ok");
            return;
        }

        if (requestCode == OPEN_STORAGE_PROVIDER_RESULT) {
            Uri uri;
            if (data != null) {
                uri = data.getData();
                Log.i(TAG, "Uri: " + uri.toString());
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(uri);
                startActivity(i);
            }
        } else if (requestCode == COPY_STORAGE_PROVIDER_RESULT){

        }
    }

    /**
     * 此函数仅供测试，创建大文件
     */
    private void createBigFile() {

        UsbFile dir = usbFileListAdapter.getCurrentDir();
        UsbFile file;
        try {
            file = dir.createFile("big_file_test" + System.currentTimeMillis() + ".txt");
            file.setLength(1024 * 1024 * 512);
            usbFileListAdapter.refresh();
        } catch (IOException e) {
            Log.e(TAG, "e creating big file!", e);
        }
    }

    /**
     * 把存放在MoveClipboard中的文件移动到当前目录
     */
    private void move() {
        MoveClipboard cl = MoveClipboard.getInstance();
        UsbFile file = cl.getFile();
        try {
            long startTime = System.currentTimeMillis();
//            file.moveTo(usbFileListAdapter.getCurrentDir());
            file.moveTo(curDevConnection, usbFileListAdapter.getCurrentDir());
            long endTime = System.currentTimeMillis();
            long totalTime = endTime - startTime;
            textViewSpeed.setText("Total Time = " + totalTime + ", fileLen = " + file.getLength());
            usbFileListAdapter.refresh();
        } catch (IOException e) {
            textViewSpeed.setText("Move IOException");
            Log.e(TAG, "e moving!", e);
        }
        cl.setFile(null);
    }

    @Override
    protected void onStart() {
        super.onStart();

        startService(serviceIntent);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();

        unbindService(serviceConnection);
    }

    @Override
    public void onBackPressed() {
        try {
            UsbFile dir = dirs.pop();
            listView.setAdapter(usbFileListAdapter = new UsbFileListAdapter(curDevConnection, this, dir));
        } catch (NoSuchElementException e) {
            super.onBackPressed();
        } catch (IOException e) {
            Log.e(TAG, "e initializing usbFileListAdapter!", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(usbReceiver);

        if(!serverService.isServerRunning()) {
            Log.d(TAG, "Stopping service");
            stopService(serviceIntent);

            for(int i=0; i<storageDevices.size(); i++) {
                storageDevices.get(i).close();
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 用来存放要在Task进行Copy的文件
    private static class CopyTaskParam {
        /* package */UsbFile from;
        /* package */File to;
    }

    public static class NewDirDialog extends DialogFragment {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final UsbActivity activity = (UsbActivity) getActivity();
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(R.string.usb_new_directory);
            builder.setMessage(R.string.usb_enter_dirname);
            final EditText input = new EditText(activity);
            builder.setView(input);

            builder.setPositiveButton(R.string.usb_cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int whichButton) {

                    UsbFile dir = activity.usbFileListAdapter.getCurrentDir();
                    try {
                        dir.createDirectory(input.getText().toString());
                        activity.usbFileListAdapter.refresh();
                    } catch (Exception e) {
                        Log.e(TAG, "e creating dir!", e);
                    }
                }
            });

            builder.setNegativeButton(R.string.usb_cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int whichButton) {
                    dialog.dismiss();
                }
            });

            builder.setCancelable(false);
            return builder.create();
        }
    }

    public static class NewFileDialog extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final UsbActivity activity = (UsbActivity) getActivity();
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(R.string.usb_new_file);
            builder.setMessage(R.string.usb_enter_filename);
            final EditText input = new EditText(activity);
            final EditText content = new EditText(activity);
            LinearLayout layout = new LinearLayout(activity);
            layout.setOrientation(LinearLayout.VERTICAL);
            TextView textView = new TextView(activity);
            textView.setText(R.string.name);
            layout.addView(textView);
            layout.addView(input);
            textView = new TextView(activity);
            textView.setText(R.string.content);
            layout.addView(textView);
            layout.addView(content);

            builder.setView(layout);

            builder.setPositiveButton(R.string.usb_ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int whichButton) {

                    UsbFile dir = activity.usbFileListAdapter.getCurrentDir();
                    try {
                        UsbFile file = dir.createFile(input.getText().toString());
                        file.write(0, ByteBuffer.wrap(content.getText().toString().getBytes()));
                        file.close();
                        activity.usbFileListAdapter.refresh();
                    } catch (Exception e) {
                        Log.e(TAG, "e creating file!", e);
                    }
                }
            });

            builder.setNegativeButton(R.string.usb_cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int whichButton) {
                    dialog.dismiss();
                }
            });

            builder.setCancelable(false);
            return builder.create();
        }
    }

    /**
     * 在Task中，通过USB从MassStorage设备拷贝文件到本机内部存储
     */
    private final int[] calculate = {0};
    private int preCount = 0, speed = 0;

    private class CopyTask extends AsyncTask<CopyTaskParam, Integer, Void> {

        private Timer timer = new Timer();
        private ProgressDialog dialog;
        private CopyTaskParam param;

        public CopyTask() {
            dialog = new ProgressDialog(UsbActivity.this);
            dialog.setTitle(R.string.usb_copy_file);
            dialog.setMessage(getString(R.string.usb_copy_file_hint));
            dialog.setIndeterminate(false);
            dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        }

        @Override
        protected void onPreExecute() {
            dialog.show();
        }

        @Override
        protected Void doInBackground(CopyTaskParam... params) {
            final long time = System.currentTimeMillis();
            final int total1[] = {0};
            param = params[0];
            try {
                OutputStream out = new BufferedOutputStream(new FileOutputStream(param.to));
//                InputStream inputStream = UsbFileStreamFactory.createBufferedInputStream(param.from, currentFs);
                InputStream inputStream = UsbFileStreamFactory.createBufferedInputStream(curDevConnection, param.from, currentFs);

                byte[] bytes = new byte[1024 * 100];
                int count;
                int total = 0;

                timer.scheduleAtFixedRate(
                    new TimerTask() {
                        @Override
                        public void run() {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    dialog.setMessage("Current Speed = " + ((calculate[0] - preCount) / 1024 / 1024) + "Mb");
                                    preCount = calculate[0];
                                }
                            });
                        }
                    },
                    0,
                    1000);

                long startTime = System.currentTimeMillis();
                while ((count = inputStream.read(bytes)) != -1) {
                    out.write(bytes, 0, count);
                    total += count;
                    calculate[0] += count;

                    publishProgress((int) total);
                }
                total1[0] = total;

                out.close();
                inputStream.close();
            } catch (IOException e) {
                CustomLog.e("lion", "-------------------------> copy error");
                e.printStackTrace();

                Log.e(TAG, "e copying!", e);
            }
            Log.d(TAG, "copy time: " + (System.currentTimeMillis() - time));

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
//			dialog.dismiss();

            Intent myIntent = new Intent(Intent.ACTION_VIEW);
            File file = new File(param.to.getAbsolutePath());
            String extension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(Uri.fromFile(file).toString());
            String mimetype = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);

            Uri uri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                myIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                uri = FileProvider.getUriForFile(UsbActivity.this, UsbActivity.this.getApplicationContext().getPackageName() + ".provider", file);
            } else {
                uri = Uri.fromFile(file);
            }
            myIntent.setDataAndType(uri, mimetype);
            try {
                startActivity(myIntent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(UsbActivity.this, R.string.usb_cannot_openfile, Toast.LENGTH_LONG).show();
            }

            timer.cancel();
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            dialog.setMax((int) param.from.getLength());
            dialog.setProgress(values[0]);
        }
    }
}
