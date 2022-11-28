package jetbrains.buildServer.buildTriggers.vcs.git.command;
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import jetbrains.buildServer.buildTriggers.vcs.git.FetchCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.LsRemoteCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.PushCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.TagCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.CommandUtil;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.GitFacadeImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.command.ssl.SslOperations;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.FuncThrow;
import jetbrains.buildServer.util.NamedThreadFactory;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.ssl.TrustStoreIO;
import jetbrains.buildServer.vcs.CommitResult;
import jetbrains.buildServer.vcs.VcsException;
import org.apache.commons.codec.CharEncoding;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static jetbrains.buildServer.buildTriggers.vcs.git.command.ssl.SslOperations.CERT_DIR;
import static jetbrains.buildServer.buildTriggers.vcs.git.command.ssl.SslOperations.CERT_FILE;

public class NativeGitCommands implements FetchCommand, LsRemoteCommand, PushCommand, TagCommand {

  private static final Logger PERFORMANCE_LOG = Logger.getInstance(NativeGitCommands.class.getName() + ".Performance");
  private static final GitVersion GIT_WITH_PROGRESS_VERSION = new GitVersion(1, 7, 1, 0);

  private static final String ALL_REF_SPEC = "+refs/*:refs/*";
  private static final String EXCLUDE_TAGS_REF_SPEC = "^refs/tags/*";

  private final ServerPluginConfig myConfig;
  private final GitDetector myGitDetector;
  private final VcsRootSshKeyManager mySshKeyManager;

  private final File myTrustedCertificatesDir;

  private static final ReentrantLock lock = new ReentrantLock();

  public NativeGitCommands(@NotNull ServerPluginConfig config,
                           @NotNull GitDetector gitDetector,
                           @NotNull VcsRootSshKeyManager sshKeyManager,
                           @Nullable File trustedCertificatesDir) {
    myConfig = config;
    myGitDetector = gitDetector;
    mySshKeyManager = sshKeyManager;
    myTrustedCertificatesDir = trustedCertificatesDir;
  }

  private boolean shouldGenerateMergedSslCertificate(@NotNull String pemContent, @NotNull File cachedSslDirectory) throws VcsException {
    if (StringUtil.isEmptyOrSpaces(pemContent)) {
      if (cachedSslDirectory.exists()) {
        FileUtil.delete(cachedSslDirectory);
      }
      return false;
    }

    File certFile = new File(cachedSslDirectory, CERT_FILE);
    try {
      return !certFile.exists() || certFile.exists() && !pemContent.equals(FileUtil.readText(certFile));
    } catch (IOException ioe) {
      throw new VcsException(ioe);
    }
  }

  private void generateMergedCertificateFile(@NotNull String pemContent, File cachedSslDirectory) throws VcsException {
    try {
      if (!cachedSslDirectory.exists() && !cachedSslDirectory.mkdir()) {
        throw new VcsException("Can not create directory for certificates: " + cachedSslDirectory.getPath());
      }

      final File file = new File(cachedSslDirectory.getPath(), CERT_FILE);
      FileUtil.writeFile(file, pemContent, CharEncoding.UTF_8);
    } catch (IOException e) {
      throw new VcsException("Can not write file with certificates", e);
    }
  }

  private static boolean isSilentFetch(@NotNull Context ctx) {
    return ctx.getGitVersion().isLessThan(GIT_WITH_PROGRESS_VERSION);
  }

  private <R> R executeWithSslHandling(@NotNull FuncThrow<R, VcsException> executedCmd, @NotNull GitFacade gitFacade)
    throws VcsException {
    int attemptsLeft = 2;
    for (int i = 0; i < attemptsLeft; ++i) {
      try {
        return executedCmd.apply();
      } catch (VcsException e) {
        if (i < attemptsLeft-1 &&
            CommandUtil.isSslError(e) &&
            myTrustedCertificatesDir != null) {

          final File cacheCertDirectory = new File(myConfig.getCachesDir(), CERT_DIR);
          final String pemContent = TrustStoreIO.pemContentFromDirectory(myTrustedCertificatesDir.getAbsolutePath());
          final String mergedCertificatePath = new File(cacheCertDirectory, CERT_FILE).getAbsolutePath();

          if (shouldGenerateMergedSslCertificate(pemContent, cacheCertDirectory)) {
            if (lock.tryLock()) {
              try {
                generateMergedCertificateFile(pemContent, cacheCertDirectory);
              } finally {
                lock.unlock();
              }
            } else {
              lock.lock();
              lock.unlock();
            }
          } else if (mergedCertificatePath.equals(SslOperations.getCertPath(gitFacade))) {
            throw e;
          }

          SslOperations.deleteSslOption(gitFacade);
          if (new File(mergedCertificatePath).exists())
            SslOperations.setSslOption(gitFacade, mergedCertificatePath);


        } else {
          throw e;
        }

      }
    }
    return null; //never executes
  }

  // Visible for testing
  protected <R> R executeCommand(@NotNull Context ctx, @NotNull String action, @NotNull String debugInfo, @NotNull FuncThrow<R, VcsException> cmd, @NotNull GitFacade gitFacade) throws VcsException{
    return executeWithSslHandling(() ->  {
      return NamedThreadFactory.executeWithNewThreadNameFuncThrow(
        String.format("Running native git %s process for : %s", action, debugInfo),
        () -> {
          final long start = System.currentTimeMillis();
          try {
            return cmd.apply();
          } finally {
            final long finish = System.currentTimeMillis();
            final String msg = "[git " + action + "] repository: " + debugInfo + " took " + (finish - start) + "ms";
            if (ctx.isDebugGitCommands()) PERFORMANCE_LOG.info(msg);
            else PERFORMANCE_LOG.debug(msg);
          }
        });
    }, gitFacade);
  }

  private void prune(@NotNull Repository db, @NotNull URIish fetchURI, @NotNull FetchSettings settings) throws VcsException {
    final GitExec gitExec = myGitDetector.detectGit();
    final Context ctx = new ContextImpl(null, myConfig, gitExec, settings.getProgress());
    final GitFacadeImpl gitFacade = new GitFacadeImpl(db.getDirectory(), ctx);
    gitFacade.setSshKeyManager(mySshKeyManager);

    executeCommand(ctx, "prune", LogUtil.describe(db, fetchURI), () -> {
      jetbrains.buildServer.buildTriggers.vcs.git.command.RemoteCommand prune =
        gitFacade.remote()
               .setCommand("prune").setRemote("origin")
               .setAuthSettings(settings.getAuthSettings()).setUseNativeSsh(true)
               .setRetryAttempts(myConfig.getConnectionRetryAttempts())
               .setRepoUrl(fetchURI)
               .trace(myConfig.getGitTraceEnv());
      prune.call();
      return true;
    }, gitFacade);
  }

  private jetbrains.buildServer.buildTriggers.vcs.git.command.FetchCommand createFetchCommand(@NotNull Repository db,
                                                                                              @NotNull URIish fetchURI,
                                                                                              @NotNull FetchSettings settings,
                                                                                              @NotNull GitFacade gitFacade,
                                                                                              @NotNull Context ctx,
                                                                                              @NotNull Collection<String> refSpecs) throws VcsException {
    final jetbrains.buildServer.buildTriggers.vcs.git.command.FetchCommand fetch =
      gitFacade.fetch()
               .setRemote(fetchURI.toString())
               .setFetchTags(false)
               .setAuthSettings(settings.getAuthSettings()).setUseNativeSsh(true)
               .setTimeout(myConfig.getFetchTimeout())
               .setRetryAttempts(myConfig.getConnectionRetryAttempts())
               .setRepoUrl(fetchURI)
               .trace(myConfig.getGitTraceEnv())
               .addPreAction(() -> GitServerUtil.removeRefLocks(db.getDirectory()))
               .setRefSpecsRefresher(
                 gitFacade.lsRemote()
                          .peelRefs()
                          .setAuthSettings(settings.getAuthSettings())
                          .setUseNativeSsh(true)
                          .setTimeout(myConfig.getRepositoryStateTimeoutSeconds())
                          .setRetryAttempts(myConfig.getConnectionRetryAttempts())
                          .setRepoUrl(fetchURI)
                          .trace(myConfig.getGitTraceEnv())
                          .setBranches(settings.getRefSpecs()
                                               .stream()
                                               .map(r -> r.toString())
                                               .map(s -> s.substring(s.lastIndexOf(":")+1))
                                               .filter(s -> !s.contains("*"))
                                               .toArray(String[]::new))
               );

    for (String spec : refSpecs) {
      fetch.setRefspec(spec);
    }

    if (isSilentFetch(ctx))
      fetch.setQuite(true);
    else
      fetch.setShowProgress(true);

    return fetch;
  }

  private Collection<String> defineRefSpecsForFetch(@NotNull FetchSettings settings) {
    final Collection<String> resultRefSpecs = new HashSet<>();
    switch (settings.getFetchMode()) {
      case FETCH_ALL_REFS:
        resultRefSpecs.add(ALL_REF_SPEC);
        break;
      case FETCH_ALL_REFS_EXCEPT_TAGS:
        resultRefSpecs.add(ALL_REF_SPEC);
        resultRefSpecs.add(EXCLUDE_TAGS_REF_SPEC);
        break;
      case FETCH_REF_SPECS:
      default:
        for (RefSpec refSpec : settings.getRefSpecs()) {
          final String refSpecStr = refSpec.toString();
          resultRefSpecs.add(refSpecStr);
        }
        break;
    }
    return resultRefSpecs;
  }

  @Override
  public void fetch(@NotNull Repository db, @NotNull URIish fetchURI, @NotNull FetchSettings settings) throws IOException, VcsException {
    final GitExec gitExec = myGitDetector.detectGit();
    final Context ctx = new ContextImpl(null, myConfig, gitExec, settings.getProgress());
    final GitFacadeImpl gitFacade = new GitFacadeImpl(db.getDirectory(), ctx);
    gitFacade.setSshKeyManager(mySshKeyManager);
    Collection<String> resultRefSpecs = defineRefSpecsForFetch(settings);

    // Before running fetch we need to prune branches which no longer exist in the remote,
    // otherwise git fails to update local branches which were e.g. renamed.
    prune(db, fetchURI, settings);

    executeCommand(ctx, "fetch", getDebugInfo(db, fetchURI, resultRefSpecs), () -> {
      jetbrains.buildServer.buildTriggers.vcs.git.command.FetchCommand fetch = createFetchCommand(db, fetchURI, settings, gitFacade, ctx, resultRefSpecs);
      fetch.call();
      return true;
    }, gitFacade);
  }



  @NotNull
  @Override
  public Map<String, Ref> lsRemote(@NotNull Repository db, @NotNull GitVcsRoot gitRoot, @NotNull FetchSettings settings) throws VcsException {
    final Context ctx = new ContextImpl(gitRoot, myConfig, myGitDetector.detectGit(), settings.getProgress());
    final GitFacadeImpl gitFacade = new GitFacadeImpl(db.getDirectory(), ctx);
    gitFacade.setSshKeyManager(mySshKeyManager);

    return executeCommand(ctx, "ls-remote", LogUtil.describe(gitRoot), () -> {
      final jetbrains.buildServer.buildTriggers.vcs.git.command.LsRemoteCommand lsRemote =
        gitFacade.lsRemote()
                 .peelRefs()
                 .setAuthSettings(gitRoot.getAuthSettings()).setUseNativeSsh(true)
                 .setTimeout(myConfig.getRepositoryStateTimeoutSeconds())
                 .setRetryAttempts(myConfig.getConnectionRetryAttempts())
                 .setRepoUrl(gitRoot.getRepositoryFetchURL().get())
                 .trace(myConfig.getGitTraceEnv());
      return lsRemote.call().stream().collect(Collectors.toMap(Ref::getName, ref -> ref));
    }, gitFacade);
  }

  @NotNull
  @Override
  public CommitResult push(@NotNull Repository db, @NotNull GitVcsRoot gitRoot, @NotNull String ref, @NotNull String commit, @NotNull String lastCommit) throws VcsException {

    final String fullRef = GitUtils.expandRef(ref);

    final Context ctx = new ContextImpl(gitRoot, myConfig, myGitDetector.detectGit());
    final GitFacadeImpl gitFacade = new GitFacadeImpl(db.getDirectory(), ctx);
    gitFacade.setSshKeyManager(mySshKeyManager);

    gitFacade.updateRef().setRef(fullRef).setRevision(commit).setOldValue(lastCommit).call();

    final String debugInfo = LogUtil.describe(gitRoot);
    try {
      return executeCommand(ctx, "push", debugInfo, () -> {
        gitFacade.push()
                 .setRemote(gitRoot.getRepositoryPushURL().toString())
                 .setRefspec(fullRef)
                 .setAuthSettings(gitRoot.getAuthSettings()).setUseNativeSsh(true)
                 .setTimeout(myConfig.getPushTimeoutSeconds())
                 .setRetryAttempts(myConfig.getConnectionRetryAttempts())
                 .setRepoUrl(gitRoot.getRepositoryPushURL().get())
                 .trace(myConfig.getGitTraceEnv())
                 .call();
        return CommitResult.createSuccessResult(commit);
      }, gitFacade);
    } catch (VcsException e) {
      // restore local ref
      try {
        gitFacade.updateRef().setRef(fullRef).setRevision(lastCommit).setOldValue(commit).call();
      } catch (VcsException v) {
        Loggers.VCS.warn("Failed to restore initial revision " + lastCommit + " of " + fullRef + " after unssuccessful push of revision " + commit + " for " + debugInfo, v);
      }
      throw e;
    }
  }

  @Override
  @NotNull
  public String tag(@NotNull OperationContext context, @NotNull String tag, @NotNull String commit) throws VcsException {
    final Context ctx = new ContextImpl(context.getGitRoot(), myConfig, myGitDetector.detectGit());
    final Repository db = context.getRepository();
    final GitFacadeImpl gitFacade = new GitFacadeImpl(db.getDirectory(), ctx);
    gitFacade.setSshKeyManager(mySshKeyManager);

    final GitVcsRoot gitRoot = context.getGitRoot();

    final PersonIdent tagger = PersonIdentFactory.getTagger(gitRoot, db);
    gitFacade.tag().setName(tag).setCommit(commit).force(true).annotate(true)
             .setTagger(tagger.getName(), tagger.getEmailAddress()).setMessage("automatically created by TeamCity VCS labeling build feature").call();

    final String debugInfo = LogUtil.describe(gitRoot);
    List<Ref> currentTags;
    try {
      currentTags = executeCommand(ctx, "ls-remote", debugInfo, () -> {
        return gitFacade.lsRemote()
                        .setTags()
                        .setBranches(tag)
                        .setAuthSettings(gitRoot.getAuthSettings()).setUseNativeSsh(true)
                        .setTimeout(myConfig.getPushTimeoutSeconds())
                        .setRetryAttempts(myConfig.getConnectionRetryAttempts())
                        .setRepoUrl(gitRoot.getRepositoryPushURL().get())
                        .trace(myConfig.getGitTraceEnv())
                        .call();
      }, gitFacade);
    } catch (VcsException v) {
      Loggers.VCS.warn("Failed to get information about remote tag: " + tag + " for " + debugInfo, v);
      throw v;
    }

    if (!currentTags.isEmpty()) {
      try {
        executeCommand(ctx, "push", debugInfo, () -> {
          gitFacade.push()
                   .setRemote(gitRoot.getRepositoryPushURL().toString())
                   .setRefspec(":" + tag)
                   .setAuthSettings(gitRoot.getAuthSettings()).setUseNativeSsh(true)
                   .setTimeout(myConfig.getPushTimeoutSeconds())
                   .setRetryAttempts(myConfig.getConnectionRetryAttempts())
                   .setRepoUrl(gitRoot.getRepositoryPushURL().get())
                   .trace(myConfig.getGitTraceEnv())
                   .call();
          Loggers.VCS.info("Tag '" + tag + "' was successfully removed from " + debugInfo);
          return tag;
        }, gitFacade);
      } catch (VcsException v) {
        Loggers.VCS.warn("Failed to remove remote tag: " + tag + " for " + debugInfo, v);
        throw v;
      }
    }

    try {
      return executeCommand(ctx, "push", debugInfo, () -> {
        gitFacade.push()
                 .setRemote(gitRoot.getRepositoryPushURL().toString())
                 .setRefspec(tag)
                 .setAuthSettings(gitRoot.getAuthSettings()).setUseNativeSsh(true)
                 .setTimeout(myConfig.getPushTimeoutSeconds())
                 .setRetryAttempts(myConfig.getConnectionRetryAttempts())
                 .setRepoUrl(gitRoot.getRepositoryPushURL().get())
                 .trace(myConfig.getGitTraceEnv())
                 .call();
        Loggers.VCS.info("Tag '" + tag + "' was successfully pushed for " + debugInfo);
        return tag;
      }, gitFacade);
    } catch (VcsException e) {
      // remove local tag
      try {
        gitFacade.tag().delete(true).setName(tag).call();
      } catch (VcsException v) {
        Loggers.VCS.warn("Failed to delete local tag " + tag + " of " + commit + " after unssuccessful push for " + debugInfo, v);
      }
      throw e;
    }
  }

  @NotNull
  private String getDebugInfo(@NotNull Repository db, @NotNull URIish uri, @NotNull Collection<String> refSpecs) {
    final StringBuilder sb = new StringBuilder();
    sb.append("(").append(db.getDirectory() != null? db.getDirectory().getAbsolutePath() + ", ":"").append(uri);
    final int size = refSpecs.size();
    int num = 0;
    for (String spec : refSpecs) {
      sb.append(", ").append(spec);
      if (num++ > 10) break;
    }
    final int hidden = size - num;
    if (hidden > 0) {
      sb.append(" and ").append(hidden).append(" more");
    }
    sb.append(")");
    return sb.toString();
  }
}
