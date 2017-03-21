package com.github.mjdev.libaums.fs;

import android.hardware.usb.UsbDeviceConnection;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;

/**
 * Created by magnusja on 13/12/16.
 */

public class UsbFileStreamFactory {
    private UsbFileStreamFactory() {}

    public static BufferedOutputStream createBufferedOutputStream(UsbFile file, FileSystem fs) {
        return new BufferedOutputStream(new UsbFileOutputStream(file), fs.getChunkSize());
    }

    public static BufferedInputStream createBufferedInputStream(UsbFile file, FileSystem fs) {
        return new BufferedInputStream(new UsbFileInputStream(file), fs.getChunkSize());
    }

    public static BufferedOutputStream createBufferedOutputStream(UsbDeviceConnection deviceConnection, UsbFile file, FileSystem fs) {
        return new BufferedOutputStream(new UsbFileOutputStream(deviceConnection, file), fs.getChunkSize());
    }

    public static BufferedInputStream createBufferedInputStream(UsbDeviceConnection deviceConnection, UsbFile file, FileSystem fs) {
        return new BufferedInputStream(new UsbFileInputStream(deviceConnection, file), fs.getChunkSize());
    }
}
