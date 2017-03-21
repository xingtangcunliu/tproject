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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * This class represents the FAT32 boot sector which is always located at the
 * beginning of every FAT32 file system. It holds important information about
 * the file system such as the cluster size and the start cluster of the root
 * directory.
 *
 * @author mjahnen
 *
 */
public class Fat32BootSector {
	private static final int BYTES_PER_SECTOR_OFF = 11;
	private static final int SECTORS_PER_CLUSTER_OFF = 13;
	private static final int RESERVED_COUNT_OFF = 14;
	private static final int FAT_COUNT_OFF = 16;
	private static final int TOTAL_SECTORS_OFF = 32;
	private static final int SECTORS_PER_FAT_OFF = 36;
	private static final int FLAGS_OFF = 40;
	private static final int ROOT_DIR_CLUSTER_OFF = 44;
	private static final int FS_INFO_SECTOR_OFF = 48;
	private static final int VOLUME_LABEL_OFF = 48;

	private short bytesPerSector;
	private short sectorsPerCluster;
	private short reservedSectors;
	private byte fatCount;
	private long totalNumberOfSectors;
	private long sectorsPerFat;
	private long rootDirStartCluster;
	private short fsInfoStartSector;
	private boolean fatMirrored;
	private byte validFat;
	private String volumeLabel;

	private Fat32BootSector() {

	}

	static Fat32BootSector read(ByteBuffer buffer) {
		Fat32BootSector result = new Fat32BootSector();
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		result.bytesPerSector = buffer.getShort(BYTES_PER_SECTOR_OFF);
		result.sectorsPerCluster = (short) (buffer.get(SECTORS_PER_CLUSTER_OFF) & 0xff);
		result.reservedSectors = buffer.getShort(RESERVED_COUNT_OFF);
		result.fatCount = buffer.get(FAT_COUNT_OFF);
		result.totalNumberOfSectors = buffer.getInt(TOTAL_SECTORS_OFF) & 0xffffffffl;
		result.sectorsPerFat = buffer.getInt(SECTORS_PER_FAT_OFF) & 0xffffffffl;
		result.rootDirStartCluster = buffer.getInt(ROOT_DIR_CLUSTER_OFF) & 0xffffffffl;
		result.fsInfoStartSector = buffer.getShort(FS_INFO_SECTOR_OFF);
		short flag = buffer.getShort(FLAGS_OFF);
		result.fatMirrored = ((byte) flag & 0x80) == 0;
		result.validFat = (byte) ((byte) flag & 0x7);

		StringBuilder builder = new StringBuilder();

		for (int i = 0; i < 11; i++) {
			byte b = buffer.get(VOLUME_LABEL_OFF + i);
			if (b == 0)
				break;
			builder.append((char) b);
		}

		result.volumeLabel = builder.toString();

		return result;
	}

	short getBytesPerSector() {
		return bytesPerSector;
	}

	short getSectorsPerCluster() {
		return sectorsPerCluster;
	}

	short getReservedSectors() {
		return reservedSectors;
	}

	byte getFatCount() {
		return fatCount;
	}

	long getTotalNumberOfSectors() {
		return totalNumberOfSectors;
	}

	long getSectorsPerFat() {
		return sectorsPerFat;
	}

	long getRootDirStartCluster() {
		return rootDirStartCluster;
	}

	short getFsInfoStartSector() {
		return fsInfoStartSector;
	}

	boolean isFatMirrored() {
		return fatMirrored;
	}

	byte getValidFat() {
		return validFat;
	}

	int getBytesPerCluster() {
		return sectorsPerCluster * bytesPerSector;
	}

	long getFatOffset(int fatNumber) {
		return getBytesPerSector() * (getReservedSectors() + fatNumber * getSectorsPerFat());
	}

	long getDataAreaOffset() {
		return getFatOffset(0) + getFatCount() * getSectorsPerFat() * getBytesPerSector();
	}

	String getVolumeLabel() {
		return volumeLabel;
	}

	@Override
	public String toString() {
		return "Fat32BootSector{" +
				"bytesPerSector=" + bytesPerSector +
				", sectorsPerCluster=" + sectorsPerCluster +
				", reservedSectors=" + reservedSectors +
				", fatCount=" + fatCount +
				", totalNumberOfSectors=" + totalNumberOfSectors +
				", sectorsPerFat=" + sectorsPerFat +
				", rootDirStartCluster=" + rootDirStartCluster +
				", fsInfoStartSector=" + fsInfoStartSector +
				", fatMirrored=" + fatMirrored +
				", validFat=" + validFat +
				", volumeLabel='" + volumeLabel + '\'' +
				'}';
	}
}
