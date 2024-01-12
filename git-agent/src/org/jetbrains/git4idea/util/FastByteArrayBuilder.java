

package org.jetbrains.git4idea.util;

import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.LinkedList;

public class FastByteArrayBuilder {
  private final LinkedList<byte[]> buffers = new LinkedList<byte[]>();

  private int alreadyBufferedSize = 0;
  private int index = 0;

  private int nextBlockSize = 512;

  public void append(@NotNull final byte[] b) {
    append(b, 0, b.length);
  }

  public void append(byte datum) {
    byte[] buffer = buffers.peekLast();
    if (buffer == null || buffer.length == index) {
      buffer = addBuffer(1);
    }
    buffer[index++] = datum;
  }

  public void append(@NotNull byte[] data, int offset, int length) {
    if (offset < 0 || offset + length > data.length || length < 0) {
      throw new IndexOutOfBoundsException();
    }
    byte[] buffer = buffers.peekLast();

    if (buffer == null || buffer.length == index) {
      buffer = addBuffer(length);
    }

    if (index + length <= buffer.length) {
      System.arraycopy(data, offset, buffer, index, length);
      index += length;
      return;
    }

    int pos = offset;
    do {
      if (index == buffer.length) {
        buffer = addBuffer(length);
      }

      int copyLength = buffer.length - index;
      if (length < copyLength) {
        copyLength = length;
      }

      System.arraycopy(data, pos, buffer, index, copyLength);
      pos += copyLength;
      index += copyLength;
      length -= copyLength;
    } while (length > 0);
  }

  @Override
  public String toString() {
    byte[] last = buffers.peekLast();
    return "FastByteArrayBuilder{" +
           "size=" + size() +
           ", buffers.length=" + buffers.size() +
           ", buffers.last.length=" + (last != null ? last.length : null) +
           ", index=" + index +
           '}';
  }

  public int size() {
    return alreadyBufferedSize + index;
  }

  private byte[] toByteArrayUnsafe() {
    int totalSize = size();
    if (totalSize == 0) {
      return new byte[0];
    } else {
      resize(totalSize);
      return buffers.getFirst();
    }
  }

  public byte[] toByteArray() {
    byte[] bytesUnsafe = toByteArrayUnsafe();
    byte[] ret = new byte[bytesUnsafe.length];
    System.arraycopy(bytesUnsafe, 0, ret, 0, bytesUnsafe.length);
    return ret;
  }

  private void resize(int targetCapacity) {
    int currentSize = size();
    if (targetCapacity < currentSize) throw new IllegalArgumentException("New capacity must not be smaller than current size");

    if (buffers.peekFirst() == null) {
      // Nothing to resize
      nextBlockSize = targetCapacity;
      return;
    }

    if (currentSize == targetCapacity && buffers.getFirst().length == targetCapacity) {
      // Already resized
      return;
    }

    // Move everything into first buffer
    byte[] data = new byte[targetCapacity];
    int pos = 0;
    Iterator it = buffers.iterator();

    while (it.hasNext()) {
      byte[] bytes = (byte[])it.next();
      if (it.hasNext()) {
        System.arraycopy(bytes, 0, data, pos, bytes.length);
        pos += bytes.length;
      } else {
        System.arraycopy(bytes, 0, data, pos, index);
      }
    }

    buffers.clear();
    buffers.add(data);
    index = currentSize;
    alreadyBufferedSize = 0;
  }

  private byte[] addBuffer(int minCapacity) {
    if (buffers.peekLast() != null) {
      alreadyBufferedSize += index;
      index = 0;
    }

    if (nextBlockSize < minCapacity) {
      nextBlockSize = nextPowerOf2(minCapacity);
    }

    byte[] buffer = new byte[nextBlockSize];
    buffers.add(buffer);
    nextBlockSize *= 2;
    return buffer;
  }

  private static int nextPowerOf2(int val) {
    --val;
    val |= val >> 1;
    val |= val >> 2;
    val |= val >> 4;
    val |= val >> 8;
    val |= val >> 16;
    ++val;
    return val;
  }
}