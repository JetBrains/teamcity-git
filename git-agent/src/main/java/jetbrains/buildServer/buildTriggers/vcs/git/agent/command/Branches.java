

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command;

import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * @author dmitry.neverov
 */
public class Branches {

  private final Set<String> myBranches = new HashSet<String>();
  private String myCurrentBranch;

  public boolean isCurrentBranch(@NotNull String branch) {
    return branch.equals(myCurrentBranch);
  }

  public boolean contains(@NotNull String branch) {
    return myBranches.contains(branch);
  }

  public void addBranch(@NotNull String branch, boolean current) {
    myBranches.add(branch);
    if (current)
      myCurrentBranch = branch;
  }

}