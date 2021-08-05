package jetbrains.buildServer.buildTriggers.vcs.git.command;

import java.io.IOException;
import java.util.Collection;
import jetbrains.buildServer.buildTriggers.vcs.git.FetchCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.GitFacadeImpl;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import jetbrains.buildServer.util.NamedThreadFactory;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;

public class NativeGitFetchCommand implements FetchCommand {

  private static final GitVersion GIT_WITH_PROGRESS_VERSION = new GitVersion(1, 7, 1, 0);

  private final ServerPluginConfig myConfig;
  private final VcsRootSshKeyManager mySshKeyManager;

  public NativeGitFetchCommand(@NotNull ServerPluginConfig config, @NotNull VcsRootSshKeyManager sshKeyManager) {
    myConfig = config;
    mySshKeyManager = sshKeyManager;
  }

  private static boolean isSilentFetch(@NotNull Context ctx) {
    return ctx.getGitVersion().isLessThan(GIT_WITH_PROGRESS_VERSION);
  }

  @Override
  public void fetch(@NotNull Repository db, @NotNull URIish fetchURI, @NotNull Collection<RefSpec> refspecs, @NotNull FetchSettings settings) throws IOException, VcsException {
    final ContextImpl ctx = new ContextImpl(myConfig, settings);
    final GitFacadeImpl gitFacade = new GitFacadeImpl(db.getDirectory(), ctx);
    gitFacade.setSshKeyManager(mySshKeyManager);

    // Before running fetch we need to prune branches which no longer exist in the remote,
    // otherwise git fails to update local branches which were e.g. renamed.
    gitFacade.remote()
             .setCommand("prune").setRemote("origin")
             .setAuthSettings(settings.getAuthSettings()).setUseNativeSsh(true)
             .call();

    final jetbrains.buildServer.buildTriggers.vcs.git.command.FetchCommand fetch = gitFacade.fetch();
    fetch
      .setRemote(fetchURI.toString())
      .setAuthSettings(settings.getAuthSettings()).setUseNativeSsh(true)
      .setTimeout(myConfig.getFetchTimeout())
      .setRetryAttempts(myConfig.getConnectionRetryAttempts())
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

  private String getDebugInfo(@NotNull Repository db, @NotNull URIish uri, @NotNull Collection<RefSpec> refSpecs) {
    final StringBuilder sb = new StringBuilder();
    for (RefSpec spec : refSpecs) {
      sb.append(spec).append(" ");
    }
    return "(" + (db.getDirectory() != null? db.getDirectory().getAbsolutePath() + ", ":"") + uri + "#" + sb + ")";
  }
}
