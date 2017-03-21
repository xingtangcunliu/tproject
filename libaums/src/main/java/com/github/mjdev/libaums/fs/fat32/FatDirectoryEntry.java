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
import java.nio.charset.Charset;
import java.util.Calendar;

/**
 * This class holds the information of an 32 byte FAT32 directory entry. It
 * holds information such as if an entry is a directory, read only or hidden.
 * <p>
 * There are three cases a {@link #FatDirectoryEntry()} is used:
 * <ul>
 * <li>To store information about a directory or a file.
 * <li>To store a part of a long filename. Every entry can store up to 13 bytes
 * of a long file name.
 * <li>To store a volume label which can occur in the root directory of a FAT32
 * file system.
 * </ul>
 * <p>
 * To determine if the entry denotes a volume label entry use
 * {@link #isVolumeLabel()}. If this method returns true only the method
 * {@link #getVolumeLabel()} makes sense to call. Calling any other method the
 * results will be undefined.
 * <p>
 * To determine if an entry is an entry for a long file name use
 * {@link #isLfnEntry()}. If this method returns true only the method
 * {@link #extractLfnPart(StringBuilder)} makes sense to call. Calling any other
 * method the results will be undefined.
 * <p>
 * In all other cases the entry is either a file or a directory which can be
 * determined by {@link #isDirectory()}. Further information of the file or
 * directory give for example {@link #getCreatedDateTime()} or
 * {@link #getStartCluster()} to access the contents.
 *
 * @author mjahnen
 *
 */
class FatDirectoryEntry {

	final static int SIZE = 32;

	private static final int ATTR_OFF = 0x0b;
	private static final int FILE_SIZE_OFF = 0x1c;
	private static final int MSB_CLUSTER_OFF = 0x14;
	private static final int LSB_CLUSTER_OFF = 0x1a;
	private static final int CREATED_DATE_OFF = 0x10;
	private static final int CREATED_TIME_OFF = 0x0e;
	private static final int LAST_WRITE_DATE_OFF = 0x18;
	private static final int LAST_WRITE_TIME_OFF = 0x16;
	private static final int LAST_ACCESSED_DATE_OFF = 0x12;

	private static final int FLAG_READONLY = 0x01;
	private static final int FLAG_HIDDEN = 0x02;
	private static final int FLAG_SYSTEM = 0x04;
	private static final int FLAG_VOLUME_ID = 0x08;
	private static final int FLAG_DIRECTORY = 0x10;
	private static final int FLAG_ARCHIVE = 0x20;

	private static final int SHORTNAME_CASE_OFF = 0x0c;

	static final int ENTRY_DELETED = 0xe5;
	private ByteBuffer data;
	private ShortName shortName;

	private FatDirectoryEntry() {

	}

	private FatDirectoryEntry(ByteBuffer data) {
		this.data = data;
		data.order(ByteOrder.LITTLE_ENDIAN);
		shortName = ShortName.parse(data);
		// clear buffer because short name took 13 bytes
		data.clear();
	}

	static FatDirectoryEntry createNew() {
		FatDirectoryEntry result = new FatDirectoryEntry();
		result.data = ByteBuffer.allocate(SIZE);

		long now = System.currentTimeMillis();
		result.setCreatedDateTime(now);
		result.setLastAccessedDateTime(now);
		result.setLastModifiedDateTime(now);

		return result;
	}

	static FatDirectoryEntry read(ByteBuffer data) {
		byte[] buffer = new byte[SIZE];

		if (data.get(data.position()) == 0)
			return null;

		data.get(buffer);

		return new FatDirectoryEntry(ByteBuffer.wrap(buffer));
	}

	void serialize(ByteBuffer buffer) {
		buffer.put(data.array());
	}

	private int getFlags() {
		return data.get(ATTR_OFF);
	}

	private void setFlag(int flag) {
		int flags = getFlags();
		data.put(ATTR_OFF, (byte) (flag | flags));
	}

	private boolean isFlagSet(int flag) {
		return (getFlags() & flag) != 0;
	}

	boolean isShortNameLowerCase() {
		return (data.get(SHORTNAME_CASE_OFF) & (byte) 0x8) != 0;
	}

	boolean isShortNameExtLowerCase() {
		return (data.get(SHORTNAME_CASE_OFF) & (byte) 0x10) != 0;
	}

	boolean isLfnEntry() {
		return isHidden() && isVolume() && isReadOnly() && isSystem();
	}

	boolean isDirectory() {
		return ((getFlags() & (FLAG_DIRECTORY | FLAG_VOLUME_ID)) == FLAG_DIRECTORY);
	}

	void setDirectory() {
		setFlag(FLAG_DIRECTORY);
	}

	boolean isVolumeLabel() {
		if (isLfnEntry())
			return false;
		else
			return ((getFlags() & (FLAG_DIRECTORY | FLAG_VOLUME_ID)) == FLAG_VOLUME_ID);
	}

	boolean isSystem() {
		return isFlagSet(FLAG_SYSTEM);
	}

	boolean isHidden() {
		return isFlagSet(FLAG_HIDDEN);
	}

	boolean isArchive() {
		return isFlagSet(FLAG_ARCHIVE);
	}

	boolean isReadOnly() {
		return isFlagSet(FLAG_READONLY);
	}

	boolean isVolume() {
		return isFlagSet(FLAG_VOLUME_ID);
	}

	boolean isDeleted() {
		return getUnsignedInt8(0) == ENTRY_DELETED;
	}

	long getCreatedDateTime() {
		return decodeDateTime(getUnsignedInt16(CREATED_DATE_OFF),
				getUnsignedInt16(CREATED_TIME_OFF));
	}

	void setCreatedDateTime(long dateTime) {
		// TODO entry has also field which holds 10th seconds created
		setUnsignedInt16(CREATED_DATE_OFF, encodeDate(dateTime));
		setUnsignedInt16(CREATED_TIME_OFF, encodeTime(dateTime));
	}

	long getLastModifiedDateTime() {
		return decodeDateTime(getUnsignedInt16(LAST_WRITE_DATE_OFF),
				getUnsignedInt16(LAST_WRITE_TIME_OFF));
	}

	void setLastModifiedDateTime(long dateTime) {
		setUnsignedInt16(LAST_WRITE_DATE_OFF, encodeDate(dateTime));
		setUnsignedInt16(LAST_WRITE_TIME_OFF, encodeTime(dateTime));
	}

	long getLastAccessedDateTime() {
		return decodeDateTime(getUnsignedInt16(LAST_ACCESSED_DATE_OFF), 0);
	}

	void setLastAccessedDateTime(long dateTime) {
		setUnsignedInt16(LAST_ACCESSED_DATE_OFF, encodeDate(dateTime));
	}

	ShortName getShortName() {
		if (data.get(0) == 0)
			return null;
		else {
			return shortName;
		}
	}

	void setShortName(ShortName shortName) {
		this.shortName = shortName;
		shortName.serialize(data);
		// clear buffer because short name put 13 bytes
		data.clear();
	}

	static FatDirectoryEntry createVolumeLabel(String volumeLabel) {
		FatDirectoryEntry result = new FatDirectoryEntry();
		ByteBuffer buffer = ByteBuffer.allocate(SIZE);
		buffer.order(ByteOrder.LITTLE_ENDIAN);

		System.arraycopy(volumeLabel.getBytes(Charset.forName("ASCII")), 0, buffer.array(), 0,
				volumeLabel.length());

		result.data = buffer;
		result.setFlag(FLAG_VOLUME_ID);

		return result;
	}

	String getVolumeLabel() {
		StringBuilder builder = new StringBuilder();

		for (int i = 0; i < 11; i++) {
			byte b = data.get(i);
			if (b == 0)
				break;
			builder.append((char) b);
		}

		return builder.toString();
	}

	long getStartCluster() {
		final int msb = getUnsignedInt16(MSB_CLUSTER_OFF);
		final int lsb = getUnsignedInt16(LSB_CLUSTER_OFF);
		return (msb << 16) | lsb;
	}

	void setStartCluster(long newStartCluster) {
		setUnsignedInt16(MSB_CLUSTER_OFF, (int) ((newStartCluster >> 16) & 0xffff));
		setUnsignedInt16(LSB_CLUSTER_OFF, (int) (newStartCluster & 0xffff));
	}

	long getFileSize() {
		return getUnsignedInt32(FILE_SIZE_OFF);
	}

	void setFileSize(long newSize) {
		setUnsignedInt32(FILE_SIZE_OFF, newSize);
	}

	static FatDirectoryEntry createLfnPart(String unicode, int offset, byte checksum,
			int index, boolean isLast) {
		FatDirectoryEntry result = new FatDirectoryEntry();

		if (isLast) {
			int diff = unicode.length() - offset;
			if (diff < 13) {
				StringBuilder builder = new StringBuilder(13);
				builder.append(unicode, offset, unicode.length());
				// end mark
				builder.append('\0');

				// fill with 0xffff
				for (int i = 0; i < 13 - diff; i++) {
					builder.append((char) 0xffff);
				}

				offset = 0;
				unicode = builder.toString();
			}
		}

		ByteBuffer buffer = ByteBuffer.allocate(SIZE);
		buffer.order(ByteOrder.LITTLE_ENDIAN);

		buffer.put(0, (byte) (isLast ? index + (1 << 6) : index));
		buffer.putShort(1, (short) unicode.charAt(offset));
		buffer.putShort(3, (short) unicode.charAt(offset + 1));
		buffer.putShort(5, (short) unicode.charAt(offset + 2));
		buffer.putShort(7, (short) unicode.charAt(offset + 3));
		buffer.putShort(9, (short) unicode.charAt(offset + 4));
		// Special mark for lfn entry
		buffer.put(11, (byte) (FLAG_HIDDEN | FLAG_VOLUME_ID | FLAG_READONLY | FLAG_SYSTEM));
		// unused
		buffer.put(12, (byte) 0);
		buffer.put(13, checksum);
		buffer.putShort(14, (short) unicode.charAt(offset + 5));
		buffer.putShort(16, (short) unicode.charAt(offset + 6));
		buffer.putShort(18, (short) unicode.charAt(offset + 7));
		buffer.putShort(20, (short) unicode.charAt(offset + 8));
		buffer.putShort(22, (short) unicode.charAt(offset + 9));
		buffer.putShort(24, (short) unicode.charAt(offset + 10));
		// unused
		buffer.putShort(26, (short) 0);
		buffer.putShort(28, (short) unicode.charAt(offset + 11));
		buffer.putShort(30, (short) unicode.charAt(offset + 12));

		result.data = buffer;

		return result;
	}

	void extractLfnPart(StringBuilder builder) {
		final char[] name = new char[13];
		name[0] = (char) data.getShort(1);
		name[1] = (char) data.getShort(3);
		name[2] = (char) data.getShort(5);
		name[3] = (char) data.getShort(7);
		name[4] = (char) data.getShort(9);
		name[5] = (char) data.getShort(14);
		name[6] = (char) data.getShort(16);
		name[7] = (char) data.getShort(18);
		name[8] = (char) data.getShort(20);
		name[9] = (char) data.getShort(22);
		name[10] = (char) data.getShort(24);
		name[11] = (char) data.getShort(28);
		name[12] = (char) data.getShort(30);

		int len = 0;
		while (len < 13 && name[len] != '\0')
			len++;

		builder.append(name, 0, len);
	}

	private int getUnsignedInt8(int offset) {
		return data.get(offset) & 0xff;
	}

	private int getUnsignedInt16(int offset) {
		final int i1 = data.get(offset) & 0xff;
		final int i2 = data.get(offset + 1) & 0xff;
		return (i2 << 8) | i1;
	}

	private long getUnsignedInt32(int offset) {
		final long i1 = data.get(offset) & 0xff;
		final long i2 = data.get(offset + 1) & 0xff;
		final long i3 = data.get(offset + 2) & 0xff;
		final long i4 = data.get(offset + 3) & 0xff;
		return (i4 << 24) | (i3 << 16) | (i2 << 8) | i1;
	}

	private void setUnsignedInt16(int offset, int value) {
		data.put(offset, (byte) (value & 0xff));
		data.put(offset + 1, (byte) ((value >>> 8) & 0xff));
	}

	private void setUnsignedInt32(int offset, long value) {
		data.put(offset, (byte) (value & 0xff));
		data.put(offset + 1, (byte) ((value >>> 8) & 0xff));
		data.put(offset + 2, (byte) ((value >>> 16) & 0xff));
		data.put(offset + 3, (byte) ((value >>> 24) & 0xff));
	}

	private static long decodeDateTime(int date, int time) {
		final Calendar calendar = Calendar.getInstance();

		calendar.set(Calendar.YEAR, 1980 + (date >> 9));
		calendar.set(Calendar.MONTH, ((date >> 5) & 0x0f) - 1);
		calendar.set(Calendar.DAY_OF_MONTH, date & 0x1f);
		calendar.set(Calendar.HOUR_OF_DAY, time >> 11);
		calendar.set(Calendar.MINUTE, (time >> 5) & 0x3f);
		calendar.set(Calendar.SECOND, (time & 0x1f) * 2);

		return calendar.getTimeInMillis();
	}

	private static int encodeDate(long timeInMillis) {
		final Calendar calendar = Calendar.getInstance();

		calendar.setTimeInMillis(timeInMillis);

		return ((calendar.get(Calendar.YEAR) - 1980) << 9)
				+ ((calendar.get(Calendar.MONTH) + 1) << 5) + calendar.get(Calendar.DAY_OF_MONTH);

	}

	private static int encodeTime(long timeInMillis) {
		final Calendar calendar = Calendar.getInstance();

		calendar.setTimeInMillis(timeInMillis);

		return (calendar.get(Calendar.HOUR_OF_DAY) << 11) + (calendar.get(Calendar.MINUTE) << 5)
				+ calendar.get(Calendar.SECOND) / 2;

	}

	@Override
	public String toString() {
		return "[FatDirectoryEntry shortName=" + shortName.getString() + "]";
	}
}
