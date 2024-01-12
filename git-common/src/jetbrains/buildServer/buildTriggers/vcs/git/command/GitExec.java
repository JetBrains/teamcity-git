

package jetbrains.buildServer.buildTriggers.vcs.git.command;

import jetbrains.buildServer.buildTriggers.vcs.git.GitVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GitExec {

  private final String myPath;
  private final GitVersion myVersion;
  private final String myCygwinBinPath;

  public GitExec(@NotNull String path, @NotNull GitVersion version) {
    this(path, version, null);
  }

  public GitExec(@NotNull String path,
                 @NotNull GitVersion version,
                 @Nullable String cygwinBinPath) {
    myPath = path;
    myVersion = version;
    myCygwinBinPath = cygwinBinPath;
  }

  @NotNull
  public String getPath() {
    return myPath;
  }

  @NotNull
  public GitVersion getVersion() {
    return myVersion;
  }

  public boolean isCygwin() {
    return myCygwinBinPath != null;
  }

  public String getCygwinBinPath() {
    return myCygwinBinPath;
  }
}