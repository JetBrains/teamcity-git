/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package jetbrains.buildServer.buildTriggers.vcs.git;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Comparator;

/**
 * Commands that allows working with git repositories
 */
public class GitUtils {
  /**
   * The UTF8 character set
   */
  public static final Charset UTF8 = Charset.forName("UTF-8");

  /**
   * Convert remote URL to JGIT form
   *
   * @param file the file to convert
   * @return the file URL recognized by JGit
   */
  public static String toURL(File file) {
    return "file:///" + file.getAbsolutePath().replace(File.separatorChar, '/');
  }

  /**
   * The version comparator
   */
  public static final Comparator<String> VERSION_COMPARATOR = new Comparator<String>() {
    public int compare(String o1, String o2) {
      long r = versionTime(o1) - versionTime(o2);
      return r < 0 ? -1 : r > 0 ? 1 : 0;
    }
  };

  /**
   * Make version string from revision hash and time
   *
   * @param revision the revision hash
   * @param time     the time of revision
   * @return the version string
   */
  @NotNull
  public static String makeVersion(@NotNull String revision, long time) {
    return revision + "@" + Long.toHexString(time);
  }

  /**
   * Extract revision number from the version
   *
   * @param version string
   * @return the revision number
   */
  @NotNull
  public static String versionRevision(@NotNull String version) {
    int i = version.indexOf('@');
    if (i == -1) {
      throw new IllegalArgumentException("Invalid format of version: " + version);
    }
    return version.substring(0, i);
  }

  /**
   * Extract revision number from the version
   *
   * @param version string
   * @return the revision number
   */
  public static long versionTime(@NotNull String version) {
    int i = version.indexOf('@');
    if (i == -1) {
      throw new IllegalArgumentException("Invalid format of version: " + version);
    }
    return Long.parseLong(version.substring(i + 1), 16);
  }

  /**
   * Create reference name from branch name
   *
   * @param branch the branch name
   * @return the reference name
   */
  public static String branchRef(String branch) {
    return "refs/heads/" + branch;
  }

  /**
   * Create remotes/origin reference name from branch name
   *
   * @param branch the branch name
   * @return the reference name
   */
  public static String remotesBranchRef(String branch) {
    return "refs/remotes/origin/" + branch;
  }

  /**
   * Convert Git path to a relative File
   *
   * @param path the path to covert
   * @return the {@link File} object
   */
  public static File toFile(String path) {
    return new File(path.replace('/', File.separatorChar));
  }

  /**
   * Ref name for the tag
   *
   * @param label the tag name
   * @return the reference name
   */
  public static String tagName(String label) {
    return "refs/tags/" + label;
  }

  /**
   * Get UTF8 bytes
   *
   * @param s a string to convert
   * @return a UTF8 bytes for the string
   */
  public static byte[] getUtf8(String s) {
    try {
      return s.getBytes(UTF8.name());
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("UTF-8 encoding not found", e);
    }
  }

  /**
   * Normalize path removing ".." and "." elements assuming "/" as separator
   *
   * @param path the path to normalize
   * @return the normalized path
   */
  public static String normalizePath(String path) {
    if (path.length() == 0 || path.equals("/")) {
      return path;
    }
    StringBuilder rc = new StringBuilder();
    String[] pc = path.split("/");
    int count = 0;
    int startBacks = 0;
    int[] pci = new int[pc.length];
    boolean startsWithSlash = path.charAt(0) == '/';
    for (int i = 0; i < pc.length; i++) {
      String f = pc[i];
      if (f.length() == 0 || ".".equals(f)) {
        // do nothing
      } else if ("..".equals(f)) {
        if (count == 0) {
          startBacks++;
        } else {
          count--;
        }
      } else {
        pci[count++] = i;
      }
    }
    for (int i = 0; i < startBacks; i++) {
      if (rc.length() != 0 || startsWithSlash) {
        rc.append('/');
      }
      rc.append("..");
    }
    for (int i = 0; i < count; i++) {
      int fi = pci[i];
      if (rc.length() != 0 || startsWithSlash) {
        rc.append('/');
      }
      rc.append(pc[fi]);
    }
    return rc.toString();
  }
}
