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

import android.hardware.usb.UsbDeviceConnection;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.github.mjdev.libaums.driver.BlockDeviceDriver;
import com.github.mjdev.libaums.fs.UsbFile;

public class FatFile implements UsbFile {

	private BlockDeviceDriver blockDevice;
	private FAT fat;
	private Fat32BootSector bootSector;

	private FatDirectory parent;
	private ClusterChain chain;
	private FatLfnDirectoryEntry entry;

	private FatFile(BlockDeviceDriver blockDevice, FAT fat, Fat32BootSector bootSector,
			FatLfnDirectoryEntry entry, FatDirectory parent) {
		this.blockDevice = blockDevice;
		this.fat = fat;
		this.bootSector = bootSector;
		this.entry = entry;
		this.parent = parent;
	}

	public static FatFile create(FatLfnDirectoryEntry entry, BlockDeviceDriver blockDevice,
			FAT fat, Fat32BootSector bootSector, FatDirectory parent) throws IOException {
		return new FatFile(blockDevice, fat, bootSector, entry, parent);
	}

	private void initChain() throws IOException {
		if (chain == null) {
			chain = new ClusterChain(entry.getStartCluster(), blockDevice, fat, bootSector);
		}
	}

	private void initChain(UsbDeviceConnection connection) throws IOException {
		if (chain == null) {
			chain = new ClusterChain(connection, entry.getStartCluster(), blockDevice, fat, bootSector);
		}
	}

	@Override
	public UsbFile search(String path) {
		throw new UnsupportedOperationException("This is a file!");
	}

	@Override
	public boolean isDirectory() {
		return false;
	}

	@Override
	public String getName() {
		return entry.getName();
	}

	@Override
	public void setName(String newName) throws IOException {
		parent.renameEntry(entry, newName);
	}

	@Override
	public long createdAt() {
		return entry.getActualEntry().getCreatedDateTime();
	}

	@Override
	public long lastModified() {
		return entry.getActualEntry().getLastModifiedDateTime();
	}

	@Override
	public long lastAccessed() {
		return entry.getActualEntry().getLastAccessedDateTime();
	}

	@Override
	public UsbFile getParent() {
		return parent;
	}

	@Override
	public String[] list() {
		throw new UnsupportedOperationException("This is a file!");
	}

	@Override
	public String[] list(UsbDeviceConnection deviceConnection) throws IOException {
		throw new UnsupportedOperationException("This is a file!");
	}

	@Override
	public UsbFile[] listFiles() throws IOException {
		throw new UnsupportedOperationException("This is a file!");
	}

	@Override
	public UsbFile[] listFiles(UsbDeviceConnection deviceConnection) throws IOException {
		throw new UnsupportedOperationException("This is a file!");
	}

	@Override
	public long getLength() {
		return entry.getFileSize();
	}

	@Override
	public void setLength(long newLength) throws IOException {
        	initChain();
		chain.setLength(newLength);
		entry.setFileSize(newLength);
	}

	@Override
	public void read(long offset, ByteBuffer destination) throws IOException {
		initChain();
		entry.setLastAccessedTimeToNow();
		chain.read(offset, destination);
	}

	@Override
	public void read(UsbDeviceConnection deviceConnection, long offset, ByteBuffer destination) throws IOException {
		initChain(deviceConnection);
		entry.setLastAccessedTimeToNow();
		chain.read(offset, destination);
	}

	@Override
	public void write(long offset, ByteBuffer source) throws IOException {
		initChain();
		long length = offset + source.remaining();
		if (length > getLength())
			setLength(length);
		entry.setLastModifiedTimeToNow();
		chain.write(offset, source);
	}

	@Override
	public void write(UsbDeviceConnection deviceConnection, long offset, ByteBuffer source) throws IOException {
		initChain(deviceConnection);
		long length = offset + source.remaining();
		if (length > getLength())
			setLength(length);
		entry.setLastModifiedTimeToNow();
		chain.write(offset, source);
	}

	@Override
	public void flush() throws IOException {
		parent.write();
	}

	@Override
	public void close() throws IOException {
		flush();
	}

	@Override
	public UsbFile createDirectory(String name) throws IOException {
		throw new UnsupportedOperationException("This is a file!");
	}

	@Override
	public UsbFile createFile(String name) throws IOException {
		throw new UnsupportedOperationException("This is a file!");
	}

	@Override
	public void moveTo(UsbFile destination) throws IOException {
		parent.move(entry, destination);
		parent = (FatDirectory) destination;
	}

	@Override
	public void moveTo(UsbDeviceConnection deviceConnection, UsbFile destination) throws IOException {
		parent.move(deviceConnection, entry, destination);
		parent = (FatDirectory) destination;
	}

	@Override
	public void delete() throws IOException {
		initChain();
		parent.removeEntry(entry);
		parent.write();
		chain.setLength(0);
	}

	@Override
	public boolean isRoot() {
		return false;
	}

}
