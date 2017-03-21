/*
 * $Id$
 *
 * Copyright (C) 2003-2015 JNode.org
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but 
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public 
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; If not, write to the Free Software Foundation, Inc., 
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */
 
package org.jnode.fs.ntfs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.jnode.fs.ntfs.attribute.AttributeListAttribute;
import org.jnode.fs.ntfs.attribute.AttributeListEntry;
import org.jnode.fs.ntfs.attribute.NTFSAttribute;
import org.jnode.fs.ntfs.attribute.NTFSNonResidentAttribute;
import org.jnode.fs.ntfs.attribute.NTFSResidentAttribute;
import org.jnode.util.NumberUtils;

/**
 * MFT file record structure.
 *
 * @author Chira
 * @author Ewout Prangsma (epr@users.sourceforge.net)
 * @author Daniel Noll (daniel@noll.id.au) (new attribute iteration support)
 */
public class FileRecord extends NTFSRecord {

    /**
     * The volume this record is a part of.
     */
    private final NTFSVolume volume;

    /**
     * The cluster size for the volume containing this record.
     */
    private final int clusterSize;

    /**
     * Index of the file record within the MFT.
     */
    private long referenceNumber;

    /**
     * Cached attribute list attribute.
     */
    protected AttributeListAttribute attributeListAttribute;

    /**
     * The stored attributes.
     */
    protected List<NTFSAttribute> storedAttributeList;

    /**
     * A cached copy of the full list of attributes.
     */
    protected List<NTFSAttribute> attributeList;

    /**
     * Cached standard information attribute.
     */
    private StandardInformationAttribute standardInformationAttribute;

    /**
     * Cached file name attribute.
     */
    private FileNameAttribute fileNameAttribute;

    /**
     * Initialize this instance.
     *
     * @param volume          reference to the NTFS volume.
     * @param referenceNumber the reference number of the file within the MFT.
     * @param buffer          data buffer.
     * @param offset          offset into the buffer.
     */
    public FileRecord(NTFSVolume volume, long referenceNumber, byte[] buffer, int offset) throws IOException {
        this(volume, volume.getBootRecord().getBytesPerSector(), volume.getClusterSize(), true, referenceNumber,
            buffer, offset);
    }

    /**
     * Initialize this instance.
     *
     * @param volume          reference to the NTFS volume.
     * @param bytesPerSector  the number of bytes-per-sector in this volume.
     * @param clusterSize     the cluster size for the volume containing this record.
     * @param strictFixUp     indicates whether an exception should be throw if fix-up values don't match.
     * @param referenceNumber the reference number of the file within the MFT.
     * @param buffer          data buffer.
     * @param offset          offset into the buffer.
     */
    public FileRecord(NTFSVolume volume, int bytesPerSector, int clusterSize, boolean strictFixUp, long referenceNumber,
                      byte[] buffer, int offset) throws IOException {

        super(bytesPerSector, strictFixUp, buffer, offset);

        this.volume = volume;
        this.clusterSize = clusterSize;
        this.referenceNumber = referenceNumber;

        storedAttributeList = readStoredAttributes();

        // Linux NTFS docs say there can only be one of these, so I'll believe them.
        attributeListAttribute = (AttributeListAttribute) findStoredAttributeByType(NTFSAttribute.Types.ATTRIBUTE_LIST);
    }

    /**
     * Checks if the record appears to be valid.
     *
     * @throws IOException if an e occurs.
     */
    public void checkIfValid() throws IOException {
        // check for the magic number to see if we have a filerecord
        if (getMagic() != Magic.FILE) {
            log.debug("Invalid magic number found for FILE record: " + getMagic() + " -- dumping buffer");
            for (int off = 0; off < getBuffer().length; off += 32) {
                StringBuilder builder = new StringBuilder();
                for (int i = off; i < off + 32 && i < getBuffer().length; i++) {
                    String hex = Integer.toHexString(getBuffer()[i]);
                    while (hex.length() < 2) {
                        hex = '0' + hex;
                    }

                    builder.append(' ').append(hex);
                }
                log.debug(builder.toString());
            }

            throw new IOException("Invalid magic found: " + getMagic());
        }

        // This additional sanity check is possible if the record also contains the MFT number.
        // Helps catch bugs where a record is being read from the wrong offset.
        final long storedReferenceNumber = getStoredReferenceNumber();
        if (storedReferenceNumber >= 0 && referenceNumber != storedReferenceNumber) {
            throw new IOException("Stored reference number " + getStoredReferenceNumber()
                + " does not match reference number " + referenceNumber);
        }
    }

    /**
     * The volume this record is a part of
     */
    public NTFSVolume getVolume() {
        return volume;
    }

    /**
     * The cluster size for the volume containing this record.
     */
    public int getClusterSize() {
        return clusterSize;
    }

    /**
     * Gets the allocated size of the FILE record in bytes.
     *
     * @return Returns the allocated size.
     */
    public long getAllocatedSize() {
        return getUInt32(0x1C);
    }

    /**
     * Gets the reference number of the base record. For continuation MFT entries this will reference the main record.
     * For main records this should match {@link #referenceNumber}.
     *
     * @return Returns the base reference number.
     */
    public long getBaseReferenceNumber() {
        return getUInt48(0x20);
    }

    /**
     * Gets the real size of the FILE record in bytes.
     *
     * @return Returns the realSize.
     */
    public long getRealSize() {
        return getUInt32(0x18);
    }

    /**
     * Is this record in use?
     *
     * @return {@code true} if the record is in use.
     */
    public boolean isInUse() {
        return (getFlags() & 0x01) != 0;
    }

    /**
     * Is this a directory?
     *
     * @return {@code true} if the record is a directory.
     */
    public boolean isDirectory() {
        return (getFlags() & 0x02) != 0;
    }

    /**
     * Gets the hard link count.
     *
     * @return Returns the hardLinkCount.
     */
    public int getHardLinkCount() {
        return getUInt16(0x12);
    }

    /**
     * Gets the byte offset to the first attribute in this mft record from the start of the mft record.
     *
     * @return the first attribute offset.
     */
    public int getFirstAttributeOffset() {
        return getUInt16(0x14);
    }

    /**
     * Gets the flags.
     *
     * @return Returns the flags.
     */
    public int getFlags() {
        return getUInt16(0x16);
    }

    /**
     * Gets the Next Attribute Id.
     *
     * @return Returns the nextAttributeID.
     */
    public int getNextAttributeID() {
        return getUInt16(0x28);
    }

    /**
     * Gets the $LogFile sequence number.
     *
     * @return the $LogFile sequence number.
     */
    public long getLsn() {
        return getInt64(0x08);
    }

    /**
     * Gets the number of times this mft record has been reused.
     *
     * @return Returns the sequenceNumber.
     */
    public int getSequenceNumber() {
        return getUInt16(0x10);
    }

    /**
     * Gets the reference number of this record within the MFT. This value is not actually stored in the record, but
     * passed in from the outside.
     *
     * @return the reference number.
     */
    public long getReferenceNumber() {
        return referenceNumber;
    }

    /**
     * @return Returns the updateSequenceOffset.
     */
    public int getUpdateSequenceOffset() {
        return getUInt16(0x4);
    }

    /**
     * Gets the stored reference number. This can be compared against the reference number to confirm that the correct
     * file record was returned, however it is not available on all versions of NTFS, and even on recent versions some
     * MFT records lack it.
     *
     * @return the stored file reference number, or {@code -1} if it is not stored.
     */
    public long getStoredReferenceNumber() {
        // Expected to be 0x2A pre-XP.
        if (getUpdateSequenceOffset() >= 0x30) {
            return getUInt32(0x2C);
        } else {
            return -1;
        }
    }

    /**
     * Gets the name of this file.
     *
     * @return the filename.
     */
    public String getFileName() {
        final FileNameAttribute fnAttr = getFileNameAttribute();
        if (fnAttr != null) {
            return fnAttr.getFileName();
        } else {
            return null;
        }
    }

    /**
     * Gets the standard information attribute for this file record.
     *
     * @return the standard information attribute.
     */
    public StandardInformationAttribute getStandardInformationAttribute() {
        if (standardInformationAttribute == null) {
            standardInformationAttribute =
                (StandardInformationAttribute) findAttributeByType(NTFSAttribute.Types.STANDARD_INFORMATION);
        }
        return standardInformationAttribute;
    }

    /**
     * Gets the file name attribute for this file record.
     *
     * @return the file name attribute.
     */
    public FileNameAttribute getFileNameAttribute() {
        if (fileNameAttribute == null) {
            Iterator<NTFSAttribute> iterator = findAttributesByType(NTFSAttribute.Types.FILE_NAME);

            // Search for a Win32 file name if possible
            while (iterator.hasNext()) {
                NTFSAttribute attribute = iterator.next();

                if (fileNameAttribute == null ||
                    fileNameAttribute.getNameSpace() != FileNameAttribute.NameSpace.WIN32) {
                    fileNameAttribute = (FileNameAttribute) attribute;
                }
            }
        }
        return fileNameAttribute;
    }

    /**
     * Gets the attributes stored in this file record.
     *
     * @return an iterator over attributes stored in this file record.
     */
    public List<NTFSAttribute> getAllStoredAttributes() {
        return storedAttributeList;
    }

    /**
     * Finds a single stored attribute by ID.
     *
     * @param id the ID.
     * @return the attribute found, or {@code null} if not found.
     */
    private NTFSAttribute findStoredAttributeByID(int id) {
        for (NTFSAttribute attr : storedAttributeList) {
            if (attr != null && attr.getAttributeID() == id) {
                return attr;
            }
        }
        return null;
    }

    /**
     * Finds a single stored attribute by type.
     *
     * @param typeID the type ID
     * @return the attribute found, or {@code null} if not found.
     * @see NTFSAttribute.Types
     */
    private NTFSAttribute findStoredAttributeByType(int typeID) {
        for (NTFSAttribute attr : storedAttributeList) {
            if (attr != null && attr.getAttributeType() == typeID) {
                return attr;
            }
        }
        return null;
    }

    /**
     * Gets a collection of all attributes in this file record, including any attributes
     * which are stored in other file records referenced from an $ATTRIBUTE_LIST attribute.
     *
     * @return a collection of all attributes.
     */
    public synchronized List<NTFSAttribute> getAllAttributes() {
        if (attributeList == null) {
            attributeList = new ArrayList<NTFSAttribute>();

            try {
                if (attributeListAttribute == null) {
                    log.debug("All attributes stored");
                    attributeList = new ArrayList<NTFSAttribute>(getAllStoredAttributes());
                } else {
                    log.debug("Attributes in attribute list");
                    readAttributeListAttributes();
                }
            } catch (Exception e) {
                log.error("Error getting attributes for entry: " + this, e);
            }
        }

        return attributeList;
    }

    /**
     * Gets the first attribute in this filerecord with a given type.
     *
     * @param attrTypeID the type ID of the attribute we're looking for.
     * @return the attribute.
     */
    public NTFSAttribute findAttributeByType(int attrTypeID) {
        log.debug("findAttributeByType(0x" + NumberUtils.hex(attrTypeID, 4) + ")");

        for (NTFSAttribute attr : getAllAttributes()) {
            if (attr.getAttributeType() == attrTypeID) {
                log.debug("findAttributeByType(0x" + NumberUtils.hex(attrTypeID, 4) + ") found");
                return attr;
            }
        }

        log.debug("findAttributeByType(0x" + NumberUtils.hex(attrTypeID, 4) + ") not found");
        return null;
    }

    /**
     * Gets attributes in this file record with a given type.
     *
     * @param attrTypeID the type ID of the attribute we're looking for.
     * @return an iterator for the matching the attributes.
     */
    public Iterator<NTFSAttribute> findAttributesByType(final int attrTypeID) {
        log.debug("findAttributesByType(0x" + NumberUtils.hex(attrTypeID, 4) + ")");

        return new FilteredAttributeIterator(getAllAttributes().iterator()) {
            @Override
            protected boolean matches(NTFSAttribute attr) {
                return attr.getAttributeType() == attrTypeID;
            }
        };
    }

    /**
     * Gets attributes in this file record with a given type and name.
     *
     * @param attrTypeID the type ID of the attribute we're looking for.
     * @param name       the name to look for.
     * @return an iterator for the matching the attributes.
     */
    public Iterator<NTFSAttribute> findAttributesByTypeAndName(final int attrTypeID, final String name) {
        log.debug("findAttributesByTypeAndName(0x" + NumberUtils.hex(attrTypeID, 4) + "," + name + ")");
        return new FilteredAttributeIterator(getAllAttributes().iterator()) {
            @Override
            protected boolean matches(NTFSAttribute attr) {
                if (attr.getAttributeType() == attrTypeID) {
                    String attrName = attr.getAttributeName();
                    if (name == null ? attrName == null : name.equals(attrName)) {
                        log.debug("findAttributesByTypeAndName(0x" + NumberUtils.hex(attrTypeID, 4) + "," + name
                            + ") found");
                        return true;
                    }
                }
                return false;
            }
        };
    }

    /**
     * Gets the total size used for the given attribute. Often the directory index entry and the FileRecord will have
     * stale values for the file length, so checking the length of the {@link NTFSAttribute.Types#DATA} attribute is the
     * most reliable way to get the actual file length.
     *
     * @param attrTypeID the type of attribute to get the size for, e.g. {@link NTFSAttribute.Types#DATA}.
     * @param name       the name of the attribute or {@code null} for no name.
     * @return the total size of the attribute.
     */
    public long getAttributeTotalSize(int attrTypeID, String name) {
        Iterator<NTFSAttribute> attributes = findAttributesByTypeAndName(attrTypeID, name);

        if (!attributes.hasNext()) {
            throw new IllegalStateException("Failed to find an attribute with type: " + attrTypeID + " and name: '" +
                name + "'");
        } else {
            NTFSAttribute attribute = attributes.next();

            if (attribute.isResident()) {
                // If the attribute is resident it should be the only attribute of that type present, so just return
                // the length
                return ((NTFSResidentAttribute) attribute).getAttributeLength();
            } else {
                // The total length seems to be stored in the first attribute of a certain type. E.g. if there are two
                // DATA attributes each with data runs, the first one has the total length, and the intermediate ones
                // seem to contain the length of that particular attribute. So here just return the length of the first
                // attribute
                return ((NTFSNonResidentAttribute) attribute).getAttributeActualSize();
            }
        }
    }

    /**
     * Reads data from the file.
     *
     * @param fileOffset the offset into the file.
     * @param dest       the destination byte array into which to copy the file data.
     * @param off        the offset into the destination byte array.
     * @param len        the number of bytes of data to read.
     * @throws IOException if an e occurs reading from the filesystem.
     */
    public void readData(long fileOffset, byte[] dest, int off, int len) throws IOException {
        // Explicitly look for the attribute with no name, to avoid getting alternate streams.
        readData(NTFSAttribute.Types.DATA, null, fileOffset, dest, off, len, true);
    }

    /**
     * Reads data from the file.
     *
     * @param attributeType the attribute type to read from.
     * @param streamName the stream name to read from, or {@code null} to read from the default stream.
     * @param fileOffset the offset into the file.
     * @param dest       the destination byte array into which to copy the file data.
     * @param off        the offset into the destination byte array.
     * @param len        the number of bytes of data to read.
     * @param limitToInitialised {@code true} if the data read in should be limited to the initalised part of the
     *                    attribute.
     * @throws IOException if an e occurs reading from the filesystem.
     */
    public void readData(int attributeType, String streamName, long fileOffset, byte[] dest, int off, int len,
                         boolean limitToInitialised)
        throws IOException {

        if (log.isDebugEnabled()) {
            log.debug("readData: offset " + fileOffset + " attr:" + attributeType + " stream: " + streamName +
                " length " + len + ", file record = " + this);
        }

        if (len == 0) {
            return;
        }

        final Iterator<NTFSAttribute> dataAttrs = findAttributesByTypeAndName(attributeType, streamName);

        if (!dataAttrs.hasNext()) {
            throw new IOException(attributeType + " attribute not found, file record = " + this);
        }

        NTFSAttribute attr = dataAttrs.next();
        if (attr.isResident()) {
            if (dataAttrs.hasNext()) {
                throw new IOException("Resident attribute should be by itself, file record = " + this);
            }

            final NTFSResidentAttribute resData = (NTFSResidentAttribute) attr;
            final int attrLength = resData.getAttributeLength();
            if (attrLength < len) {
                throw new IOException("File data(" + attrLength + "b) is not large enough to read:" + len + "b");
            }
            resData.getData(resData.getAttributeOffset() + (int) fileOffset, dest, off, len);

            if (log.isDebugEnabled()) {
                log.debug("readData: read from resident data");
            }

            return;
        }

        // At this point we know that at least the first attribute is non-resident...

        // Grab the initialised size (if that is itself initialised)
        long initialisedSize = ((NTFSNonResidentAttribute) attr).getAttributeInitializedSize();
        if (initialisedSize == 0)
        {
            limitToInitialised = false;
        }

        // calculate start and end cluster
        final int clusterSize = getClusterSize();
        final long startCluster = fileOffset / clusterSize;
        final long endCluster = (fileOffset + len - 1) / clusterSize;
        final int nrClusters = (int) (endCluster - startCluster + 1);
        final byte[] tmp = new byte[nrClusters * clusterSize];

        long clusterOffset = 0;
        long clusterWithinNresData = startCluster;
        int readClusters = 0;
        while (true) {
            if (attr.isResident()) {
                throw new IOException("Resident attribute should be by itself, file record = " + this);
            }

            final NTFSNonResidentAttribute nresData = (NTFSNonResidentAttribute) attr;

            readClusters += nresData.readVCN(clusterWithinNresData, tmp, 0, nrClusters);

            if (readClusters > 0) {
                // If if the data is past the 'initialised' part of the attribute. If it is uninitialised then it must
                // be read as zeros. Annoyingly the initialised portion isn't even cluster aligned...
                long endOffset = (clusterOffset + clusterWithinNresData + nrClusters) * clusterSize;


                if (endOffset > initialisedSize && limitToInitialised) {
                    int delta = (int)(endOffset - initialisedSize);
                    int startIndex = Math.max((int)(tmp.length - delta), 0);

                    if (startIndex < tmp.length) {
                        Arrays.fill(tmp, startIndex, tmp.length, (byte) 0);
                    }
                }
            }

            if (readClusters == nrClusters) {
                // Already done.
                break;
            }

            // When there are multiple attributes, the data in each one claims to start at VCN 0.
            // Clearly this is not the case, so we need to offset when we read.
            clusterWithinNresData -= nresData.getNumberOfVCNs();
            clusterOffset += nresData.getNumberOfVCNs();

            if (dataAttrs.hasNext()) {
                attr = dataAttrs.next();
            } else {
                break;
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("readData: read " + readClusters + " from non-resident attributes");
        }

        if (readClusters != nrClusters) {
            throw new IOException("Requested " + nrClusters + " clusters but only read " + readClusters +
                ", file offset = " + fileOffset + ", file record = " + this);
        }

        System.arraycopy(tmp, (int) (fileOffset % clusterSize), dest, off, len);
    }

    @Override
    public String toString() {
        if (isInUse()) {
            return String.format("FileRecord [%d fileName='%s']", referenceNumber, getFileName());
        } else {
            return String.format("FileRecord [%d unused]", referenceNumber);
        }
    }

    /**
     * Reads in all attributes referenced by the attribute-list attribute.
     */
    private synchronized void readAttributeListAttributes() {
        Iterator<AttributeListEntry> entryIterator;

        try {
            entryIterator = attributeListAttribute.getAllEntries();
        } catch (Exception e) {
            throw new IllegalStateException("Error getting attributes from attribute list, file record " +
                FileRecord.this, e);
        }

        Map<Integer, NTFSNonResidentAttribute> compressedByType =
            new LinkedHashMap<Integer, NTFSNonResidentAttribute>();

        while (entryIterator.hasNext()) {
            AttributeListEntry entry = entryIterator.next();

            try {
                // If it's resident (i.e. in the current file record) then we don't need to
                // look it up, and doing so would risk infinite recursion.
                NTFSAttribute attribute;
                if (entry.getFileReferenceNumber() == referenceNumber) {
                    attribute = findStoredAttributeByID(entry.getAttributeID());
                } else {
                    log.debug("Looking up MFT entry for: " + entry.getFileReferenceNumber());

                    // When reading the MFT itself don't attempt to check the index is in range (we won't know the total
                    // MFT length yet)
                    MasterFileTable mft = getVolume().getMFT();
                    FileRecord holdingRecord = getReferenceNumber() == MasterFileTable.SystemFiles.MFT
                        ? mft.getRecordUnchecked(entry.getFileReferenceNumber())
                        : mft.getRecord(entry.getFileReferenceNumber());

                    attribute = holdingRecord.findStoredAttributeByID(entry.getAttributeID());

                    if (attribute == null) {
                        log.error(String.format("Failed to find an attribute matching entry '%s' in the holding record", entry));
                        continue;
                    } else if (!attribute.isResident() && attribute.isCompressedAttribute() &&
                        compressedByType.containsKey(attribute.getAttributeType())) {

                        // Get the fallback compression unit
                        NTFSNonResidentAttribute firstAttribute = compressedByType.get(attribute.getAttributeType());
                        int fallbackCompressionUnit = 1 << firstAttribute.getStoredCompressionUnitSize();

                        // Re-read the attribute with the fallback compression unit
                        attribute = NTFSAttribute.getAttribute(attribute.getFileRecord(), attribute.getOffset(),
                            fallbackCompressionUnit);
                    }
                }

                // Record the first compressed attribute of each type
                if (!attribute.isResident() && attribute.isCompressedAttribute() &&
                    !compressedByType.containsKey(attribute.getAttributeType())) {

                    compressedByType.put(attribute.getAttributeType(), (NTFSNonResidentAttribute) attribute);
                }

                if (log.isDebugEnabled()) {
                    log.debug("Attribute: " + attribute);
                }

                attributeList.add(attribute);
            } catch (Exception e) {
                throw new IllegalStateException("Error getting MFT or FileRecord for attribute in list, ref = 0x" +
                    Long.toHexString(entry.getFileReferenceNumber()), e);
            }
        }
    }

    /**
     * Reads in the stored attributes.
     *
     * @return the stored attributes.
     */
    private List<NTFSAttribute> readStoredAttributes() {
        List<NTFSAttribute> attributes = new ArrayList<NTFSAttribute>();
        int offset = getFirstAttributeOffset();

        while (true) {
            int type = getUInt32AsInt(offset);

            if (type == 0xFFFFFFFF) {
                // Normal end of list condition.
                break;
            } else {
                NTFSAttribute attribute = NTFSAttribute.getAttribute(FileRecord.this, offset);

                if (log.isDebugEnabled()) {
                    log.debug("Attribute: " + attribute.toDebugString());
                }

                int offsetToNextOffset = getUInt32AsInt(offset + 0x04);
                if (offsetToNextOffset <= 0) {
                    log.debug("Non-positive offset, preventing infinite loop.  Data on disk may be corrupt.  "
                        + "referenceNumber = " + referenceNumber);
                    break;
                } else {
                    offset += offsetToNextOffset;
                    attributes.add(attribute);
                }
            }
        }

        return attributes;
    }

    /**
     * An iterator for filtering another iterator.
     */
    private abstract class FilteredAttributeIterator implements Iterator<NTFSAttribute> {
        private Iterator<NTFSAttribute> attributes;
        private NTFSAttribute cached;
        private boolean hasCached;

        private FilteredAttributeIterator(Iterator<NTFSAttribute> attributes) {
            this.attributes = attributes;
        }

        @Override
        public boolean hasNext() {
            if (hasCached) {
                return true;
            } else {
                nextMatch();
                return hasCached;
            }
        }

        @Override
        public NTFSAttribute next() {
            if (hasNext()) {
                hasCached = false;
                return cached;
            }

            throw new NoSuchElementException();
        }

        /**
         * Gets the next matching attribute.
         *
         * @return the next match.
         */
        private NTFSAttribute nextMatch() {
            while (attributes.hasNext()) {
                NTFSAttribute attribute = attributes.next();

                if (matches(attribute)) {
                    hasCached = true;
                    cached = attribute;
                    return attribute;
                }
            }

            hasCached = false;
            return null;
        }

        /**
         * Implemented by subclasses to perform matching logic.
         *
         * @param attr the attribute.
         * @return {@code true} if it matches, {@code false} otherwise.
         */
        protected abstract boolean matches(NTFSAttribute attr);

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
