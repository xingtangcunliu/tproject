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

package com.github.mjdev.libaums.driver.scsi;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import android.hardware.usb.UsbDeviceConnection;
import android.util.Log;

import com.github.mjdev.libaums.CustomLog;
import com.github.mjdev.libaums.usb.UsbCommunication;
import com.github.mjdev.libaums.driver.BlockDeviceDriver;
import com.github.mjdev.libaums.driver.scsi.commands.CommandBlockWrapper;
import com.github.mjdev.libaums.driver.scsi.commands.CommandBlockWrapper.Direction;
import com.github.mjdev.libaums.driver.scsi.commands.CommandStatusWrapper;
import com.github.mjdev.libaums.driver.scsi.commands.ScsiInquiry;
import com.github.mjdev.libaums.driver.scsi.commands.ScsiInquiryResponse;
import com.github.mjdev.libaums.driver.scsi.commands.ScsiRead10;
import com.github.mjdev.libaums.driver.scsi.commands.ScsiReadCapacity;
import com.github.mjdev.libaums.driver.scsi.commands.ScsiReadCapacityResponse;
import com.github.mjdev.libaums.driver.scsi.commands.ScsiTestUnitReady;
import com.github.mjdev.libaums.driver.scsi.commands.ScsiWrite10;

/**
 * This class is responsible for handling mass storage devices which follow the
 * SCSI standard. This class communicates with the mass storage device via the
 * different SCSI commands.
 *
 * @author mjahnen
 * @see com.github.mjdev.libaums.driver.scsi.commands
 */
public class ScsiBlockDevice implements BlockDeviceDriver {

	private static final String TAG = ScsiBlockDevice.class.getSimpleName();

	private UsbCommunication usbCommunication;
	private ByteBuffer outBuffer;
	private ByteBuffer cswBuffer;

	private int blockSize;
	private int lastBlockAddress;

	private ScsiWrite10 writeCommand = new ScsiWrite10();
	private ScsiRead10 readCommand = new ScsiRead10();
	private CommandStatusWrapper csw = new CommandStatusWrapper();

	public ScsiBlockDevice(UsbCommunication usbCommunication) {
		this.usbCommunication = usbCommunication;
		outBuffer = ByteBuffer.allocate(31);
		cswBuffer = ByteBuffer.allocate(CommandStatusWrapper.SIZE);
	}

	@Override
	public void init() throws IOException {
		ByteBuffer inBuffer = ByteBuffer.allocate(36);
		ScsiInquiry inquiry = new ScsiInquiry((byte) inBuffer.array().length);
		transferCommand(inquiry, inBuffer);
		inBuffer.clear();
		// TODO support multiple luns!
		ScsiInquiryResponse inquiryResponse = ScsiInquiryResponse.read(inBuffer);
		CustomLog.d(TAG, "inquiry response: " + inquiryResponse);

		if (inquiryResponse.getPeripheralQualifier() != 0
				|| inquiryResponse.getPeripheralDeviceType() != 0) {
			throw new IOException("unsupported PeripheralQualifier or PeripheralDeviceType");
		}

		ScsiTestUnitReady testUnit = new ScsiTestUnitReady();
		if (!transferCommand(testUnit, null)) {
			CustomLog.w(TAG, "unit not ready!");
		}

		ScsiReadCapacity readCapacity = new ScsiReadCapacity();
		inBuffer.clear();
		transferCommand(readCapacity, inBuffer);
		inBuffer.clear();
		ScsiReadCapacityResponse readCapacityResponse = ScsiReadCapacityResponse.read(inBuffer);
		blockSize = readCapacityResponse.getBlockLength();
		lastBlockAddress = readCapacityResponse.getLogicalBlockAddress();

		CustomLog.i(TAG, "Block size: " + blockSize);
		CustomLog.i(TAG, "Last block address: " + lastBlockAddress);
	}

	private boolean transferCommand(CommandBlockWrapper command, ByteBuffer inBuffer)
			throws IOException {
		byte[] outArray = outBuffer.array();
		Arrays.fill(outArray, (byte) 0);

		outBuffer.clear();
		command.serialize(outBuffer);
		outBuffer.clear();

		int written = usbCommunication.bulkOutTransfer(outBuffer); // ------> 9
		if (written != outArray.length) {
			throw new IOException("Writing all bytes on command " + command + " failed!");
		}

		int transferLength = command.getdCbwDataTransferLength();
		int read = 0;
		if (transferLength > 0) {

			if (command.getDirection() == Direction.IN) {
				do {
					read += usbCommunication.bulkInTransfer(inBuffer);
				} while (read < transferLength);

				if (read != transferLength) {
					throw new IOException("Unexpected command size (" + read + ") on response to " + command);
				}
			} else {
				written = 0;
				do {
					written += usbCommunication.bulkOutTransfer(inBuffer);
				} while (written < transferLength);

				if (written != transferLength) {
					throw new IOException("Could not write all bytes: " + command);
				}
			}
		}


		// expecting csw now
		cswBuffer.clear();
		read = usbCommunication.bulkInTransfer(cswBuffer);
		if (read != CommandStatusWrapper.SIZE) {
			throw new IOException("Unexpected command size while expecting csw");
		}
		cswBuffer.clear();

		csw.read(cswBuffer);
		if (csw.getbCswStatus() != CommandStatusWrapper.COMMAND_PASSED) {
			throw new IOException("Unsuccessful Csw status: " + csw.getbCswStatus());
		}

		if (csw.getdCswTag() != command.getdCbwTag()) {
			throw new IOException("wrong csw tag!");
		}

		return csw.getbCswStatus() == CommandStatusWrapper.COMMAND_PASSED;
	}

	private boolean transferCommand(UsbDeviceConnection usbDeviceConnection, CommandBlockWrapper command, ByteBuffer inBuffer)
			throws IOException {
		byte[] outArray = outBuffer.array();
		Arrays.fill(outArray, (byte) 0);

		outBuffer.clear();
		command.serialize(outBuffer);
		outBuffer.clear();

		int written = usbCommunication.bulkOutTransfer(usbDeviceConnection, outBuffer); // ------> 9
		if (written != outArray.length) {
			throw new IOException("Writing all bytes on command " + command + " failed!");
		}

		int transferLength = command.getdCbwDataTransferLength();
		int read = 0;
		if (transferLength > 0) {

			if (command.getDirection() == Direction.IN) {
				do {
					read += usbCommunication.bulkInTransfer(usbDeviceConnection, inBuffer);
				} while (read < transferLength);

				if (read != transferLength) {
					throw new IOException("Unexpected command size (" + read + ") on response to "
							+ command);
				}
			} else {
				written = 0;
				do {
					written += usbCommunication.bulkOutTransfer(usbDeviceConnection, inBuffer);
				} while (written < transferLength);

				if (written != transferLength) {
					throw new IOException("Could not write all bytes: " + command);
				}
			}
		}


		// expecting csw now
		cswBuffer.clear();
		read = usbCommunication.bulkInTransfer(usbDeviceConnection, cswBuffer);
		if (read != CommandStatusWrapper.SIZE) {
			throw new IOException("Unexpected command size while expecting csw");
		}
		cswBuffer.clear();

		csw.read(cswBuffer);
		if (csw.getbCswStatus() != CommandStatusWrapper.COMMAND_PASSED) {
			throw new IOException("Unsuccessful Csw status: " + csw.getbCswStatus());
		}

		if (csw.getdCswTag() != command.getdCbwTag()) {
			throw new IOException("wrong csw tag!");
		}

		return csw.getbCswStatus() == CommandStatusWrapper.COMMAND_PASSED;
	}

	@Override
	public synchronized void read(long devOffset, ByteBuffer dest) throws IOException {
		//long time = System.currentTimeMillis();
		if (dest.remaining() % blockSize != 0) {
			throw new IllegalArgumentException("dest.remaining() must be multiple of blockSize!");
		}

		readCommand.init((int) devOffset, dest.remaining(), blockSize);
		//CustomLog.d(TAG, "reading: " + read);

		transferCommand(readCommand, dest); // ------> 8
		dest.position(dest.limit());

		//CustomLog.d(TAG, "read time: " + (System.currentTimeMillis() - time));
	}

	@Override
	public synchronized void read(UsbDeviceConnection usbDeviceConnection, long devOffset, ByteBuffer dest) throws IOException {
		//long time = System.currentTimeMillis();
		if (dest.remaining() % blockSize != 0) {
			throw new IllegalArgumentException("dest.remaining() must be multiple of blockSize!");
		}

		readCommand.init((int) devOffset, dest.remaining(), blockSize);
		//CustomLog.d(TAG, "reading: " + read);

		transferCommand(usbDeviceConnection, readCommand, dest); // ------> 8
		dest.position(dest.limit());

		//CustomLog.d(TAG, "read time: " + (System.currentTimeMillis() - time));
	}

	@Override
	public synchronized void write(long devOffset, ByteBuffer src) throws IOException {
		//long time = System.currentTimeMillis();
		if (src.remaining() % blockSize != 0) {
			throw new IllegalArgumentException("src.remaining() must be multiple of blockSize!");
		}

		writeCommand.init((int) devOffset, src.remaining(), blockSize);
		//CustomLog.d(TAG, "writing: " + write);

		transferCommand(writeCommand, src);
		src.position(src.limit());

		//CustomLog.d(TAG, "write time: " + (System.currentTimeMillis() - time));
	}

	@Override
	public synchronized void write(UsbDeviceConnection usbDeviceConnection, long devOffset, ByteBuffer src) throws IOException {
		//long time = System.currentTimeMillis();
		if (src.remaining() % blockSize != 0) {
			throw new IllegalArgumentException("src.remaining() must be multiple of blockSize!");
		}

		writeCommand.init((int) devOffset, src.remaining(), blockSize);
		//CustomLog.d(TAG, "writing: " + write);

		transferCommand(usbDeviceConnection, writeCommand, src);
		src.position(src.limit());

		//CustomLog.d(TAG, "write time: " + (System.currentTimeMillis() - time));
	}

	@Override
	public int getBlockSize() {
		return blockSize;
	}
}
