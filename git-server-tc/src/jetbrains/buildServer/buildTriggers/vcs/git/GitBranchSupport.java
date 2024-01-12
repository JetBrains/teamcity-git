

package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.vcs.BranchSupport;
import org.jetbrains.annotations.NotNull;


public class GitBranchSupport implements BranchSupport, GitServerExtension {

  @NotNull
  public String getRemoteRunOnBranchPattern() {
    return "refs/heads/remote-run/*";
  }
}