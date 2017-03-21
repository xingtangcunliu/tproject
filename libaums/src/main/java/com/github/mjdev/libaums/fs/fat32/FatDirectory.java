/*
 * (C) Copyright 2014-2016 mjahnen <jahnen@in.tum.de>
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import android.hardware.usb.UsbDeviceConnection;
import android.util.Log;

import com.github.mjdev.libaums.CustomLog;
import com.github.mjdev.libaums.driver.BlockDeviceDriver;
import com.github.mjdev.libaums.fs.AbstractUsbFile;
import com.github.mjdev.libaums.fs.UsbFile;

/**
 * This class represents a directory in the FAT32 file system. It can hold other
 * directories and files.
 *
 * @author mjahnen
 *
 */
public class FatDirectory extends AbstractUsbFile {

	private static String TAG = FatDirectory.class.getSimpleName();

	private ClusterChain chain;
	private BlockDeviceDriver blockDevice;
	private FAT fat;
	private Fat32BootSector bootSector;

	private String volumeLabel;
	private boolean hasBeenInited;
	private List<FatLfnDirectoryEntry> entries; // Entries read from the device.
	private FatDirectory parent; // Null if this is the root directory.
	private FatLfnDirectoryEntry entry; // Null if this is the root directory.

	private Map<String, FatLfnDirectoryEntry> lfnMap;
	private Map<ShortName, FatDirectoryEntry> shortNameMap;

	/**
	 * Constructs a new FatDirectory with the given information.
	 */
	private FatDirectory(BlockDeviceDriver blockDevice, FAT fat, Fat32BootSector bootSector, FatDirectory parent) {
		this.blockDevice = blockDevice;
		this.fat = fat;
		this.bootSector = bootSector;
		this.parent = parent;
		lfnMap = new HashMap<String, FatLfnDirectoryEntry>();
		shortNameMap = new HashMap<ShortName, FatDirectoryEntry>();
	}

	static FatDirectory create(FatLfnDirectoryEntry entry, BlockDeviceDriver blockDevice, FAT fat, Fat32BootSector bootSector, FatDirectory parent) {
		FatDirectory result = new FatDirectory(blockDevice, fat, bootSector, parent);
		result.entry = entry;
		return result;
	}

	static FatDirectory readRoot(BlockDeviceDriver blockDevice, FAT fat, Fat32BootSector bootSector) throws IOException {
		FatDirectory result = new FatDirectory(blockDevice, fat, bootSector, null);
		result.chain = new ClusterChain(bootSector.getRootDirStartCluster(), blockDevice, fat, bootSector);
		result.init();
		return result;
	}

	private void init() throws IOException {
		if (chain == null) {
			chain = new ClusterChain(entry.getStartCluster(), blockDevice, fat, bootSector); // ------> 4
		}

		if(entries == null) {
			entries = new ArrayList<FatLfnDirectoryEntry>();
		}

		if(entries.size() == 0 && !hasBeenInited) {
			readEntries();
		}

		hasBeenInited = true;
	}

	private void init(UsbDeviceConnection deviceConnection) throws IOException {
		if (chain == null) {
			chain = new ClusterChain(deviceConnection, entry.getStartCluster(), blockDevice, fat, bootSector); // ------> 4
		}

		if(entries == null) {
			entries = new ArrayList<FatLfnDirectoryEntry>();
		}

		if(entries.size() == 0 && !hasBeenInited) {
			readEntries(deviceConnection);
		}

		hasBeenInited = true;
	}

	/**
	 * Reads all entries from the directory
	 */
	private void readEntries() throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate((int) chain.getLength());
		chain.read(0, buffer);

		ArrayList<FatDirectoryEntry> list = new ArrayList<FatDirectoryEntry>();
		buffer.flip();
		while (buffer.remaining() > 0) {
			FatDirectoryEntry e = FatDirectoryEntry.read(buffer);
			if (e == null) {
				break;
			}

			if (e.isLfnEntry()) {
				list.add(e);
				continue;
			}

			if (e.isVolumeLabel()) {
				if (!isRoot()) {
					CustomLog.w(TAG, "volume label in non root dir!");
				}
				volumeLabel = e.getVolumeLabel();
				CustomLog.w(TAG, "volume label: " + volumeLabel);
				continue;
			}

			if (e.isDeleted()) {
				list.clear();
				continue;
			}

			FatLfnDirectoryEntry lfnEntry = FatLfnDirectoryEntry.read(e, list);
			addEntry(lfnEntry, e);
			list.clear();
		}
	}

	private void readEntries(UsbDeviceConnection deviceConnection) throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate((int) chain.getLength());
		chain.read(deviceConnection, 0, buffer);
		ArrayList<FatDirectoryEntry> list = new ArrayList<FatDirectoryEntry>();
		buffer.flip();
		while (buffer.remaining() > 0) {
			FatDirectoryEntry e = FatDirectoryEntry.read(buffer);
			if (e == null) {
				break;
			}

			if (e.isLfnEntry()) {
				list.add(e);
				continue;
			}

			if (e.isVolumeLabel()) {
				if (!isRoot()) {
					CustomLog.w(TAG, "volume label in non root dir!");
				}
				volumeLabel = e.getVolumeLabel();
				CustomLog.w(TAG, "volume label: " + volumeLabel);
				continue;
			}

			if (e.isDeleted()) {
				list.clear();
				continue;
			}

			FatLfnDirectoryEntry lfnEntry = FatLfnDirectoryEntry.read(e, list);
			addEntry(lfnEntry, e);
			list.clear();
		}
	}

	private void addEntry(FatLfnDirectoryEntry lfnEntry, FatDirectoryEntry entry) {
		entries.add(lfnEntry);
		lfnMap.put(lfnEntry.getName().toLowerCase(Locale.getDefault()), lfnEntry);
		shortNameMap.put(entry.getShortName(), entry);
	}

	/**
	 * Removes (if existing) the long file name entry
	 */
	void removeEntry(FatLfnDirectoryEntry lfnEntry) {
		entries.remove(lfnEntry);
		lfnMap.remove(lfnEntry.getName().toLowerCase(Locale.getDefault()));
		shortNameMap.remove(lfnEntry.getActualEntry().getShortName());
	}

	void renameEntry(FatLfnDirectoryEntry lfnEntry, String newName) throws IOException {
		if (lfnEntry.getName().equals(newName))
			return;

		removeEntry(lfnEntry);
		lfnEntry.setName(newName, ShortNameGenerator.generateShortName(newName, shortNameMap.keySet()));
		addEntry(lfnEntry, lfnEntry.getActualEntry());
		write();
	}

	/**
	 * Writes the entries to the disk.
	 */
	void write() throws IOException {
		init();
		final boolean writeVolumeLabel = isRoot() && volumeLabel != null;
		// first lookup the total entries needed
		int totalEntryCount = 0;
		for (FatLfnDirectoryEntry entry : entries) {
			totalEntryCount += entry.getEntryCount();
		}

		if (writeVolumeLabel)
			totalEntryCount++;

		long totalBytes = totalEntryCount * FatDirectoryEntry.SIZE;
		chain.setLength(totalBytes);

		ByteBuffer buffer = ByteBuffer.allocate((int) chain.getLength());
		buffer.order(ByteOrder.LITTLE_ENDIAN);

		if (writeVolumeLabel)
			FatDirectoryEntry.createVolumeLabel(volumeLabel).serialize(buffer);

		for (FatLfnDirectoryEntry entry : entries) {
			entry.serialize(buffer);
		}

		if (totalBytes % bootSector.getBytesPerCluster() != 0 || totalBytes == 0) {
			// add dummy entry filled with zeros to mark end of entries
			buffer.put(new byte[32]);
		}

		buffer.flip();
		chain.write(0, buffer);
	}

	void write(UsbDeviceConnection usbDeviceConnection) throws IOException {
		init();
		final boolean writeVolumeLabel = isRoot() && volumeLabel != null;
		// first lookup the total entries needed
		int totalEntryCount = 0;
		for (FatLfnDirectoryEntry entry : entries) {
			totalEntryCount += entry.getEntryCount();
		}

		if (writeVolumeLabel)
			totalEntryCount++;

		long totalBytes = totalEntryCount * FatDirectoryEntry.SIZE;
		chain.setLength(totalBytes);

		ByteBuffer buffer = ByteBuffer.allocate((int) chain.getLength());
		buffer.order(ByteOrder.LITTLE_ENDIAN);

		if (writeVolumeLabel)
			FatDirectoryEntry.createVolumeLabel(volumeLabel).serialize(buffer);

		for (FatLfnDirectoryEntry entry : entries) {
			entry.serialize(buffer);
		}

		if (totalBytes % bootSector.getBytesPerCluster() != 0 || totalBytes == 0) {
			// add dummy entry filled with zeros to mark end of entries
			buffer.put(new byte[32]);
		}

		buffer.flip();
		chain.write(usbDeviceConnection, 0, buffer);
	}

    @Override
	public boolean isRoot() {
		return entry == null;
	}

	String getVolumeLabel() {
		return volumeLabel;
	}

	@Override
	public FatFile createFile(String name) throws IOException {
		if (lfnMap.containsKey(name.toLowerCase(Locale.getDefault())))
			throw new IOException("Item already exists!");

		init();

		ShortName shortName = ShortNameGenerator.generateShortName(name, shortNameMap.keySet());

		FatLfnDirectoryEntry entry = FatLfnDirectoryEntry.createNew(name, shortName);
		// alloc completely new chain
		long newStartCluster = fat.alloc(new Long[0], 1)[0];
		entry.setStartCluster(newStartCluster);

		CustomLog.d(TAG, "adding entry: " + entry + " with short name: " + shortName);
		addEntry(entry, entry.getActualEntry());
		// write changes immediately to disk
		write();

		return FatFile.create(entry, blockDevice, fat, bootSector, this);
	}

	@Override
	public FatDirectory createDirectory(String name) throws IOException {
		if (lfnMap.containsKey(name.toLowerCase(Locale.getDefault())))
			throw new IOException("Item already exists!");

		init();

		ShortName shortName = ShortNameGenerator.generateShortName(name, shortNameMap.keySet());

		FatLfnDirectoryEntry entry = FatLfnDirectoryEntry.createNew(name, shortName);
		entry.setDirectory();
		// alloc completely new chain
		long newStartCluster = fat.alloc(new Long[0], 1)[0];
		entry.setStartCluster(newStartCluster);

		CustomLog.d(TAG, "adding entry: " + entry + " with short name: " + shortName);
		addEntry(entry, entry.getActualEntry());
		// write changes immediately to disk
		write();

		FatDirectory result = FatDirectory.create(entry, blockDevice, fat, bootSector, this);
		result.hasBeenInited = true;

		result.entries = new ArrayList<FatLfnDirectoryEntry>();

		// first create the dot entry which points to the dir just created
		FatLfnDirectoryEntry dotEntry = FatLfnDirectoryEntry.createNew(null, new ShortName(".", ""));
		dotEntry.setDirectory();
		dotEntry.setStartCluster(newStartCluster);
		FatLfnDirectoryEntry.copyDateTime(entry, dotEntry);
		result.addEntry(dotEntry, dotEntry.getActualEntry());

		FatLfnDirectoryEntry dotDotEntry = FatLfnDirectoryEntry.createNew(null, new ShortName("..", ""));
		dotDotEntry.setDirectory();
		dotDotEntry.setStartCluster(isRoot() ? 0 : entry.getStartCluster());
		FatLfnDirectoryEntry.copyDateTime(entry, dotDotEntry);
		result.addEntry(dotDotEntry, dotDotEntry.getActualEntry());

		result.write();

		return result;
	}

	@Override
	public void setLength(long newLength) {
		throw new UnsupportedOperationException("This is a directory!");
	}

	@Override
	public long getLength() {
		throw new UnsupportedOperationException("This is a directory!");
	}

	@Override
	public boolean isDirectory() {
		return true;
	}

	@Override
	public String getName() {
		return entry != null ? entry.getName() : "";
	}

	@Override
	public void setName(String newName) throws IOException {
		if (isRoot())
			throw new IllegalStateException("Cannot rename root dir!");
		parent.renameEntry(entry, newName);
	}

	@Override
	public long createdAt() {
        if (isRoot())
            throw new IllegalStateException("root dir!");
        return entry.getActualEntry().getCreatedDateTime();
	}

	@Override
	public long lastModified() {
        if (isRoot())
            throw new IllegalStateException("root dir!");
        return entry.getActualEntry().getLastModifiedDateTime();
	}

	@Override
	public long lastAccessed() {
        if (isRoot())
            throw new IllegalStateException("root dir!");
        return entry.getActualEntry().getLastAccessedDateTime();
	}

	@Override
	public UsbFile getParent() {
		return parent;
	}

	@Override
	public String[] list(UsbDeviceConnection deviceConnection) throws IOException {
		init(deviceConnection);
        List<String> list = new ArrayList<String>(entries.size());
		for (int i = 0; i < entries.size(); i++) {
			String name = entries.get(i).getName();
			if (!name.equals(".") && !name.equals("..")) {
				list.add(name);
			}
		}

        String[] array = new String[list.size()];
        array = list.toArray(array);

        return array;
	}

	@Override
	public String[] list() throws IOException {
		init();
		List<String> list = new ArrayList<String>(entries.size());
		for (int i = 0; i < entries.size(); i++) {
			String name = entries.get(i).getName();
			if (!name.equals(".") && !name.equals("..")) {
				list.add(name);
			}
		}

		String[] array = new String[list.size()];
		array = list.toArray(array);

		return array;
	}

	@Override
	public UsbFile[] listFiles() throws IOException {
        init(); // ------> 3
        List<UsbFile> list = new ArrayList<UsbFile>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            FatLfnDirectoryEntry entry = entries.get(i);
            String name = entry.getName();
            if (name.equals(".") || name.equals(".."))
                continue;

            if (entry.isDirectory()) {
                list.add(FatDirectory.create(entry, blockDevice, fat, bootSector, this));
            } else {
                list.add(FatFile.create(entry, blockDevice, fat, bootSector, this));
            }
        }

        UsbFile[] array = new UsbFile[list.size()];
        array = list.toArray(array);

        return array;
	}

	@Override
	public UsbFile[] listFiles(UsbDeviceConnection deviceConnection) throws IOException {
		init(deviceConnection); // ------> 3
		List<UsbFile> list = new ArrayList<UsbFile>(entries.size());
		for (int i = 0; i < entries.size(); i++) {
			FatLfnDirectoryEntry entry = entries.get(i);
			String name = entry.getName();
			if (name.equals(".") || name.equals(".."))
				continue;

			if (entry.isDirectory()) {
				list.add(FatDirectory.create(entry, blockDevice, fat, bootSector, this));
			} else {
				list.add(FatFile.create(entry, blockDevice, fat, bootSector, this));
			}
		}

		UsbFile[] array = new UsbFile[list.size()];
		array = list.toArray(array);

		return array;
	}

	@Override
	public void read(long offset, ByteBuffer destination) throws IOException {
		throw new UnsupportedOperationException("This is a directory!");
	}

	@Override
	public void read(UsbDeviceConnection deviceConnection, long offset, ByteBuffer destination) throws IOException {
		throw new UnsupportedOperationException("This is a directory!");
	}

	@Override
	public void write(long offset, ByteBuffer source) throws IOException {
		throw new UnsupportedOperationException("This is a directory!");
	}

	@Override
	public void write(UsbDeviceConnection deviceConnection, long offset, ByteBuffer source) throws IOException {
		throw new UnsupportedOperationException("This is a directory!");
	}

	@Override
	public void flush() throws IOException {
		throw new UnsupportedOperationException("This is a directory!");
	}

	@Override
	public void close() throws IOException {
		throw new UnsupportedOperationException("This is a directory!");
	}

	@Override
	public void moveTo(UsbFile destination) throws IOException {
		if (isRoot())
			throw new IllegalStateException("cannot move root dir!");

		if (!destination.isDirectory())
			throw new IllegalStateException("destination cannot be a file!");
		if (!(destination instanceof FatDirectory))
			throw new IllegalStateException("cannot move between different filesystems!");
		// TODO check if destination is really on the same physical device or partition!

		FatDirectory destinationDir = (FatDirectory) destination;
		if (destinationDir.lfnMap.containsKey(entry.getName().toLowerCase(Locale.getDefault())))
			throw new IOException("item already exists in destination!");

        init();
		destinationDir.init();

		parent.removeEntry(entry);
		destinationDir.addEntry(entry, entry.getActualEntry());

		parent.write();
		destinationDir.write();
		parent = destinationDir;
	}

	@Override
	public void moveTo(UsbDeviceConnection deviceConnection, UsbFile destination) throws IOException {
		if (isRoot())
			throw new IllegalStateException("cannot move root dir!");

		if (!destination.isDirectory())
			throw new IllegalStateException("destination cannot be a file!");
		if (!(destination instanceof FatDirectory))
			throw new IllegalStateException("cannot move between different filesystems!");
		// TODO check if destination is really on the same physical device or partition!

		FatDirectory destinationDir = (FatDirectory) destination;
		if (destinationDir.lfnMap.containsKey(entry.getName().toLowerCase(Locale.getDefault())))
			throw new IOException("item already exists in destination!");

		init();
		destinationDir.init();

		parent.removeEntry(entry);
		destinationDir.addEntry(entry, entry.getActualEntry());

		parent.write();
		destinationDir.write();
		parent = destinationDir;
	}

	void move(UsbDeviceConnection deviceConnection, FatLfnDirectoryEntry entry, UsbFile destination) throws IOException {
		if (!destination.isDirectory())
			throw new IllegalStateException("destination cannot be a file!");
		if (!(destination instanceof FatDirectory))
			throw new IllegalStateException("cannot move between different filesystems!");
		// TODO check if destination is really on the same physical device or partition!

		FatDirectory destinationDir = (FatDirectory) destination;
		if (destinationDir.lfnMap.containsKey(entry.getName().toLowerCase(Locale.getDefault())))
			throw new IOException("item already exists in destination!");

		init();
		destinationDir.init();

		removeEntry(entry);
		destinationDir.addEntry(entry, entry.getActualEntry());

		write(deviceConnection);
		destinationDir.write(deviceConnection);
	}

	void move(FatLfnDirectoryEntry entry, UsbFile destination) throws IOException {
		if (!destination.isDirectory())
			throw new IllegalStateException("destination cannot be a file!");
		if (!(destination instanceof FatDirectory))
			throw new IllegalStateException("cannot move between different filesystems!");
		// TODO check if destination is really on the same physical device or partition!

		FatDirectory destinationDir = (FatDirectory) destination;
		if (destinationDir.lfnMap.containsKey(entry.getName().toLowerCase(Locale.getDefault())))
			throw new IOException("item already exists in destination!");

		init();
		destinationDir.init();

		removeEntry(entry);
		destinationDir.addEntry(entry, entry.getActualEntry());

		write();
		destinationDir.write();
	}

	@Override
	public void delete() throws IOException {
		if (isRoot())
			throw new IllegalStateException("Root dir cannot be deleted!");

		init();
		UsbFile[] subElements = listFiles();

		for (UsbFile file : subElements) {
			file.delete();
		}

		parent.removeEntry(entry);
		parent.write();
		chain.setLength(0);
	}
}
