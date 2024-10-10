package jetbrains.buildServer.buildTriggers.vcs.git.gitProxy.data;

import java.util.List;

public class CommitList {
  public List<Commit> commits;
  public int totalMatched;
  public int totalCommits;
  public int totalBranches;
}
