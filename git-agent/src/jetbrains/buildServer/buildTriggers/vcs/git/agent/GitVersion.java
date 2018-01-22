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

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.text.MessageFormat;
import java.text.ParseException;
import java.util.Locale;

/**
 * The version of the git. Note that the version number ignores build and commit hash.
 */
public final class GitVersion implements Comparable<GitVersion> {
  /**
   * The format of the "git version" (four components)
   */
  @NonNls private static final MessageFormat FORMAT_4 =
    new MessageFormat("git version {0,number,integer}.{1,number,integer}.{2,number,integer}.{3,number,integer}", Locale.US);
  /**
   * The format of the "git version" (three components)
   */
  @NonNls private static final MessageFormat FORMAT_3 =
    new MessageFormat("git version {0,number,integer}.{1,number,integer}.{2,number,integer}", Locale.US);
  /**
   * The minimal supported version
   */
  public static final GitVersion MIN = new GitVersion(1, 6, 4, 0);

  private final int myMajor;
  private final int myMinor;
  private final int myRevision;
  private final int myPatchLevel;

  public GitVersion(int major, int minor, int revision) {
    this(major, minor, revision, 0);
  }

  public GitVersion(int major, int minor, int revision, int patchLevel) {
    myMajor = major;
    myMinor = minor;
    myRevision = revision;
    myPatchLevel = patchLevel;
  }

  /**
   * Parse output of "git version" command
   *
   * @param version a a version number
   * @return a git version
   */
  public static GitVersion parse(String version) {
    try {
      Object[] parsed = FORMAT_4.parse(version);
      int major = ((Long)parsed[0]).intValue();
      int minor = ((Long)parsed[1]).intValue();
      int revision = ((Long)parsed[2]).intValue();
      int patchLevel = ((Long)parsed[3]).intValue();
      return new GitVersion(major, minor, revision, patchLevel);
    }
    catch (ParseException e) {
      try {
        Object[] parsed = FORMAT_3.parse(version);
        int major = ((Long)parsed[0]).intValue();
        int minor = ((Long)parsed[1]).intValue();
        int revision = ((Long)parsed[2]).intValue();
        int patchLevel = 0;
        return new GitVersion(major, minor, revision, patchLevel);
      }
      catch (ParseException ex) {
        throw new IllegalArgumentException("Unsupported format of git --version output: '" + version + "'");
      }
    }
  }

  /**
   * @return true if the version is supported by the plugin
   */
  public boolean isSupported() {
    return compareTo(MIN) >= 0;
  }


  public boolean isLessThan(@NotNull GitVersion other) {
    return compareTo(other) < 0;
  }

  @Override
  public boolean equals(final Object obj) {
    return obj instanceof GitVersion && compareTo((GitVersion)obj) == 0;
  }


  @Override
  public int hashCode() {
    return ((myMajor * 17 + myMinor) * 17 + myRevision) * 17 + myPatchLevel;
  }


  public int compareTo(final GitVersion o) {
    int d = myMajor - o.myMajor;
    if (d != 0) {
      return d;
    }
    d = myMinor - o.myMinor;
    if (d != 0) {
      return d;
    }
    d = myRevision - o.myRevision;
    if (d != 0) {
      return d;
    }
    return myPatchLevel - o.myPatchLevel;
  }


  @Override
  public String toString() {
    //noinspection ConcatenationWithEmptyString
    return "" + myMajor + "." + myMinor + "." + myRevision + "." + myPatchLevel;
  }


  public boolean isGreaterThan(final GitVersion other) {
    return compareTo(other) > 0;
  }

  @TestOnly
  public GitVersion previousVersion() {
    return new GitVersion(myMajor, myMinor, myRevision - 1);
  }
}
