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

package com.github.mjdev.libaums.driver.scsi.commands;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * This class represents the response of a SCSI Inquiry. It holds various
 * information about the mass storage device.
 * <p>
 * This response is received in the data phase.
 *
 * @author mjahnen
 * @see com.github.mjdev.libaums.driver.scsi.commands.ScsiInquiry
 */
public class ScsiInquiryResponse {

	private byte peripheralQualifier;
	private byte peripheralDeviceType;
	boolean removableMedia;
	byte spcVersion;
	byte responseDataFormat;

	private ScsiInquiryResponse() {

	}

	public static ScsiInquiryResponse read(ByteBuffer buffer) {
		ScsiInquiryResponse response = new ScsiInquiryResponse();
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		byte b = buffer.get();
		response.peripheralQualifier = (byte) (b & (byte) 0xe0);
		response.peripheralDeviceType = (byte) (b & (byte) 0x1f);
		response.removableMedia = buffer.get() == 0x80;
		response.spcVersion = buffer.get();
		response.responseDataFormat = (byte) (buffer.get() & (byte) 0x7);
		return response;
	}

	public byte getPeripheralQualifier() {
		return peripheralQualifier;
	}

	public byte getPeripheralDeviceType() {
		return peripheralDeviceType;
	}

	public boolean isRemovableMedia() {
		return removableMedia;
	}

	public byte getSpcVersion() {
		return spcVersion;
	}

	public byte getResponseDataFormat() {
		return responseDataFormat;
	}

	@Override
	public String toString() {
		return "ScsiInquiryResponse [peripheralQualifier=" + peripheralQualifier
				+ ", peripheralDeviceType=" + peripheralDeviceType + ", removableMedia="
				+ removableMedia + ", spcVersion=" + spcVersion + ", responseDataFormat="
				+ responseDataFormat + "]";
	}
}
