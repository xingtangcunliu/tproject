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

package com.github.mjdev.libaums.fs.fat32;

import java.io.IOException;
import java.nio.ByteBuffer;

import android.hardware.usb.UsbDeviceConnection;
import android.util.Log;

import com.github.mjdev.libaums.CustomLog;
import com.github.mjdev.libaums.driver.BlockDeviceDriver;

/**
 * This class represents a cluster chain which can be followed in the FAT of a
 * FAT32 file system. You can {@link #read(long, ByteBuffer) read} from or
 * {@link #write(long, ByteBuffer) write} to it easily without having to worry
 * about the specific clusters.
 *
 * @author mjahnen
 *
 */
public class ClusterChain {

	private static final String TAG = ClusterChain.class.getSimpleName();

	private BlockDeviceDriver blockDevice;
	private FAT fat;
	private Long[] chain;
	private long clusterSize;
	private long dataAreaOffset;

	ClusterChain(long startCluster, BlockDeviceDriver blockDevice, FAT fat, Fat32BootSector bootSector) throws IOException {
		CustomLog.d(TAG, "Init a cluster chain, reading from FAT");
		this.fat = fat;
		this.blockDevice = blockDevice;
		chain = fat.getChain(startCluster); // ------> 5
		clusterSize = bootSector.getBytesPerCluster();
		dataAreaOffset = bootSector.getDataAreaOffset();
		CustomLog.d(TAG, "Finished init of a cluster chain");
	}

	ClusterChain(UsbDeviceConnection usbDeviceConnection, long startCluster, BlockDeviceDriver blockDevice,
				 FAT fat, Fat32BootSector bootSector) throws IOException {
		CustomLog.d(TAG, "Init a cluster chain, reading from FAT");
		this.fat = fat;
		this.blockDevice = blockDevice;
		chain = fat.getChain(usbDeviceConnection, startCluster); // ------> 5
		clusterSize = bootSector.getBytesPerCluster();
		dataAreaOffset = bootSector.getDataAreaOffset();
		CustomLog.d(TAG, "Finished init of a cluster chain");
	}

	void read(long offset, ByteBuffer dest) throws IOException {
		int length = dest.remaining();

		int chainIndex = (int) (offset / clusterSize);
		if (offset % clusterSize != 0) {
			// offset in the cluster
			int clusterOffset = (int) (offset % clusterSize);
			int size = Math.min(length, (int) (clusterSize - clusterOffset));
			dest.limit(dest.position() + size);

			blockDevice.read(getFileSystemOffset(chain[chainIndex], clusterOffset), dest);

			// round up to next cluster in the chain
			chainIndex++;
			// make length now a multiple of the cluster size
			length -= size;
		}

		// now we can proceed reading the clusters without an offset in the cluster
		while (length > 0) {
			int size = (int) Math.min(clusterSize, length);
			dest.limit(dest.position() + size);

			blockDevice.read(getFileSystemOffset(chain[chainIndex], 0), dest);

			chainIndex++;
			length -= size;
		}
	}

	void read(UsbDeviceConnection deviceConnection, long offset, ByteBuffer dest) throws IOException {
		int length = dest.remaining();

		int chainIndex = (int) (offset / clusterSize);
		if (offset % clusterSize != 0) {
			// offset in the cluster
			int clusterOffset = (int) (offset % clusterSize);
			int size = Math.min(length, (int) (clusterSize - clusterOffset));
			dest.limit(dest.position() + size);

			blockDevice.read(getFileSystemOffset(chain[chainIndex], clusterOffset), dest);

			// round up to next cluster in the chain
			chainIndex++;
			// make length now a multiple of the cluster size
			length -= size;
		}

		// now we can proceed reading the clusters without an offset in the cluster
		while (length > 0) {
			int size = (int) Math.min(clusterSize, length);
			dest.limit(dest.position() + size);

			blockDevice.read(deviceConnection, getFileSystemOffset(chain[chainIndex], 0), dest);

			chainIndex++;
			length -= size;
		}
	}

	void write(long offset, ByteBuffer source) throws IOException {
		int length = source.remaining();

		int chainIndex = (int) (offset / clusterSize);
		if (offset % clusterSize != 0) {
			int clusterOffset = (int) (offset % clusterSize);
			int size = Math.min(length, (int) (clusterSize - clusterOffset));
			source.limit(source.position() + size);

			blockDevice.write(getFileSystemOffset(chain[chainIndex], clusterOffset), source);

			// round up to next cluster in the chain
			chainIndex++;
			// make length now a multiple of the cluster size
			length -= size;
		}

		while (length > 0) {
			int size = (int) Math.min(clusterSize, length);
			source.limit(source.position() + size);

			blockDevice.write(getFileSystemOffset(chain[chainIndex], 0), source);

			chainIndex++;
			length -= size;
		}
	}

	void write(UsbDeviceConnection usbDeviceConnection, long offset, ByteBuffer source) throws IOException {
		int length = source.remaining();

		int chainIndex = (int) (offset / clusterSize);
		if (offset % clusterSize != 0) {
			int clusterOffset = (int) (offset % clusterSize);
			int size = Math.min(length, (int) (clusterSize - clusterOffset));
			source.limit(source.position() + size);

			blockDevice.write(usbDeviceConnection, getFileSystemOffset(chain[chainIndex], clusterOffset), source);

			// round up to next cluster in the chain
			chainIndex++;
			// make length now a multiple of the cluster size
			length -= size;
		}

		while (length > 0) {
			int size = (int) Math.min(clusterSize, length);
			source.limit(source.position() + size);

			blockDevice.write(usbDeviceConnection, getFileSystemOffset(chain[chainIndex], 0), source);

			chainIndex++;
			length -= size;
		}
	}

	private long getFileSystemOffset(long cluster, int clusterOffset) {
		return dataAreaOffset + clusterOffset + (cluster - 2) * clusterSize;
	}

	void setClusters(int newNumberOfClusters) throws IOException {
		int oldNumberOfClusters = getClusters();
		if (newNumberOfClusters == oldNumberOfClusters)
			return;

		if (newNumberOfClusters > oldNumberOfClusters) {
			CustomLog.d(TAG, "grow chain");
			chain = fat.alloc(chain, newNumberOfClusters - oldNumberOfClusters);
		} else {
			CustomLog.d(TAG, "shrink chain");
			chain = fat.free(chain, oldNumberOfClusters - newNumberOfClusters);
		}
	}

	int getClusters() {
		return chain.length;
	}

	void setLength(long newLength) throws IOException {
		final long newNumberOfClusters = ((newLength + clusterSize - 1) / clusterSize);
		setClusters((int) newNumberOfClusters);
	}

	long getLength() {
		return chain.length * clusterSize;
	}
}
