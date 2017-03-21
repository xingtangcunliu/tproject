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
 * Represents the response of a read capacity request.
 * <p>
 * The response data is received in the data phase
 *
 * @author mjahnen
 * @see com.github.mjdev.libaums.driver.scsi.commands.ScsiReadCapacity
 */
public class ScsiReadCapacityResponse {

	private int logicalBlockAddress;
	private int blockLength;

	private ScsiReadCapacityResponse() {

	}

	public static ScsiReadCapacityResponse read(ByteBuffer buffer) {
		buffer.order(ByteOrder.BIG_ENDIAN);
		ScsiReadCapacityResponse res = new ScsiReadCapacityResponse();
		res.logicalBlockAddress = buffer.getInt();
		res.blockLength = buffer.getInt();
		return res;
	}

	public int getLogicalBlockAddress() {
		return logicalBlockAddress;
	}

	public int getBlockLength() {
		return blockLength;
	}
}