package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.spearce.jgit.lib.Commit;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.lib.RepositoryConfig;

import java.io.File;
import java.util.Comparator;

/**
 * Commands that allows working with git repositories
 */
public class GitUtils {
  /**
   * Amount of characters disiplayed for in the display version of revision number
   */
  public static final int DISPLAY_VERSION_AMOUNT = 40;

  /**
   * Convert remote URL to JGIT form
   *
   * @param file the file to convert
   * @return the file URL recongnized by JGit
   */
  public static String toURL(File file) {
    return "file:///" + file.getAbsolutePath().replace(File.separatorChar, '/');
  }

  /**
   * The version comparator
   */
  public static final Comparator<String> VERSION_COMPATOR = new Comparator<String>() {
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
   * Ensures that a bare repository exists at the specified path.
   * If it does not, the directory is attempted to be created.
   *
   * @param dir    the path to the directory to init
   * @param remote the remote URL
   * @return a connection to repository
   * @throws VcsException if the there is a problem with accessing VCS
   */
  public static Repository getRepository(File dir, String remote) throws VcsException {
    final File parentFile = dir.getParentFile();
    if (!parentFile.exists()) {
      if (!parentFile.mkdirs()) {
        throw new VcsException("Unable to created parent directory: " + parentFile);
      }
    }
    boolean create = !dir.exists() || !new File(dir, "config").exists();
    if (!create && !dir.isDirectory()) {
      throw new VcsException("The specified path is not a directory: " + dir);
    }
    try {
      Repository r = new Repository(dir);
      if (create) {
        r.create();
        final RepositoryConfig config = r.getConfig();
        config.setString("core", null, "bare", "true");
        config.setString("teamcity", null, "remote", remote);
        config.save();
      } else {
        final RepositoryConfig config = r.getConfig();
        final String existingRemote = config.getString("teamcity", null, "remote");
        if (existingRemote != null && !remote.equals(existingRemote)) {
          throw new VcsException(
            "The specified directory " + dir + " is already used for another remote " + existingRemote + " and cannot be used for others.");
        }
      }
      return r;
    } catch (Exception ex) {
      throw new VcsException("The repository at " + dir + " cannot be openned or created: " + ex, ex);
    }
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
   * Make version from commit object
   *
   * @param c the commit object
   * @return the version string
   */
  public static String makeVersion(Commit c) {
    return makeVersion(c.getCommitId().name(), c.getCommitter().getWhen().getTime());
  }

  /**
   * Get user for the commit
   *
   * @param c the commit
   * @return the user name
   */
  public static String getUser(Commit c) {
    return c.getAuthor().getName() + " <" + c.getAuthor().getEmailAddress() + ">";
  }

  /**
   * Create display version for the commit
   *
   * @param c the commit to examine
   * @return the display version
   */
  public static String displayVersion(Commit c) {
    return displayVersion(c.getCommitId().name());
  }

  /**
   * Create display version for the commit
   *
   * @param version the version to examine
   * @return the display version
   */
  public static String displayVersion(String version) {
    return version.substring(0, DISPLAY_VERSION_AMOUNT);
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
}
