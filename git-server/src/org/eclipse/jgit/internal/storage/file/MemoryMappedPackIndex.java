/*
 * Copyright 2000-2018 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.eclipse.jgit.internal.storage.file;

import com.intellij.openapi.diagnostic.Logger;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.NB;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;

/**
 * Memory mapped implementation on {@link PackIndex} reading.
 * Stolen from Upsource.
 *
 * @see <a href="https://upsource.jetbrains.com/circlet/file/bcd361e152cbc90f09fe5d2751140da18d119862/vcs-hosting/vcs-server/git-backend/src/main/java/jetbrains/vcs/server/hosting/git/jgit/MemoryMappedPackIndex.kt">origin code</a>
 *
 * @author Mikhail Khorkov
 * @since 2018.2
 */
public class MemoryMappedPackIndex extends PackIndex.PackIndexFactory {

  private final static Logger LOG = Logger.getInstance(MemoryMappedPackIndex.class.getName());

  private static final long IS_O64 = 1L << 31;
  private static final int FANOUT = 256;
  private static final byte[] TOC = new byte[]{-1, "t".getBytes()[0], "O".getBytes()[0], "c".getBytes()[0]};

  public PackIndex open(final File idxFile) throws IOException {
    try {
      final FileChannel fc = FileChannel.open(idxFile.toPath(), StandardOpenOption.READ);
      final MappedByteBuffer buffer = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
      if (isV2Index(buffer)) {
        return new PackIndexV2MM(buffer, fc.size());
      } else {
        LOG.warn("Index " + idxFile.getPath() + " is not V2");
      }
    } catch (Throwable e) {
      LOG.warn("Reading memory-mapped index " + idxFile.getPath(), e);
      // use default method
    }

    return super.open(idxFile);
  }

  private boolean isV2Index(final MappedByteBuffer buffer) {
    final byte[] toc = new byte[4];
    buffer.get(toc, 0, toc.length);
    if (!Arrays.equals(toc, TOC)) {
      return false;
    }

    return decodeInt32(buffer) == 2;
  }

  /*
      - A 4-byte magic number \377tOc which is an unreasonable fanout[0] value.
      - A 4-byte version number (= 2)
      - A 256-entry fan-out table just like v1.
      - A table of sorted 20-byte SHA-1 object names. These are packed together without offset values to reduce the cache footprint of the binary search for a specific object name.
      - A table of 4-byte CRC32 values of the packed object data. This is new in v2 so compressed data can be copied directly from pack to pack during repacking without undetected data corruption.
      - A table of 4-byte offset values (in network byte order). These are usually 31-bit pack file offsets, but large offsets are encoded as an index into the next table with the msbit set.
      - A table of 8-byte offset entries (empty for pack files less than 2 GiB). Pack files are organized with heavily used objects toward the front, so most object references should not need to refer to this table.
      - A copy of the 20-byte SHA-1 checksum at the end of corresponding packfile.
      - 20-byte SHA-1-checksum of all of the above.
   */

  private class PackIndexV2MM extends PackIndex {
    private final MappedByteBuffer myBuffer;
    private final long mySize;
    private final int[] fanoutTable = new int[FANOUT];
    private final int objectCnt;

    private int shaOffset;
    private int crcOffset;
    private int ofsOffset;
    private int o64Offset;

    private volatile boolean isClosed = false;

    PackIndexV2MM(final MappedByteBuffer buffer, final long size) throws IOException {
      myBuffer = buffer;
      mySize = size;

      if (size >= Integer.MAX_VALUE) {
        throw new IOException("index file too large");
      }

      for (int i = 0; i < FANOUT; i++) {
        fanoutTable[i] = decodeInt32(myBuffer);
      }

      objectCnt = fanoutTable[FANOUT - 1];
      if (objectCnt < 0) {
        throw new IOException("More than 2G objects in index");
      }

      shaOffset = 4 + 4 + 256 * 4;
      crcOffset = shaOffset + objectCnt * 20;
      ofsOffset = crcOffset + objectCnt * 4;
      o64Offset = ofsOffset + objectCnt * 4;

      packChecksum = new byte[20];
      myBuffer.position((int)mySize - 40);
      myBuffer.get(packChecksum);
    }

    private void assertNotClosed() {
      if (isClosed) {
        throw new Error("PackIndex is closed");
      }
    }

    @Override
    public void close() {
      myBuffer.clear();

      try {
        // clean buffer using cleaner to free memory
        final Field field = myBuffer.getClass().getDeclaredField("cleaner");
        field.setAccessible(true);
        final Object cleaner = field.get(myBuffer);
        final Method cleanMethod = cleaner.getClass().getMethod("clean");
        cleanMethod.invoke(cleaner);
      } catch (Throwable e) {
        LOG.warnAndDebugDetails("Exception while cleaning memory pack index", e);
      }

      isClosed = true;
    }

    @Override
    public boolean hasCRC32Support() {
      return true;
    }

    @Override
    public long getOffset(final long nthPosition) {
      assertNotClosed();

      myBuffer.position(ofsOffset + (int)nthPosition * 4);
      long offset = decodeUInt32(myBuffer);

      if ((offset & IS_O64) != 0L) {
        myBuffer.position(o64Offset + (int)(offset & (~IS_O64)) * 8);
        return decodeUInt64(myBuffer);
      }

      return offset;
    }

    @Override
    public ObjectId getObjectId(final long nthPosition) {
      assertNotClosed();

      myBuffer.position(shaOffset + (int)nthPosition * 20);
      return ObjectId.fromRaw(MemoryMappedPackIndex.read(myBuffer, 20));
    }

    @Override
    public void resolve(final Set<ObjectId> matches, final AbbreviatedObjectId id, final int matchLimit) {
      assertNotClosed();

      int pos = binarySearch(id.getFirstByte(), id::prefixCompare);
      if (pos >= 0) {
        // We may have landed in the middle of the matches.  Move
        // backwards to the start of matches, then walk forwards.
        //
        while (0 < pos && (id.prefixCompare(getObjectId(pos - 1)) == 0)) {
          pos--;
        }
        while (pos < objectCnt && (id.prefixCompare(getObjectId(pos)) == 0)) {
          matches.add(getObjectId(pos));
          if (matches.size() > matchLimit) {
            break;
          }
          pos++;
        }
      }
    }

    @NotNull
    @Override
    public Iterator<MutableEntry> iterator() {
      assertNotClosed();

      class V2MutableEntry extends MutableEntry {

        private long position = 0;

        @Override
        public void ensureId() {
          idBuffer.fromObjectId(getObjectId(position));
        }

        public long getOffset() {
          return PackIndexV2MM.this.getOffset(position);
        }

        @Override
        public MutableEntry cloneEntry() {
          final V2MutableEntry entry = new V2MutableEntry();
          entry.position = this.position;
          entry.idBuffer.fromObjectId(idBuffer);
          return entry;
        }
      }

      return new PackIndex.EntriesIterator() {

        @Override
        protected MutableEntry initEntry() {
          return new V2MutableEntry();
        }

        /**
         * Implementation must update {@link #returnedNumber} before returning
         * element.
         */
        @Override
        public MutableEntry next() {
          if (returnedNumber < objectCnt) {
            returnedNumber++;
            ((V2MutableEntry)entry).position = returnedNumber - 1;
            return entry;
          }
          throw new NoSuchElementException();
        }
      };
    }

    @Override
    public long getOffset64Count() {
      // actually, it is quite expensive to calculate this
      throw new UnsupportedOperationException();
    }

    @Override
    public long findCRC32(final AnyObjectId objId) throws MissingObjectException {
      assertNotClosed();

      final int pos = binarySearch(objId);
      if (pos < 0) {
        throw new MissingObjectException(objId.copy(), "unknown");
      }

      myBuffer.position(crcOffset + pos * 4);
      return decodeUInt32(myBuffer);
    }

    @Override
    public long getObjectCount() {
      return (long)objectCnt;
    }

    @Override
    public long findOffset(final AnyObjectId objId) {
      assertNotClosed();

      final int pos = binarySearch(objId);
      if (pos < 0) {
        return -1;
      }

      return getOffset((long)pos);
    }

    // return < 0 if not found
    private int binarySearch(final AnyObjectId objId) {
      return binarySearch(objId.getFirstByte(), objId::compareTo);
    }

    private int binarySearch(int firstByte, Function<ObjectId, Integer> compare) {
      int low = firstByte == 0 ? 0 : fanoutTable[firstByte - 1];
      int high = fanoutTable[firstByte];

      while (low < high) {
        int mid = (low + high) >>> 1;
        int cmp = compare.apply(getObjectId((long)mid));
        if (cmp < 0) {
          high = mid;
        } else if (cmp == 0) {
          return mid;
        } else {
          low = mid + 1;
        }
      }

      return -1;
    }
  }

  private static int decodeInt32(final MappedByteBuffer buffer) {
    return NB.decodeInt32(read(buffer, 4), 0);
  }

  private static long decodeUInt32(final MappedByteBuffer buffer) {
    return NB.decodeUInt32(read(buffer, 4), 0);
  }

  private static long decodeUInt64(final MappedByteBuffer buffer) {
    return NB.decodeUInt64(read(buffer, 8), 0);
  }

  private static byte[] read(final MappedByteBuffer buffer, final int size) {
    final byte[] bytes = new byte[size];
    buffer.get(bytes);
    return bytes;
  }
}
