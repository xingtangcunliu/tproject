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

package com.github.mjdev.libaums.driver;

import android.hardware.usb.UsbDeviceConnection;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * This interface describes a simple block device with a certain block size and
 * the ability to read and write at a certain device offset.
 *
 * @author mjahnen
 *
 */
public interface BlockDeviceDriver {
	/**
	 * Initializes the block device for further use. This method should be
	 * called before doing anything else on the block device.
	 */
	public void init() throws IOException;

	/**
	 * Reads from the block device at a certain offset into the given buffer.
	 * The amount of bytes to be read are determined by
	 * {@link java.nio.ByteBuffer#remaining()}.
	 */
	public void read(long deviceOffset, ByteBuffer buffer) throws IOException;

	public void read(UsbDeviceConnection usbDeviceConnection, long deviceOffset, ByteBuffer buffer) throws IOException;

	/**
	 * Writes to the block device at a certain offset from the given buffer. The
	 * amount of bytes to be written are determined by
	 * {@link java.nio.ByteBuffer#remaining()}.
	 */
	public void write(long deviceOffset, ByteBuffer buffer) throws IOException;
	public void write(UsbDeviceConnection usbDeviceConnection, long deviceOffset, ByteBuffer buffer) throws IOException;

	/**
	 * Returns the block size of the block device. Every block device can only
	 * read and store bytes in a specific block with a certain size.
	 */
	public int getBlockSize();
}
