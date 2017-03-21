/*
 * (C) Copyright 2014 mjahnen <jahnen@in.tum.de>
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

package com.github.mjdev.libaums.partition;

import java.io.IOException;
import java.nio.ByteBuffer;

import android.hardware.usb.UsbDeviceConnection;
import android.util.Log;

import com.github.mjdev.libaums.CustomLog;
import com.github.mjdev.libaums.driver.BlockDeviceDriver;
import com.github.mjdev.libaums.fs.FileSystem;
import com.github.mjdev.libaums.fs.FileSystemFactory;

/**
 * This class represents a partition on an mass storage device. A partition has
 * a certain file system which can be accessed via {@link #getFileSystem()}.
 * This file system is needed to to access the files and directories of a
 * partition.
 * <p>
 * The method {@link #getVolumeLabel()} returns the volume label for the
 * partition. Calling the method is equivalent to calling
 * {@link FileSystem#getVolumeLabel()}.
 *
 * @author mjahnen
 *
 */
public class Partition implements BlockDeviceDriver {

	private static final String TAG = Partition.class.getSimpleName();

	// private PartitionTableEntry partitionTableEntry;
	private BlockDeviceDriver blockDevice;
	private int logicalBlockAddress;
	private int blockSize;
	private FileSystem fileSystem;

	private Partition() {

	}

	public static Partition createPartition(PartitionTableEntry entry, BlockDeviceDriver blockDevice) throws IOException {
		Partition partition = null;

		partition = new Partition();
		partition.logicalBlockAddress = entry.getLogicalBlockAddress();
		partition.blockDevice = blockDevice;
		partition.blockSize = blockDevice.getBlockSize();
		try {
			partition.fileSystem = FileSystemFactory.createFileSystem(entry, partition);
		} catch (FileSystemFactory.UnsupportedFileSystemException e) {
			CustomLog.w(TAG, "Unsupported fs on partition");
		}

		return (partition.fileSystem != null ? partition : null);
	}

	public FileSystem getFileSystem() {
		return fileSystem;
	}

	public String getVolumeLabel() {
		return fileSystem.getVolumeLabel();
	}

	@Override
	public void init() {

	}

	@Override
	public void read(long offset, ByteBuffer dest) throws IOException {
		long devOffset = offset / blockSize + logicalBlockAddress;
		// TODO try to make this more efficient by for example making tmp buffer global
		if (offset % blockSize != 0) {
			//CustomLog.w(TAG, "device offset " + offset + " not a multiple of block size");
			ByteBuffer tmp = ByteBuffer.allocate(blockSize);

			blockDevice.read(devOffset, tmp);
			tmp.clear();
			tmp.position((int) (offset % blockSize));
			int limit = Math.min(dest.remaining(), tmp.remaining());
			tmp.limit(tmp.position() + limit);
			dest.put(tmp);

			devOffset++;
		}

		if (dest.remaining() > 0) {
			ByteBuffer buffer;
			if (dest.remaining() % blockSize != 0) {
				//CustomLog.w(TAG, "we have to round up size to next block sector");
				int rounded = blockSize - dest.remaining() % blockSize + dest.remaining();
				buffer = ByteBuffer.allocate(rounded);
				buffer.limit(rounded);
			} else {
				buffer = dest;
			}

			blockDevice.read(devOffset, buffer); // ------> 7

			if (dest.remaining() % blockSize != 0) {
                System.arraycopy(buffer.array(), 0, dest.array(), dest.position(), dest.remaining());
			}
		}
	}

    @Override
    public void read(UsbDeviceConnection usbDeviceConnection, long offset, ByteBuffer dest) throws IOException {
        long devOffset = offset / blockSize + logicalBlockAddress;
        // TODO try to make this more efficient by for example making tmp buffer global
        if (offset % blockSize != 0) {
            //CustomLog.w(TAG, "device offset " + offset + " not a multiple of block size");
            ByteBuffer tmp = ByteBuffer.allocate(blockSize);

            blockDevice.read(usbDeviceConnection, devOffset, tmp);
            tmp.clear();
            tmp.position((int) (offset % blockSize));
            int limit = Math.min(dest.remaining(), tmp.remaining());
            tmp.limit(tmp.position() + limit);
            dest.put(tmp);

            devOffset++;
        }

        if (dest.remaining() > 0) {
            ByteBuffer buffer;
            if (dest.remaining() % blockSize != 0) {
                //CustomLog.w(TAG, "we have to round up size to next block sector");
                int rounded = blockSize - dest.remaining() % blockSize + dest.remaining();
                buffer = ByteBuffer.allocate(rounded);
                buffer.limit(rounded);
            } else {
                buffer = dest;
            }

            blockDevice.read(usbDeviceConnection, devOffset, buffer); // ------> 7

            if (dest.remaining() % blockSize != 0) {
                System.arraycopy(buffer.array(), 0, dest.array(), dest.position(), dest.remaining());
            }
        }
    }

	@Override
	public void write(long offset, ByteBuffer src) throws IOException {
		long devOffset = offset / blockSize + logicalBlockAddress;
		// TODO try to make this more efficient by for example making tmp buffer global
		if (offset % blockSize != 0) {
			//CustomLog.w(TAG, "device offset " + offset + " not a multiple of block size");
			ByteBuffer tmp = ByteBuffer.allocate(blockSize);

			blockDevice.read(devOffset, tmp);
			tmp.clear();
			tmp.position((int) (offset % blockSize));
			int remaining = Math.min(tmp.remaining(), src.remaining());
			tmp.put(src.array(), src.position(), remaining);
			src.position(src.position() + remaining);
			tmp.clear();
			blockDevice.write(devOffset, tmp);

			devOffset++;
		}

		if (src.remaining() > 0) {
            // TODO try to make this more efficient by for example only allocating blockSize and making it global
            ByteBuffer buffer;
            if (src.remaining() % blockSize != 0) {
                //CustomLog.w(TAG, "we have to round up size to next block sector");
                int rounded = blockSize - src.remaining() % blockSize + src.remaining();
                buffer = ByteBuffer.allocate(rounded);
                buffer.limit(rounded);

                System.arraycopy(src.array(), src.position(), buffer.array(), 0, src.remaining());
            } else {
                buffer = src;
            }

            blockDevice.write(devOffset, buffer);
        }
	}

	@Override
	public void write(UsbDeviceConnection usbDeviceConnection, long offset, ByteBuffer src) throws IOException {
		long devOffset = offset / blockSize + logicalBlockAddress;
		// TODO try to make this more efficient by for example making tmp buffer global
		if (offset % blockSize != 0) {
			//CustomLog.w(TAG, "device offset " + offset + " not a multiple of block size");
			ByteBuffer tmp = ByteBuffer.allocate(blockSize);

			blockDevice.read(devOffset, tmp);
			tmp.clear();
			tmp.position((int) (offset % blockSize));
			int remaining = Math.min(tmp.remaining(), src.remaining());
			tmp.put(src.array(), src.position(), remaining);
			src.position(src.position() + remaining);
			tmp.clear();
			blockDevice.write(devOffset, tmp);

			devOffset++;
		}

		if (src.remaining() > 0) {
			ByteBuffer buffer;
			if (src.remaining() % blockSize != 0) {
				//CustomLog.w(TAG, "we have to round up size to next block sector");
				int rounded = blockSize - src.remaining() % blockSize + src.remaining();
				buffer = ByteBuffer.allocate(rounded);
				buffer.limit(rounded);

				System.arraycopy(src.array(), src.position(), buffer.array(), 0, src.remaining());
			} else {
				buffer = src;
			}

			blockDevice.write(usbDeviceConnection, devOffset, buffer);
		}
	}

	@Override
	public int getBlockSize() {
		return blockDevice.getBlockSize();
	}
}
