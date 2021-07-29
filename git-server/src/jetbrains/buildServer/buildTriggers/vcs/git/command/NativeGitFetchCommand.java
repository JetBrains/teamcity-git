package jetbrains.buildServer.buildTriggers.vcs.git.command;

import java.io.IOException;
import java.util.Collection;
import jetbrains.buildServer.buildTriggers.vcs.git.FetchCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.GitFacadeImpl;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
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
    final jetbrains.buildServer.buildTriggers.vcs.git.command.FetchCommand fetch = gitFacade.fetch();
    fetch
      .setRemote(fetchURI.toString())
      .setAuthSettings(settings.getAuthSettings())
      .setRetryAttempts(myConfig.getConnectionRetryAttempts())
      .setTimeout(myConfig.getFetchTimeout())
      .setUseNativeSsh(true)
      .addPreAction(() -> GitServerUtil.removeRefLocks(db.getDirectory()));

    for (RefSpec refSpec : refspecs) {
      fetch.setRefspec(refSpec.toString());
    }

    if (isSilentFetch(ctx))
      fetch.setQuite(true);
    else
      fetch.setShowProgress(true);

    fetch.call();
  }

}
