/*
 * (C) Copyright 2016 mjahnen <jahnen@in.tum.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */


package com.github.mjdev.libaums.fs;

import android.hardware.usb.UsbDeviceConnection;
import android.support.annotation.NonNull;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * UsbFileOutputStream provides common OutputStream access to a UsbFile.
 */
public class UsbFileOutputStream extends OutputStream {

    private UsbFile file;
    private int currentByteOffset = 0;
    private UsbDeviceConnection deviceConnection;

    public UsbFileOutputStream(@NonNull UsbFile file) {

        if(file.isDirectory()) {
            throw new RuntimeException("UsbFileOutputStream cannot be created on directory!");
        }

        this.file = file;
    }

    public UsbFileOutputStream(UsbDeviceConnection deviceConnection, @NonNull UsbFile file) {

        if(file.isDirectory()) {
            throw new RuntimeException("UsbFileOutputStream cannot be created on directory!");
        }

        this.file = file;
        this.deviceConnection = deviceConnection;
    }

    @Override
    public void write(int oneByte) throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[] {(byte) oneByte});

        if(deviceConnection != null) {
            file.write(deviceConnection, currentByteOffset, byteBuffer);
        } else {
            file.write(currentByteOffset, byteBuffer);
        }
        currentByteOffset++;
    }

    @Override
    public void close() throws IOException {
        file.close();
    }

    @Override
    public void flush() throws IOException {
        file.flush();
    }

    @Override
    public void write(byte[] buffer) throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);

        if(deviceConnection != null) {
            file.write(deviceConnection, currentByteOffset, byteBuffer);
        } else {
            file.write(currentByteOffset, byteBuffer);
        }
        currentByteOffset += buffer.length;
    }

    @Override
    public void write(byte[] buffer, int offset, int count) throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
        byteBuffer.position(offset);
        byteBuffer.limit(count + offset);

        if(deviceConnection != null) {
            file.write(deviceConnection, currentByteOffset, byteBuffer);
        } else {
            file.write(currentByteOffset, byteBuffer);
        }
        currentByteOffset += count;
    }
}