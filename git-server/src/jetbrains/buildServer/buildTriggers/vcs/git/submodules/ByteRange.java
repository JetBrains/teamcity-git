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

package jetbrains.buildServer.buildTriggers.vcs.git.submodules;

/**
 * The range of bytes. Contains methods suitable for checking membership in a hash set.
 * The class assumes that byte array does not changes after the construction.
 */
public class ByteRange implements Comparable<ByteRange> {
  /**
   * The data for the range
   */
  private final byte[] myData;
  /**
   * The start of the range
   */
  private final int myStart;
  /**
   * The position after the last byte
   */
  private final int myLength;
  /**
   * The hash code
   */
  private int myHash;

  /**
   * The constructor
   *
   * @param data   the bytes for the range
   * @param start  the start of the range
   * @param length the length of the range
   */
  public ByteRange(byte[] data, int start, int length) {
    assert start >= 0;
    assert length >= 0;
    assert start + length <= data.length;
    myData = data;
    myStart = start;
    myLength = length;
    int hash = 0;
    for (int i = myStart; i < start + length; i++) {
      hash += hash * 19 + data[i];
    }
    myHash = hash;

  }

  /**
   * A constructor from the entire array
   *
   * @param bytes the array with characters
   */
  public ByteRange(byte[] bytes) {
    this(bytes, 0, bytes.length);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int hashCode() {
    return myHash;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof ByteRange)) {
      return false;
    }
    final ByteRange r = (ByteRange)obj;
    if (myHash != r.myHash || myLength != r.myLength) {
      return false;
    }
    for (int i = 0; i < myLength; i++) {
      if (myData[myStart + i] != r.myData[r.myStart + i]) {
        return false;
      }
    }
    return true;
  }

  /**
   * Compare byte ranges treating bytes by absolute values
   * {@inheritDoc}
   */
  public int compareTo(ByteRange o) {
    int n = Math.min(myLength, o.myLength);
    for (int i = 0; i < n; i++) {
      int d = myData[myStart + i] - o.myData[o.myStart + i];
      if (d != 0) {
        return d;
      }
    }
    return myLength - o.myLength;
  }
}
