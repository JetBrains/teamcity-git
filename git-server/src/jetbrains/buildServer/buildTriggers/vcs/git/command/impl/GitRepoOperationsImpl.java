package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.util.Map;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitExec;
import jetbrains.buildServer.buildTriggers.vcs.git.command.NativeGitCommands;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.IOGuard;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.FetchConnection;
import org.eclipse.jgit.transport.Transport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.buildTriggers.vcs.git.GitServerUtil.friendlyNotSupportedException;
import static jetbrains.buildServer.buildTriggers.vcs.git.GitServerUtil.friendlyTransportException;

public class GitRepoOperationsImpl implements GitRepoOperations {
  private static final Logger PERFORMANCE_LOG = Logger.getInstance(GitVcsSupport.class.getName() + ".Performance");

  private final TransportFactory myTransportFactory;
  private final VcsRootSshKeyManager mySshKeyManager;
  private final ServerPluginConfig myConfig;
  private final FetchCommand myJGitFetchCommand;
  private GitExec myGitExec;

  public GitRepoOperationsImpl(@NotNull ServerPluginConfig config,
                               @NotNull TransportFactory transportFactory,
                               @NotNull VcsRootSshKeyManager sshKeyManager,
                               @NotNull FetchCommand jGitFetchCommand) {
    myConfig = config;
    myTransportFactory = transportFactory;
    mySshKeyManager = sshKeyManager;
    myJGitFetchCommand = jGitFetchCommand;
  }

  @NotNull
  @Override
  public FetchCommand fetchCommand() {
    if (isNativeGitOperationsEnabledAndSupported()) {
      return new NativeGitCommands(myConfig, this::detectGit, mySshKeyManager);
    }
    return myJGitFetchCommand;
  }

  public boolean isNativeGitOperationsEnabled() {
    return TeamCityProperties.getBoolean("teamcity.git.nativeOperationsEnabled");
  }

  @Override
  public boolean isNativeGitOperationsSupported() {
    final GitExec gitExec = gitExec();
    return gitExec != null && GitVersion.fetchSupportsStdin(gitExec.getVersion());
  }

  private boolean isNativeGitOperationsEnabledAndSupported() {
    return isNativeGitOperationsEnabled() && isNativeGitOperationsSupported();
  }

  @NotNull
  @Override
  public LsRemoteCommand lsRemoteCommand() {
    if (isNativeGitOperationsEnabledAndSupported()) {
      return new NativeGitCommands(myConfig, this::detectGit, mySshKeyManager);
    }

    return new LsRemoteCommand() {
      @NotNull
      @Override
      public Map<String, Ref> lsRemote(@NotNull Repository db, @NotNull GitVcsRoot gitRoot) throws VcsException {
        return getRemoteRefsJGit(db, gitRoot);
      }
    };
  }

  @NotNull
  private Map<String, Ref> getRemoteRefsJGit(@NotNull Repository db, @NotNull GitVcsRoot gitRoot) throws VcsException {
    try {
      return IOGuard.allowNetworkCall(() -> {
        try {
          return Retry.retry(new Retry.Retryable<Map<String, Ref>>() {
            @Override
            public boolean requiresRetry(@NotNull final Exception e) {
              return GitServerUtil.isRecoverable(e);
            }

            @Nullable
            @Override
            public Map<String, Ref> call() throws Exception {
              final long start = System.currentTimeMillis();
              try (
                final Transport transport = myTransportFactory.createTransport(db, gitRoot.getRepositoryFetchURL().get(), gitRoot.getAuthSettings(), myConfig.getRepositoryStateTimeoutSeconds());
                final FetchConnection connection = transport.openFetch()) {
                return connection.getRefsMap();
              } catch (NotSupportedException nse) {
                throw friendlyNotSupportedException(gitRoot, nse);
              } catch (WrongPassphraseException e) {
                throw new VcsException(e.getMessage(), e);
              } finally {
                final long finish = System.currentTimeMillis();
                PERFORMANCE_LOG.debug("[getRemoteRefs] repository: " + LogUtil.describe(gitRoot) + ", took " + (finish - start) + "ms");
              }
            }

            @NotNull
            @Override
            public Logger getLogger() {
              return Loggers.VCS;
            }
          }, myConfig.getConnectionRetryIntervalMillis(), myConfig.getConnectionRetryAttempts());
        } catch (TransportException t) {
          throw friendlyTransportException(t, gitRoot);
        }
      });
    } catch (Exception e) {
      if (e instanceof VcsException) {
        throw (VcsException) e;
      }
      else throw new VcsException(e);
    }
  }

  @NotNull
  private GitExec detectGit() throws VcsException {
    final String gitPath = myConfig.getPathToGit();
    if (gitPath == null) {
      throw new IllegalArgumentException("No path to git provided: please specify path to git executable using \"teamcity.server.git.executable.path\" server startup property");
    }
    if (myGitExec == null || !gitPath.equals(myGitExec.getPath())) {
      GitVersion gitVersion;
      try {
        gitVersion = new GitFacadeImpl(new File("."), new StubContext(gitPath)).version().call();
      } catch (VcsException e) {
        throw new VcsException("Unable to run git at path " + gitPath + ": please specify correct path to git executable using \"teamcity.server.git.executable.path\" server startup property", e);
      }
      if (gitVersion.isSupported()) {
        myGitExec = new GitExec(gitPath, gitVersion, null);
      } else {
        throw new VcsException("TeamCity supports git version " + GitVersion.DEPRECATED + " or higher, found git ("+ gitPath +") has version " + gitVersion + ": please upgrade git");
      }
    }
    return myGitExec;
  }

  @Nullable
  @Override
  public GitExec gitExec() {
    try {
      return detectGit();
    } catch (VcsException ignored) {
    }
    return null;
  }
}
