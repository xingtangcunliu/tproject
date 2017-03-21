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
import java.nio.ByteOrder;

import android.util.Log;

import com.github.mjdev.libaums.driver.BlockDeviceDriver;

/**
 * This class holds information which shall support the {@link FAT}. For example
 * it has a method to get the last allocated cluster (
 * {@link #getLastAllocatedClusterHint()}). The FAT can use this to make
 * searching for free clusters more efficient because it does not have to search
 * the hole FAT.
 *
 * @author mjahnen
 *
 */
class FsInfoStructure {

	static int INVALID_VALUE = 0xFFFFFFFF;

	private static int LEAD_SIGNATURE_OFF = 0;
	private static int STRUCT_SIGNATURE_OFF = 484;
	private static int TRAIL_SIGNATURE_OFF = 508;
	private static int FREE_COUNT_OFF = 488;
	private static int NEXT_FREE_OFFSET = 492;

	private static int LEAD_SIGNATURE = 0x41615252;
	private static int STRUCT_SIGNATURE = 0x61417272;
	private static int TRAIL_SIGNATURE = 0xAA550000;

	private static final String TAG = FsInfoStructure.class.getSimpleName();

	private int offset;
	private BlockDeviceDriver blockDevice;
	private ByteBuffer buffer;

	private FsInfoStructure(BlockDeviceDriver blockDevice, int offset) throws IOException {
		this.blockDevice = blockDevice;
		this.offset = offset;
		buffer = ByteBuffer.allocate(512);
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		blockDevice.read(offset, buffer);
		buffer.clear();

		if (buffer.getInt(LEAD_SIGNATURE_OFF) != LEAD_SIGNATURE
				|| buffer.getInt(STRUCT_SIGNATURE_OFF) != STRUCT_SIGNATURE
				|| buffer.getInt(TRAIL_SIGNATURE_OFF) != TRAIL_SIGNATURE) {
			throw new IOException("invalid fs i structure!");
		}
	}

	static FsInfoStructure read(BlockDeviceDriver blockDevice, int offset)
			throws IOException {
		return new FsInfoStructure(blockDevice, offset);
	}

	void setFreeClusterCount(long value) {
		buffer.putInt(FREE_COUNT_OFF, (int) value);
	}

	long getFreeClusterCount() {
		return buffer.getInt(FREE_COUNT_OFF);
	}

	void setLastAllocatedClusterHint(long value) {
		buffer.putInt(NEXT_FREE_OFFSET, (int) value);
	}

	long getLastAllocatedClusterHint() {
		return buffer.getInt(NEXT_FREE_OFFSET);
	}

	void decreaseClusterCount(long numberOfClusters) {
		long freeClusterCount = getFreeClusterCount();
		if (freeClusterCount != FsInfoStructure.INVALID_VALUE) {
			setFreeClusterCount(freeClusterCount - numberOfClusters);
		}
	}

	void write() throws IOException {
		Log.d(TAG, "writing to device");
		blockDevice.write(offset, buffer);
		buffer.clear();
	}
}
