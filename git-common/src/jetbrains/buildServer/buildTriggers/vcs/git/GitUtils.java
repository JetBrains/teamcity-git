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

package jetbrains.buildServer.buildTriggers.vcs.git;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.SystemInfo;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.SimpleCommandLineProcessRunner;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.VcsUtil;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;

/**
 * Commands that allows working with git repositories
 */
public class GitUtils {
  private static final String SSH_V2 = "SSH-2.0";

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
      return o1.compareTo(o2);
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
    if (i == -1)
      return version;
    return version.substring(0, i);
  }

  /**
   * Expands ref name to full ref name
   *
   * @param ref the ref name
   * @return full ref name
   */
  public static String expandRef(String ref) {
    if (ref.startsWith("refs/")) {
      return ref;
    } else {
      return "refs/heads/" + ref;
    }
  }

  public static String getShortBranchName(@NotNull String fullRefName) {
    if (isRegularBranch(fullRefName))
      return fullRefName.substring(Constants.R_HEADS.length());
    return fullRefName;
  }

  public static boolean isTag(@NotNull Ref ref) {
    return isTag(ref.getName());
  }

  public static boolean isTag(@NotNull String fullRefName) {
    return fullRefName.startsWith(Constants.R_TAGS);
  }

  public static boolean isRegularBranch(@NotNull String fullRefName) {
    return fullRefName.startsWith(Constants.R_HEADS);
  }

  /**
   * Creates remote ref from local ref name for remote called 'origin'
   *
   * @param ref local ref name
   * @return full remote ref name
   */
  public static String createRemoteRef(String ref) {
    if (ref.startsWith("refs/")) {
      if (ref.startsWith("refs/heads/")) {
        return "refs/remotes/origin/" + ref.substring("refs/heads/".length());
      } else {
        return ref;
      }
    } else {
      return "refs/remotes/origin/" + ref;
    }
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


  public static boolean isAnonymousGitWithUsername(@NotNull URIish uri) {
    return "git".equals(uri.getScheme()) && uri.getUser() != null;
  }

  /**
   * Returns build parameter name with the vcs branch name for given
   * git VCS root
   */
  public static String getGitRootBranchParamName(@NotNull VcsRoot root) {
    return jetbrains.buildServer.buildTriggers.vcs.git.Constants.GIT_ROOT_BUILD_BRANCH_PREFIX + VcsUtil.getSimplifiedName(root);
  }


  public static File getGitDir(@NotNull File workingTreeDir) throws IOException, VcsException {
    File dotGit = new File(workingTreeDir, ".git");
    if (dotGit.isFile()) {
      List<String> content = FileUtil.readFile(dotGit);
      if (content.isEmpty())
        throw new VcsException("Empty " + dotGit.getAbsolutePath());
      String line = content.get(0);
      if (!line.startsWith("gitdir:"))
        throw new VcsException("Wrong format of " + dotGit.getAbsolutePath() + ": " + content);
      String gitDirPath = line.substring("gitdir:".length()).trim();
      File gitDir = new File(gitDirPath);
      if (gitDir.isAbsolute())
        return gitDir;
      return new File(workingTreeDir, gitDirPath);
    }
    return dotGit;
  }

  @NotNull
  public static String getRevision(@NotNull final Ref ref) {
    return getObjectId(ref).name();
  }

  @NotNull
  public static ObjectId getObjectId(@NotNull final Ref ref) {
    if (isTag(ref) && ref.getPeeledObjectId() != null)
      return ref.getPeeledObjectId();
    return ref.getObjectId();
  }


  /**
   * Returns short file name for the given file on windows. The file must exist.
   * @param f file of interest
   * @return see above
   * @throws UnsupportedOperationException if called on non windows platform
   * @throws IOException in case of IO error
   */
  public static String getShortFileName(@NotNull File f) throws UnsupportedOperationException, IOException {
    if (!SystemInfo.isWindows)
      throw new UnsupportedOperationException();

    GeneralCommandLine cmd = new GeneralCommandLine();
    cmd.setExePath("cmd.exe");
    cmd.addParameters("/c", "for %F in (\"" + f.getCanonicalPath() + "\") do @echo %~sF");

    ExecResult res = SimpleCommandLineProcessRunner.runCommand(cmd, null);
    Throwable error = res.getException();
    if (error != null)
      throw new IOException(error);

    return res.getStdout().trim();
  }


  @NotNull
  public static String getSshClientVersion(@NotNull String originalSshClientVersion, @NotNull String teamcityVersion) {
    if (!originalSshClientVersion.startsWith(SSH_V2))
      return originalSshClientVersion;
    //rfc4253-4.2
    return SSH_V2 + "-" + teamcityVersion.replace(' ', '-') + originalSshClientVersion.substring(SSH_V2.length());
  }
}
