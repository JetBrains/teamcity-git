package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitExec;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitNativeOperationsStatus;
import jetbrains.buildServer.buildTriggers.vcs.git.command.NativeGitCommands;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.metrics.*;
import jetbrains.buildServer.serverSide.IOGuard;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.CommitResult;
import jetbrains.buildServer.vcs.Function;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.FetchConnection;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.Transport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.buildTriggers.vcs.git.GitServerUtil.friendlyNotSupportedException;
import static jetbrains.buildServer.buildTriggers.vcs.git.GitServerUtil.friendlyTransportException;

public class GitRepoOperationsImpl implements GitRepoOperations {
  private static final Logger PERFORMANCE_LOG = Logger.getInstance(GitRepoOperationsImpl.class.getName() + ".Performance");
  private static final String GIT_NATIVE_OPERATIONS_ENABLED = "teamcity.git.nativeOperationsEnabled";
  private static final Counter EMPTY_COUNTER = new NoOpCounter();

  private final TransportFactory myTransportFactory;
  private final VcsRootSshKeyManager mySshKeyManager;
  private final ServerPluginConfig myConfig;
  private final FetchCommand myJGitFetchCommand;
  private final LazyGitExec myGitExec = new LazyGitExec();

  private final Function<String, Counter> myFetchDurationTimerProvider;
  private final GitNativeOperationsStatus myMainConfigSettings;

  public GitRepoOperationsImpl(@NotNull ServerPluginConfig config,
                               @NotNull TransportFactory transportFactory,
                               @NotNull VcsRootSshKeyManager sshKeyManager,
                               @NotNull FetchCommand jGitFetchCommand) {
    this(config, NO_IMPL, transportFactory, sshKeyManager, jGitFetchCommand, (Function<String, Counter>)repoUrl -> EMPTY_COUNTER);
  }

  public GitRepoOperationsImpl(@NotNull ServerPluginConfig config,
                               @NotNull GitNativeOperationsStatus nativeOperationsStatus,
                               @NotNull TransportFactory transportFactory,
                               @NotNull VcsRootSshKeyManager sshKeyManager,
                               @NotNull FetchCommand jGitFetchCommand,
                               @NotNull ServerMetrics serverMetrics) {
    this(config, nativeOperationsStatus, transportFactory, sshKeyManager, jGitFetchCommand, new FetchDurationTimers(serverMetrics, config));
  }

  private GitRepoOperationsImpl(@NotNull ServerPluginConfig config,
                                @NotNull GitNativeOperationsStatus nativeOperationsStatus,
                                @NotNull TransportFactory transportFactory,
                                @NotNull VcsRootSshKeyManager sshKeyManager,
                                @NotNull FetchCommand jGitFetchCommand,
                                @NotNull Function<String, Counter> fetchDurationTimerProvider) {
    myConfig = config;
    myMainConfigSettings = nativeOperationsStatus;
    myTransportFactory = transportFactory;
    mySshKeyManager = sshKeyManager;
    myJGitFetchCommand = jGitFetchCommand;
    myFetchDurationTimerProvider = fetchDurationTimerProvider;
  }

  @NotNull
  @Override
  public FetchCommand fetchCommand(@NotNull String repoUrl) {
    return new MetricReportingFetchCommand((FetchCommand)getNativeGitCommandOptional(repoUrl).orElse(myJGitFetchCommand), myFetchDurationTimerProvider.apply(repoUrl));
  }

  @NotNull
  private Optional<GitCommand> getNativeGitCommandOptional(@NotNull String repoUrl) {
    if (isNativeGitOperationsEnabledInternal(repoUrl)) {
      final GitExec gitExec = gitExecInternal();
      if (isNativeGitOperationsSupported(gitExec)) {
        //noinspection ConstantConditions
        return Optional.of(new NativeGitCommands(myConfig, () -> gitExec, mySshKeyManager));
      }
    }
    return Optional.empty();
  }

  @Override
  public boolean isNativeGitOperationsEnabled(@NotNull String repoUrl) {
    final Optional<GitCommand> optional = getNativeGitCommandOptional(repoUrl);
    return optional.isPresent() && optional.get() instanceof NativeGitCommands;
  }

  private boolean isNativeGitOperationsEnabledInternal(@NotNull String repoUrl) {
    for (Map.Entry<String, String> e : TeamCityProperties.getPropertiesWithPrefix(GIT_NATIVE_OPERATIONS_ENABLED).entrySet()) {
      if (e.getKey().length() == GIT_NATIVE_OPERATIONS_ENABLED.length()) continue;
      final String url = e.getKey().substring(GIT_NATIVE_OPERATIONS_ENABLED.length() + 1);
      if (repoUrl.contains(url)) {
        return Boolean.parseBoolean(e.getValue());
      }
    }
    final String fromProperties = TeamCityProperties.getProperty(GIT_NATIVE_OPERATIONS_ENABLED);
    if (StringUtil.isNotEmpty(fromProperties)) return Boolean.parseBoolean(fromProperties);

    return myMainConfigSettings.isNativeGitOperationsEnabled();
 }

  @Override
  public boolean isNativeGitOperationsEnabled() {
    for (String v : TeamCityProperties.getPropertiesWithPrefix(GIT_NATIVE_OPERATIONS_ENABLED).values()) {
      if (Boolean.parseBoolean(v)) return true;
    }
    return myMainConfigSettings.isNativeGitOperationsEnabled();
  }

  @Override
  public boolean setNativeGitOperationsEnabled(boolean nativeGitOperatoinsEnabled) {
    return myMainConfigSettings.setNativeGitOperationsEnabled(nativeGitOperatoinsEnabled);
  }

  @Override
  public boolean isNativeGitOperationsSupported(@Nullable GitExec gitExec) {
    return gitExec != null && GitVersion.fetchSupportsStdin(gitExec.getVersion());
  }

  @NotNull
  @Override
  public LsRemoteCommand lsRemoteCommand(@NotNull String repoUrl) {
    return (LsRemoteCommand)getNativeGitCommandOptional(repoUrl).orElse(
      (LsRemoteCommand)(db, gitRoot, settings) -> getRemoteRefsJGit(db, gitRoot));
  }

  @NotNull
  @Override
  public LsRemoteCommand lsRemoteCommand(boolean nativeOperations) {
    if (nativeOperations) {
      final GitExec gitExec = gitExecInternal();
      if (isNativeGitOperationsSupported(gitExec)) {
        return new NativeGitCommands(myConfig, () -> gitExec, mySshKeyManager);
      } else {
        throw new UnsupportedOperationException("git executable " + gitExec.getPath() + " version " + gitExec.getVersion() + " is not supported for running native Git commands on server-side");
      }
    }
    return (db, gitRoot, settings) -> getRemoteRefsJGit(db, gitRoot);
  }

  @NotNull
  private Map<String, Ref> getRemoteRefsJGit(@NotNull Repository db, @NotNull GitVcsRoot gitRoot) throws VcsException {
    try {
      return IOGuard.allowNetworkCall(() -> {
        try {
          return Retry.retry(new Retry.Retryable<Map<String, Ref>>() {
            @Override
            public boolean requiresRetry(@NotNull final Exception e, int attempt, int maxAttempts) {
              return GitServerUtil.isRecoverable(e, gitRoot.getAuthSettings(), attempt, maxAttempts);
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
  private GitExec detectGitInternal() throws VcsException {
    final String gitPath = myConfig.getPathToGit();
    if (StringUtil.isEmpty(gitPath)) {
      throw new VcsException("No path to Git provided: please specify path to Git executable using \"teamcity.server.git.executable.path\" server startup property or or add it to the PATH environment variable");
    }
    GitVersion gitVersion;
    try {
      gitVersion = IOGuard.allowCommandLine(() -> new GitFacadeImpl(new File("."), new StubContext(gitPath)).version().call());
    } catch (VcsException e) {
      throw new VcsException("Unable to run Git at path \"" + gitPath + "\": Install Git (version " + GitVersion.DEPRECATED + " or higher) on your system, specify correct path to Git executable using \"teamcity.server.git.executable.path\" server startup property or add it to the PATH environment variable");
    }
    if (gitVersion.isSupported()) {
      return new GitExec(gitPath, gitVersion, null);
    }
    throw new VcsException("TeamCity supports Git version " + GitVersion.DEPRECATED + " or higher, detected Git (path \""+ gitPath +"\") has version " + gitVersion + ".\n" + "Please install the latest Git version");
  }


  @Nullable
  private GitExec gitExecInternal() {
    return myGitExec.getOrDetect();
  }

  @NotNull
  @Override
  public GitExec detectGit() throws VcsException {
    myGitExec.reset();
    return detectGitInternal();
  }

  @NotNull
  @Override
  public PushCommand pushCommand(@NotNull String repoUrl) {
    return (PushCommand)getNativeGitCommandOptional(repoUrl).orElse((PushCommand)this::pushJGit);
  }

  @NotNull
  private CommitResult pushJGit(@NotNull Repository db, @NotNull GitVcsRoot gitRoot,
                                @NotNull String ref,
                                @NotNull String commit, @NotNull String lastCommit) throws VcsException {

    ref = GitUtils.expandRef(ref);
    try (Transport tn = myTransportFactory.createTransport(db, gitRoot.getRepositoryPushURL().get(), gitRoot.getAuthSettings(), myConfig.getPushTimeoutSeconds())) {
      final ObjectId commitId = ObjectId.fromString(commit);
      final ObjectId lastCommitId = ObjectId.fromString(lastCommit);

      final RemoteRefUpdate ru = new RemoteRefUpdate(db, null, commitId, ref, false, null, lastCommitId);
      tn.push(NullProgressMonitor.INSTANCE, Collections.singletonList(ru));
      switch (ru.getStatus()) {
        case UP_TO_DATE:
        case OK:
          return CommitResult.createSuccessResult(commitId.name());
        default: {
          StringBuilder error = new StringBuilder();
          error.append("Push failed, status: ").append(ru.getStatus());
          if (ru.getMessage() != null)
            error.append(", message: ").append(ru.getMessage());
          throw new VcsException(error.toString());
        }
      }
    } catch (VcsException e) {
      throw e;
    } catch (Exception e) {
      throw new VcsException("Error while pushing a commit, root " + gitRoot + ", revision " + commit + ", destination " + ref + ": " + e.getMessage(), e);
    }
  }

  @NotNull
  @Override
  public TagCommand tagCommand(@NotNull GitVcsSupport vcsSupport, @NotNull String repoUrl) {
    return (TagCommand)getNativeGitCommandOptional(repoUrl).orElse(new GitLabelingSupport(vcsSupport, myTransportFactory, myConfig));
  }

  private class LazyGitExec {
    private final ReentrantLock myLock = new ReentrantLock();
    private volatile Optional<GitExec> myRef = null;

    @Nullable
    GitExec getOrDetect() {
      Optional<GitExec> val = myRef;
      if (val != null) {
        return val.orElse(null);
      }

      myLock.lock();
      try {
        val = myRef;
        if (val != null) {
          return val.orElse(null);
        }

        try {
          val = Optional.of(detectGitInternal());
        } catch (Exception e) {
          val = Optional.empty();
          Loggers.VCS.warn("Failed to detect supported git executable, native git operations will be disabled", e);
        }

        myRef = val;
        return val.orElse(null);
      } finally {
        myLock.unlock();
      }
    }

    void reset() {
      myRef = null;
    }
  }

  private static class FetchDurationTimers implements Function<String, Counter> {
    private final ServerPluginConfig myConfig;
    private final ServerMetrics myServerMetrics;
    private final ConcurrentHashMap<String, Counter> myFetchDurationTimers = new ConcurrentHashMap<>();

    private FetchDurationTimers(@NotNull ServerMetrics serverMetrics, @NotNull ServerPluginConfig config) {
      myServerMetrics = serverMetrics;
      myConfig = config;
    }

    @Override
    public Counter apply(final String repoUrl) {
      // we can report metric separately for specified repos
      final String key = myConfig.getFetchDurationMetricRepos().contains(repoUrl) ? repoUrl : "ALL";
      return myFetchDurationTimers.computeIfAbsent(key, url -> myServerMetrics.metricBuilder("vcs.git.fetch.duration")
        .tags("repoUrl", key)
        .dataType(MetricDataType.MILLISECONDS)
        .experimental(true)
        .description("git fetch operations duration")
        .buildCounter());
    }
  }
}
