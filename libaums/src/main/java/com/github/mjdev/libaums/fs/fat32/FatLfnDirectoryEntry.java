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
import java.util.List;

/**
 * This class represents a long file name entry. The long file name can be
 * accessed via {@link #getName()}. This class delegates most actions to the
 * {@link #actualEntry}. It is responsible for parsing and serializing long file
 * names and the actual entry with the corresponding short name.
 * <p>
 * To understand the structure of long file name entries it is advantageous to
 * look at the official FAT32 specification.
 *
 * @author mjahnen
 *
 */
class FatLfnDirectoryEntry {

	private FatDirectoryEntry actualEntry;
	private String lfnName;

	private FatLfnDirectoryEntry() {

	}

	private FatLfnDirectoryEntry(FatDirectoryEntry actualEntry, String lfnName) {
		this.actualEntry = actualEntry;
		this.lfnName = lfnName;
	}

	static FatLfnDirectoryEntry createNew(String name, ShortName shortName) {
		FatLfnDirectoryEntry result = new FatLfnDirectoryEntry();

		result.lfnName = name;
		result.actualEntry = FatDirectoryEntry.createNew();
		result.actualEntry.setShortName(shortName);

		return result;
	}

	static FatLfnDirectoryEntry read(FatDirectoryEntry actualEntry,
			List<FatDirectoryEntry> lfnParts) {
		StringBuilder builder = new StringBuilder(13 * lfnParts.size());

		if (lfnParts.size() > 0) {
			// stored in reverse order on the disk
			for (int i = lfnParts.size() - 1; i >= 0; i--) {
				lfnParts.get(i).extractLfnPart(builder);
			}

			return new FatLfnDirectoryEntry(actualEntry, builder.toString());
		}

		return new FatLfnDirectoryEntry(actualEntry, null);
	}

	void serialize(ByteBuffer buffer) {
		if (lfnName != null) {
			byte checksum = actualEntry.getShortName().calculateCheckSum();
			int entrySize = getEntryCount();

			// long filename is stored in reverse order
			int index = entrySize - 2;
			// first write last entry
			FatDirectoryEntry entry = FatDirectoryEntry.createLfnPart(lfnName, index * 13,
					checksum, index + 1, true);
			entry.serialize(buffer);

			while ((index--) > 0) {
				entry = FatDirectoryEntry.createLfnPart(lfnName, index * 13, checksum, index + 1,
						false);
				entry.serialize(buffer);
			}
		}

		// finally write the actual entry
		actualEntry.serialize(buffer);
	}

	int getEntryCount() {
		// we always have the actual entry
		int result = 1;

		// if long filename exists add needed entries
		if (lfnName != null) {
			int len = lfnName.length();
			result += len / 13;
			if (len % 13 != 0)
				result++;
		}

		return result;
	}

	String getName() {
		if (lfnName != null)
			return lfnName;

		String sname = actualEntry.getShortName().getString();
		String name = sname;
		String ext = "";

		String[] split = sname.split(".");
		if(split.length == 2) {
			name = split[0];
			ext = split[0];
		}

		if(actualEntry.isShortNameLowerCase())
			name = name.toLowerCase();
		if(actualEntry.isShortNameExtLowerCase())
			ext = ext.toLowerCase();

		if(!ext.isEmpty())
			name = name + "." + ext;

		return name;
	}

	void setName(String newName, ShortName shortName) {
		lfnName = newName;
		actualEntry.setShortName(shortName);
	}

	long getFileSize() {
		return actualEntry.getFileSize();
	}

	void setFileSize(long newSize) {
		actualEntry.setFileSize(newSize);
	}

	long getStartCluster() {
		return actualEntry.getStartCluster();
	}

	void setStartCluster(long newStartCluster) {
		actualEntry.setStartCluster(newStartCluster);
	}

	void setLastAccessedTimeToNow() {
		actualEntry.setLastAccessedDateTime(System.currentTimeMillis());
	}

	void setLastModifiedTimeToNow() {
		actualEntry.setLastModifiedDateTime(System.currentTimeMillis());
	}

	boolean isDirectory() {
		return actualEntry.isDirectory();
	}

	void setDirectory() {
		actualEntry.setDirectory();
	}

	FatDirectoryEntry getActualEntry() {
		return actualEntry;
	}

	static void copyDateTime(FatLfnDirectoryEntry from, FatLfnDirectoryEntry to) {
		FatDirectoryEntry actualFrom = from.getActualEntry();
		FatDirectoryEntry actualTo = from.getActualEntry();
		actualTo.setCreatedDateTime(actualFrom.getCreatedDateTime());
		actualTo.setLastAccessedDateTime(actualFrom.getLastAccessedDateTime());
		actualTo.setLastModifiedDateTime(actualFrom.getLastModifiedDateTime());
	}

	@Override
	public String toString() {
		return "[FatLfnDirectoryEntry getName()=" + getName() + "]";
	}
}
