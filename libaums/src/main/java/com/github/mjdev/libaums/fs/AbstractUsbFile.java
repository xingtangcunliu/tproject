package com.github.mjdev.libaums.fs;

import android.util.Log;

import com.github.mjdev.libaums.CustomLog;

import java.io.IOException;

/**
 * Created by magnusja on 3/1/17.
 */

public abstract class AbstractUsbFile implements UsbFile {
    private static final String TAG = AbstractUsbFile.class.getSimpleName();

    @Override
    public UsbFile search(String path) throws IOException {
        CustomLog.d(TAG, "search file: " + path);

        int index = path.indexOf(UsbFile.separator);

        if(index < 0) {
            CustomLog.d(TAG, "search entry: " + path);

            UsbFile file = searchThis(path);
            return file;
        } else {
            String subPath = path.substring(index + 1);
            String dirName = path.substring(0, index);
            CustomLog.d(TAG, "search recursively " + subPath + " in " + dirName);

            UsbFile file = searchThis(dirName);
            if(file != null && file.isDirectory()) {
                CustomLog.d(TAG, "found directory " + dirName);
                return file.search(subPath);
            }
        }
        CustomLog.d(TAG, "not found " + path);

        return null;
    }

    private UsbFile searchThis(String name) throws IOException {
        for(UsbFile file: listFiles()) {
            if(file.getName().equals(name))
                return file;
        }

        return null;
    }
}
