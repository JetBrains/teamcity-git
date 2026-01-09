

package jetbrains.buildServer.buildTriggers.vcs.git;

import java.text.MessageFormat;
import java.text.ParseException;
import java.util.Locale;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

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
  /**
   * Will become MIN in the next release
   */
  public static final GitVersion DEPRECATED = new GitVersion(2, 10, 0, 0);

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

  // see FORMAT_4
  public static GitVersion fromString(@NotNull String version) {
    return parse("git version " + version);
  }


  public boolean isGreaterThan(final GitVersion other) {
    return compareTo(other) > 0;
  }

  @TestOnly
  public GitVersion previousVersion() {
    return new GitVersion(myMajor, myMinor, myRevision - 1);
  }

  public static final GitVersion GIT_VERSION_2_29 = new GitVersion(2, 29, 0);

  // --stdin option was added to git fetch command in version 2.29.0
  // https://git-scm.com/docs/git-fetch#Documentation/git-fetch.txt---stdin
  public static boolean fetchSupportsStdin(@NotNull GitVersion version) {
    return !version.isLessThan(GIT_VERSION_2_29);
  }

  public static boolean isNoShowForcedUpdatesSupported(@NotNull GitVersion version) {
    return !version.isLessThan(new GitVersion(2, 23, 0));
  }

  public static boolean negativeRefSpecSupported(@NotNull GitVersion version) {
    return !version.isLessThan(GIT_VERSION_2_29);
  }
}