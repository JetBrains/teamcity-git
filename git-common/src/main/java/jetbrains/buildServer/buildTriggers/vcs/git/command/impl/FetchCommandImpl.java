

package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVersion;
import jetbrains.buildServer.buildTriggers.vcs.git.command.CommitGraphCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.FetchCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitFacade;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FetchCommandImpl extends BaseAuthCommandImpl<FetchCommand> implements FetchCommand {

  private final Set<String> myRefSpecs = new HashSet<>();
  private boolean myQuite;
  private boolean myShowProgress;
  private Integer myDepth;
  private boolean myFetchTags = true;
  private String myRemoteUrl;
  private boolean myNoShowForcedUpdates = false;

  private Callable<List<Ref>> myListBranchesRefresher;
  private Callable<Integer> myCommitGraphRefresher;

  public FetchCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }

  @NotNull
  public FetchCommand setRefspec(@NotNull String refspec) {
    myRefSpecs.add(refspec);
    return this;
  }

  @NotNull
  public FetchCommand setQuite(boolean quite) {
    myQuite = quite;
    return this;
  }

  @NotNull
  public FetchCommand setShowProgress(boolean showProgress) {
    myShowProgress = showProgress;
    return this;
  }

  @NotNull
  public FetchCommand setDepth(int depth) {
    myDepth = depth;
    return this;
  }

  @NotNull
  public FetchCommand setFetchTags(boolean fetchTags) {
    myFetchTags = fetchTags;
    return this;
  }

  @NotNull
  @Override
  public FetchCommand setRemote(@NotNull String remoteUrl) {
    myRemoteUrl = remoteUrl;
    return this;
  }

  @NotNull
  @Override
  public FetchCommand setRefSpecsRefresher(Callable<List<Ref>> lsBranchRefresher) {
    myListBranchesRefresher = lsBranchRefresher;
    return this;
  }

  @NotNull
  @Override
  public FetchCommand setRefreshCommitGraphIfCorrupted(GitFacade facade) {
    myCommitGraphRefresher = () -> facade.commitGraph()
                                  .setWriteCommand()
                                  .setReachable()
                                  .setStrategy("replace")
                                  .call();
    return this;
  }

  @NotNull
  @Override
  public FetchCommand setNoShowForcedUpdates(boolean noShowForcedUpdates) {
    myNoShowForcedUpdates = noShowForcedUpdates;
    return this;
  }

  public void call() throws VcsException {
    final GitCommandLine cmd = getCmd();
    final GitVersion gitVersion = cmd.getGitVersion();

    cmd.addParameter("fetch");
    if (myQuite)
      cmd.addParameter("-q");
    if (myShowProgress)
      cmd.addParameter("--progress");
    if (myDepth != null)
      cmd.addParameter("--depth=" + myDepth);
    if (!myFetchTags)
      cmd.addParameter("--no-tags");
    if(myNoShowForcedUpdates && GitVersion.isNoShowForcedUpdatesSupported(gitVersion)){
      cmd.addParameter("--no-show-forced-updates");
    }
    if (gitVersion.isGreaterThan(new GitVersion(1, 7, 3))) {
      cmd.addParameter("--recurse-submodules=no"); // we process submodules separately
    }

    cmd.setHasProgress(true);

    byte[] refSpecsBytes = new byte[0];
    if (myRefSpecs.size() > 1 && GitVersion.fetchSupportsStdin(gitVersion)) {
      cmd.addParameter("--stdin");
      cmd.addParameter(getRemote());
      refSpecsBytes = refSpecsToBytes(cmd);
    } else {
      cmd.addParameter(getRemote());
      myRefSpecs.forEach(refSpec -> cmd.addParameter(refSpec));
    }

    runCmd(new FetchCommandRetryable(cmd.stdErrLogLevel("info")));
  }

  @NotNull
  private byte[] refSpecsToBytes(@NotNull GitCommandLine cmd) {
    final StringBuilder res = new StringBuilder();
    for (String refSpec : myRefSpecs) {
      res.append(refSpec).append("\n");
    }
    return res.toString().getBytes(cmd.getCharset());
  }

  @NotNull
  private String getRemote() {
    return myRemoteUrl == null ? "origin" : myRemoteUrl;
  }

  class FetchCommandRetryable extends GitCommandRetryable {
    FetchCommandRetryable(@NotNull GitCommandLine cmd) {
      super(cmd, refSpecsToBytes(cmd));
    }

    @Override
    public boolean requiresRetry(@NotNull Exception e, int attempt, int maxAttempts) {
      return requiresRetryWithInputRefresh(e, super.requiresRetry(e, attempt, maxAttempts));
    }

    boolean requiresRetryWithInputRefresh(Exception e, boolean shouldRetry) {
      if (!shouldRetry) {
        return false;
      }

      if (!(e instanceof VcsException)) {
        return true;
      }

      if (CommandUtil.isRefsError((VcsException)e)) {
        try {
          refreshRefsAndStdin();
          if (myRefSpecs.isEmpty()) {
            return false;
          }
        } catch (VcsException ve) {
          return false;
        }
      }

      if (CommandUtil.isCommitGraphError((VcsException)e)) {
        if (myCommitGraphRefresher != null) {
          try {
            int result = myCommitGraphRefresher.call();
            return result == 0;
          } catch (Exception ve) {
            return false;
          }
        }
      }

      return true;
    }

    void refreshRefsAndStdin() throws VcsException {
      if (myListBranchesRefresher == null) {
        return;
      }

      List<Ref> remoteRefs;
      try {
        remoteRefs = myListBranchesRefresher.call();
      } catch (Exception e) {
        throw new VcsException("Failed to refresh remote branches", e);
      }

      if (myRefSpecs.stream().anyMatch(ref -> ref.contains("*"))) {
        return;
      }

      myRefSpecs.retainAll(remoteRefs.stream()
                                     .filter(ref -> ref.getName().startsWith("ref"))
                                     .map(ref -> "+" + ref.getName() + ":" + ref.getName())
                                     .collect(Collectors.toSet()));
      myInput = refSpecsToBytes(myCmd);
    }
  }
}