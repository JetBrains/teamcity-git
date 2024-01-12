

package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class LimitingRevWalk extends RevWalk {
  private final ServerPluginConfig myConfig;
  private final OperationContext myContext;
  private final GitVcsRoot myGitRoot;
  private final Repository myRepository;
  private int myNextCallCount = 0;
  private RevCommit myCurrentCommit;
  private int myNumberOfCommitsToVisit = -1;

  LimitingRevWalk(@NotNull ServerPluginConfig config, @NotNull OperationContext context) throws VcsException {
    super(context.getRepository());
    myConfig = config;
    myContext = context;
    myGitRoot = context.getGitRoot();
    myRepository = context.getRepository();
  }

  LimitingRevWalk(@NotNull ObjectReader reader, @NotNull ServerPluginConfig config, @NotNull OperationContext context) throws VcsException {
    super(reader);
    myConfig = config;
    myContext = context;
    myGitRoot = context.getGitRoot();
    myRepository = context.getRepository();
  }

  @Override
  public RevCommit next() throws IOException {
    myCurrentCommit = super.next();
    myNextCallCount++;
    if (myCurrentCommit != null && shouldLimitByNumberOfCommits() && myNextCallCount > myNumberOfCommitsToVisit) {
      myCurrentCommit = null;
    }
    return myCurrentCommit;
  }


  public void limitByNumberOfCommits(final int numberOfCommitsToVisit) {
    myNumberOfCommitsToVisit = numberOfCommitsToVisit;
  }

  @NotNull
  public RevCommit getCurrentCommit() {
    checkCurrentCommit();
    return myCurrentCommit;
  }

  private boolean shouldLimitByNumberOfCommits() {
    return myNumberOfCommitsToVisit != -1;
  }

  protected void checkCurrentCommit() {
    if (myCurrentCommit == null)
      throw new IllegalStateException("Current commit is null");
  }

  @NotNull
  public OperationContext getContext() {
    return myContext;
  }

  @NotNull
  public GitVcsRoot getGitRoot() {
    return myGitRoot;
  }

  @NotNull
  public Repository getRepository() {
    return myRepository;
  }

  @NotNull
  public ServerPluginConfig getConfig() {
    return myConfig;
  }

  public int getVisitedCommitsNum() {
    return myNextCallCount;
  }
}