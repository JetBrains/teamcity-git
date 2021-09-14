package jetbrains.buildServer.buildTriggers.vcs.git.command;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import jetbrains.buildServer.buildTriggers.vcs.git.FetchCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.LsRemoteCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.PushCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.GitFacadeImpl;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import jetbrains.buildServer.util.NamedThreadFactory;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.CommitResult;
import jetbrains.buildServer.vcs.CommitSettings;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;

public class NativeGitCommands implements FetchCommand, LsRemoteCommand, PushCommand {

  private static final GitVersion GIT_WITH_PROGRESS_VERSION = new GitVersion(1, 7, 1, 0);

  private final ServerPluginConfig myConfig;
  private final GitDetector myGitDetector;
  private final VcsRootSshKeyManager mySshKeyManager;

  public NativeGitCommands(@NotNull ServerPluginConfig config,
                           @NotNull GitDetector gitDetector,
                           @NotNull VcsRootSshKeyManager sshKeyManager) {
    myConfig = config;
    myGitDetector = gitDetector;
    mySshKeyManager = sshKeyManager;
  }

  private static boolean isSilentFetch(@NotNull Context ctx) {
    return ctx.getGitVersion().isLessThan(GIT_WITH_PROGRESS_VERSION);
  }

  @Override
  public void fetch(@NotNull Repository db, @NotNull URIish fetchURI, @NotNull Collection<RefSpec> refspecs, @NotNull FetchSettings settings) throws IOException, VcsException {
    final Context ctx = new ContextImpl(myConfig, myGitDetector.detectGit(), settings.getProgress());
    final GitFacadeImpl gitFacade = new GitFacadeImpl(db.getDirectory(), ctx);
    gitFacade.setSshKeyManager(mySshKeyManager);

    // Before running fetch we need to prune branches which no longer exist in the remote,
    // otherwise git fails to update local branches which were e.g. renamed.
    try {
      gitFacade.remote()
               .setCommand("prune").setRemote("origin")
               .setAuthSettings(settings.getAuthSettings()).setUseNativeSsh(true)
               .trace(myConfig.getGitTraceEnv())
               .call();
    } catch (VcsException e) {
      Loggers.VCS.warnAndDebugDetails("Error while pruning removed branches in " + db, e);
    }

    final jetbrains.buildServer.buildTriggers.vcs.git.command.FetchCommand fetch =
      gitFacade.fetch()
               .setRemote(fetchURI.toString())
               .setFetchTags(false)
               .setAuthSettings(settings.getAuthSettings()).setUseNativeSsh(true)
               .setTimeout(myConfig.getFetchTimeout())
               .setRetryAttempts(myConfig.getConnectionRetryAttempts())
               .trace(myConfig.getGitTraceEnv())
               .addPreAction(() -> GitServerUtil.removeRefLocks(db.getDirectory()));

    for (RefSpec refSpec : refspecs) {
      fetch.setRefspec(refSpec.toString());
    }

    if (isSilentFetch(ctx))
      fetch.setQuite(true);
    else
      fetch.setShowProgress(true);

    NamedThreadFactory.executeWithNewThreadNameFuncThrow("Running native git fetch process for : " + getDebugInfo(db, fetchURI, refspecs), () -> {
      fetch.call();
      return true;
    });
  }

  @NotNull
  @Override
  public Map<String, Ref> lsRemote(@NotNull Repository db, @NotNull GitVcsRoot gitRoot) throws VcsException {
    final Context ctx = new ContextImpl(myConfig, myGitDetector.detectGit());
    final GitFacadeImpl gitFacade = new GitFacadeImpl(db.getDirectory(), ctx);
    gitFacade.setSshKeyManager(mySshKeyManager);

    final jetbrains.buildServer.buildTriggers.vcs.git.command.LsRemoteCommand lsRemote =
      gitFacade.lsRemote()
               .peelRefs()
               .setAuthSettings(gitRoot.getAuthSettings()).setUseNativeSsh(true)
               .setTimeout(myConfig.getRepositoryStateTimeoutSeconds())
               .setRetryAttempts(myConfig.getConnectionRetryAttempts())
               .trace(myConfig.getGitTraceEnv());

    return NamedThreadFactory.executeWithNewThreadNameFuncThrow("Running native git ls-remote process for : " + getDebugInfo(db, gitRoot.getRepositoryFetchURL().get()), () -> {
      return lsRemote.call().stream().collect(Collectors.toMap(Ref::getName, ref -> ref));
    });
  }

  @NotNull
  @Override
  public CommitResult push(@NotNull Repository db, @NotNull GitVcsRoot gitRoot, @NotNull String commit, @NotNull String lastCommit, @NotNull CommitSettings settings) throws VcsException {
    final Context ctx = new ContextImpl(myConfig, myGitDetector.detectGit());
    final GitFacadeImpl gitFacade = new GitFacadeImpl(db.getDirectory(), ctx);
    gitFacade.setSshKeyManager(mySshKeyManager);

    final String ref = GitUtils.expandRef(gitRoot.getRef());
    gitFacade.updateRef().setRef(ref).setRevision(commit).setOldValue(lastCommit).call();

    return NamedThreadFactory.executeWithNewThreadNameFuncThrow("Running native git push process for : " + getDebugInfo(db, gitRoot.getRepositoryFetchURL().get()), () -> {
      gitFacade.push()
               .setRefspec(ref)
               .setAuthSettings(gitRoot.getAuthSettings()).setUseNativeSsh(true)
               .setTimeout(myConfig.getPushTimeoutSeconds())
               .trace(myConfig.getGitTraceEnv())
               .call();
      return CommitResult.createSuccessResult(commit);
    });
  }

  @NotNull
  private String getDebugInfo(@NotNull Repository db, @NotNull URIish uri, @NotNull Collection<RefSpec> refSpecs) {
    return getDebugInfo(db, uri, refSpecs.toArray(new RefSpec[0]));
  }

  @NotNull
  private String getDebugInfo(@NotNull Repository db, @NotNull URIish uri, RefSpec... refSpecs) {
    final StringBuilder sb = new StringBuilder();
    sb.append("(").append(db.getDirectory() != null? db.getDirectory().getAbsolutePath() + ", ":"").append(uri).append("#");
    for (RefSpec spec : refSpecs) {
      sb.append(spec).append(" ");
    }
    sb.append(")");

    final int commandLineStrLimit = TeamCityProperties.getInteger("teamcity.externalProcessRunner.limitCommandLineLengthInThreadName", 1000);
    return StringUtil.truncateStringValueWithDotsAtCenter(sb.toString(), commandLineStrLimit);
  }
}
