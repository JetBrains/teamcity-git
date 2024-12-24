

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import jetbrains.buildServer.BuildProblemData;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.BuildDirectoryCleanerCallback;
import jetbrains.buildServer.agent.BuildProgressLogger;
import jetbrains.buildServer.agent.SmartDirectoryCleaner;
import jetbrains.buildServer.agent.oauth.AgentTokenStorage;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.*;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.ssl.SSLInvestigator;
import jetbrains.buildServer.buildTriggers.vcs.git.command.errors.GitIndexCorruptedException;
import jetbrains.buildServer.buildTriggers.vcs.git.command.errors.GitOutdatedIndexException;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.CommandUtil;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.RefImpl;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.messages.DefaultMessagesInfo;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.*;
import org.apache.log4j.Logger;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static jetbrains.buildServer.buildTriggers.vcs.git.GitUtils.getGitDir;
import static jetbrains.buildServer.buildTriggers.vcs.git.agent.GitUtilsAgent.detectExtraHTTPCredentialsInBuild;

public class UpdaterImpl implements Updater {

  private final static Logger LOG = Logger.getLogger(UpdaterImpl.class);
  //--force option in git submodule update introduced in 1.7.6
  private final static GitVersion GIT_WITH_FORCE_SUBMODULE_UPDATE = new GitVersion(1, 7, 6);
  public final static GitVersion GIT_WITH_SPARSE_CHECKOUT = new GitVersion(1, 7, 4);
  public final static GitVersion BROKEN_SPARSE_CHECKOUT = new GitVersion(2, 7, 0);
  public final static GitVersion MIN_GIT_SSH_COMMAND = new GitVersion(2, 3, 0);//GIT_SSH_COMMAND was introduced in git 2.3.0
  public final static GitVersion GIT_UPDATE_REFS_STDIN = new GitVersion(1, 8, 5); // update-refs with '--stdin' support
  public final static GitVersion GIT_CLEAN_LEARNED_EXCLUDE = new GitVersion(1, 7, 3); // clean first learned -e <pattern> and --exclude=<pattern> in 1.7.3
  /**
   * Git version supporting an empty credential helper - the only way to disable system/global/local cred helper
   */
  public final static GitVersion EMPTY_CRED_HELPER = new GitVersion(2, 9, 0);
  /** Git version supporting [credential] section in config (the first version including a6fc9fd3f4b42cd97b5262026e18bd451c28ee3c) */
  public final static GitVersion CREDENTIALS_SECTION_VERSION = new GitVersion(1, 7, 10);
  public final static GitVersion REV_PARSE_LEARNED_SHALLOW_CLONE = new GitVersion(2, 15, 0);

  private static final String ENABLE_REMOVAL_OF_OUTDATED_REFS = "teamcity.internal.git.removeOutdatedRefs.enable";

  protected final FS myFS;
  private final SmartDirectoryCleaner myDirectoryCleaner;
  protected final BuildProgressLogger myLogger;
  protected final AgentPluginConfig myPluginConfig;
  protected final GitFactory myGitFactory;
  protected final File myTargetDirectory;
  protected final String myRevision;
  protected final AgentGitVcsRoot myRoot;
  protected final String myFullBranchName;
  protected final AgentRunningBuild myBuild;
  private final CheckoutRules myRules;
  private final CheckoutMode myCheckoutMode;
  protected final MirrorManager myMirrorManager;
  protected final SubmoduleManager mySubmoduleManager;
  //remote repository refs, stored in field in order to not run 'git ls-remote' command twice
  private Refs myRemoteRefs;

  public UpdaterImpl(@NotNull FS fs,
                     @NotNull AgentPluginConfig pluginConfig,
                     @NotNull MirrorManager mirrorManager,
                     @NotNull SmartDirectoryCleaner directoryCleaner,
                     @NotNull GitFactory gitFactory,
                     @NotNull AgentRunningBuild build,
                     @NotNull VcsRoot root,
                     @NotNull String version,
                     @NotNull File targetDir,
                     @NotNull CheckoutRules rules,
                     @NotNull CheckoutMode checkoutMode,
                     final SubmoduleManager submoduleManager,
                     @NotNull AgentTokenStorage tokenStorage) throws VcsException {
    myFS = fs;
    myPluginConfig = pluginConfig;
    myDirectoryCleaner = directoryCleaner;
    myGitFactory = gitFactory;
    myBuild = build;
    myLogger = build.getBuildLogger();
    myRevision = GitUtils.versionRevision(version);
    myTargetDirectory = targetDir;
    mySubmoduleManager = submoduleManager;
    myRoot = new AgentGitVcsRoot(mirrorManager, myTargetDirectory, root, tokenStorage, detectExtraHTTPCredentialsInBuild(build));
    if (myRoot.isModifiedFetchUrlUsed()) {
      myLogger.logMessage(DefaultMessagesInfo.createTextMessage(
        String.format("Fetch URL %s is used instead of %s for VCS root %s on the agent due to a substitution rule defined in one of the %s.* agent properties",
                      myRoot.getRepositoryFetchURL(),
                      myRoot.getProperty(jetbrains.buildServer.buildTriggers.vcs.git.Constants.FETCH_URL),
                      myRoot.getName(),
                      GitVcsRoot.FETCH_URL_MAPPING_PROPERTY_NAME_PREFIX
                      )));
    }
    myFullBranchName = getBranch();
    myRules = rules;
    myCheckoutMode = checkoutMode;
    myMirrorManager = mirrorManager;
  }


  private String getBranch() {
    String defaultBranchName = GitUtils.expandRef(myRoot.getRef());
    String rootBranchParam = GitUtils.getGitRootBranchParamName(myRoot.getOriginalRoot());
    String customBranch = myBuild.getSharedConfigParameters().get(rootBranchParam);
    return customBranch != null ? customBranch : defaultBranchName;
  }


  public void update() throws VcsException {
    logInfo("Git version: " + myPluginConfig.getGitVersion());
    logGitConfig();
    logSshOptions(myPluginConfig.getGitVersion());
    checkAuthMethodIsSupported();
    doUpdate();
    checkNoDiffWithUpperLimitRevision();
  }

  private void logGitConfig() {
    if (!myPluginConfig.isDebugSsh()) return;
    final String config;
    try {
      config = myGitFactory.create(myTargetDirectory).listConfig().call();
    } catch (VcsException e) {
      return;
    }
    logDebug(config);
  }

  private void logSshOptions(@NotNull GitVersion gitVersion) {
    if (myPluginConfig.isUseNativeSSH()) {
      logInfo("Will use native ssh (" + PluginConfigImpl.USE_NATIVE_SSH + "=true)");
      if (myRoot.getAuthSettings().getAuthMethod() == AuthenticationMethod.TEAMCITY_SSH_KEY) {
        if (gitVersion.isLessThan(UpdaterImpl.MIN_GIT_SSH_COMMAND)) {
          logWarn("Git " + gitVersion + " doesn't support the GIT_SSH_COMMAND environment variable, uploaded SSH keys will not work. " +
                  "Required git version is " + UpdaterImpl.MIN_GIT_SSH_COMMAND);
        } else if (!myPluginConfig.isUseGitSshCommand()) {
          logWarn("Use of GIT_SSH_COMMAND is disabled (" + PluginConfigImpl.USE_GIT_SSH_COMMAND + "=false), uploaded SSH keys will not work.");
        }
      }
    }
  }

  private void logInfo(@NotNull String msg) {
    myLogger.message(msg);
    Loggers.VCS.info(msg);
  }

  private void logWarn(@NotNull String msg) {
    myLogger.warning(msg);
    Loggers.VCS.warn(msg);
  }

  private void logDebug(@NotNull String msg) {
    myLogger.debug(msg);
    Loggers.VCS.debug(msg);
  }

  protected void doUpdate() throws VcsException {
    String message = "Update checkout directory (" + myTargetDirectory.getAbsolutePath() + ")";
    myLogger.activityStarted(message, GitBuildProgressLogger.GIT_PROGRESS_ACTIVITY);
    try {
      logStartUpdating();
      initGitRepository();
      doFetch();
      updateSources();
    } finally {
      myLogger.activityFinished(message, GitBuildProgressLogger.GIT_PROGRESS_ACTIVITY);
    }
  }

  private void logStartUpdating() {
    LOG.info("Starting update of root " + myRoot.getName() + " in " + myTargetDirectory + " to revision " + myRevision);
    LOG.debug("Updating " + myRoot.debugInfo());
  }


  private void initGitRepository() throws VcsException {
    final File gitDir = new File(myTargetDirectory, ".git");
    if (gitDir.exists()) {
      if ((myPluginConfig.isUseShallowClone(myRoot) || myPluginConfig.isUseShallowCloneFromMirrorToCheckoutDir()) ^ isShallowRepository(gitDir)) {
        // settings changed: recreate repo in checkout dir
        initDirectory(true);
      } else {
        try {
          configureRemoteUrl(gitDir, myRoot.getRepositoryFetchURL());
          setupExistingRepository();
          configureSparseCheckout();
        } catch (Exception e) {
          LOG.warn("Do clean checkout due to errors while configure use of local mirrors", e);
          initDirectory(true);
        }
      }
    } else {
      initDirectory(false);
    }
    getSSLInvestigator(myRoot.getRepositoryFetchURL()).setCertificateOptions(myGitFactory.create(myTargetDirectory));
    removeOrphanedIdxFiles(gitDir);
  }

  @NotNull
  protected SSLInvestigator getSSLInvestigator(@NotNull CommonURIish remoteUrl) {
    return new SSLInvestigator(remoteUrl.<URIish>get(), myBuild.getAgentTempDirectory().getPath(),
                               myBuild.getAgentConfiguration());
  }

  private boolean isShallowRepository(@NotNull File gitDir) {
    if (!myPluginConfig.getGitVersion().isLessThan(REV_PARSE_LEARNED_SHALLOW_CLONE)) {
      try {
        return "true".equals(myGitFactory.create(gitDir).revParse().setShallow(true).call());
      } catch (VcsException e) {
        LOG.warn("Exception while running git rev-parse --is-shallow-repository", e);
      }
    }
    return new File(gitDir, "shallow").exists();
  }

  protected void setupNewRepository() throws VcsException {
  }


  protected void setupExistingRepository() throws VcsException {
    removeUrlSections();
    removeLfsStorage();
    disableAlternates();
  }


  private void updateSources() throws VcsException {
    final AgentGitFacade git = myGitFactory.create(myTargetDirectory);
    boolean branchChanged = false;
    removeIndexLock();
    if (GitUtilsAgent.isRegularBranch(myFullBranchName)) {
      String branchName = GitUtilsAgent.getShortBranchName(myFullBranchName);
      Branches branches = git.listBranches(false);
      if (branches.isCurrentBranch(branchName)) {
        removeIndexLock();
        runAndFixIndexErrors(git, new VcsCommand() {
          @Override
          public void call() throws VcsException {
            reset(git).setHard(true).setRevision(myRevision).call();
          }
        });
        git.setUpstream(branchName, GitUtils.createRemoteRef(myFullBranchName)).call();
      } else {
        branchChanged = true;
        if (!branches.contains(branchName)) {
          git.createBranch()
            .setName(branchName)
            .setStartPoint(GitUtils.createRemoteRef(myFullBranchName))
            .setTrack(true)
            .call();
        }
        git.updateRef().setRef(myFullBranchName).setRevision(myRevision).call();
        final String finalBranchName = branchName;
        runAndFixIndexErrors(git, new VcsCommand() {
          @Override
          public void call() throws VcsException {
            checkout(git)
              .setForce(true)
              .setBranch(finalBranchName)
              .setQuiet(isQuietCheckout())
              .setTimeout(myPluginConfig.getCheckoutIdleTimeoutSeconds())
              .call();
          }
        });
        if (branches.contains(branchName)) {
          git.setUpstream(branchName, GitUtils.createRemoteRef(myFullBranchName)).call();
        }
      }
    } else if (GitUtilsAgent.isTag(myFullBranchName)) {
      Ref tag = getRef(myTargetDirectory, myFullBranchName);
      if (tag == null || !tag.getObjectId().name().equals(myRevision)) {
        runAndFixIndexErrors(git, () -> forceCheckout(git, myRevision));
      } else {
        runAndFixIndexErrors(git, () -> forceCheckout(git, myFullBranchName.substring("refs/tags/".length())));
      }
      branchChanged = true;
    } else {
      runAndFixIndexErrors(git, () -> forceCheckout(git, myRevision));
      branchChanged = true;
    }

    doClean(branchChanged);
    if (myRoot.isCheckoutSubmodules()) {
      checkoutSubmodules(myTargetDirectory);
    }
  }

  protected boolean isQuietCheckout() {
    return !"false".equalsIgnoreCase(myBuild.getSharedConfigParameters().get("teamcity.internal.git.quietCheckout"));
  }

  private void forceCheckout(@NotNull AgentGitFacade git, @NotNull String what) throws VcsException {
    checkout(git)
      .setBranch(what)
      .setForce(true)
      .setQuiet(isQuietCheckout())
      .setTimeout(myPluginConfig.getCheckoutIdleTimeoutSeconds())
      .call();
  }

  private void runAndFixIndexErrors(@NotNull AgentGitFacade git, @NotNull VcsCommand cmd) throws VcsException {
    try {
      cmd.call();
    } catch (GitIndexCorruptedException e) {
      File gitIndex = e.getGitIndex();
      myLogger.message("Git index '" + gitIndex.getAbsolutePath() + "' is corrupted, remove it and repeat the command");
      FileUtil.delete(gitIndex);
      cmd.call();
    } catch (GitOutdatedIndexException e) {
      myLogger.message("Refresh outdated git index and repeat the command");
      updateIndex(git).reallyRefresh(true).quiet(true).call();
      cmd.call();
    } catch (Exception e) {
      if (e instanceof VcsException)
        throw (VcsException) e;
      throw new VcsException(e);
    }
  }


  @NotNull
  private UpdateIndexCommand updateIndex(final AgentGitFacade git) {
    return git.updateIndex()
      .setAuthSettings(myRoot.getAuthSettings())
      .setUseNativeSsh(myPluginConfig.isUseNativeSSH());
  }


  @NotNull
  private ResetCommand reset(final AgentGitFacade git) {
    return git.reset()
      .setAuthSettings(myRoot.getAuthSettings())
      .setUseNativeSsh(myPluginConfig.isUseNativeSSH());
  }

  @NotNull
  protected CheckoutCommand checkout(final AgentGitFacade git) {
    return git.checkout()
      .setAuthSettings(myRoot.getAuthSettings())
      .setUseNativeSsh(myPluginConfig.isUseNativeSSH());
  }

  protected void updateSubmodules(@NotNull final File repositoryDir) throws VcsException, ConfigInvalidException, IOException {
    AgentGitFacade git = myGitFactory.create(repositoryDir);
    SubmoduleUpdateCommand submoduleUpdate = git.submoduleUpdate()
            .setAuthSettings(myRoot.getAuthSettings())
            .setUseNativeSsh(myPluginConfig.isUseNativeSSH())
            .setTimeout(myPluginConfig.getSubmoduleUpdateTimeoutSeconds())
            .setForce(isForceUpdateSupported());
    submoduleUpdate.addConfig("protocol.file.allow", "always");
    submoduleUpdate.call();
  }

  private void checkoutSubmodules(@NotNull final File repositoryDir) throws VcsException {
    try {
      final Config gitModules = readGitModules(repositoryDir);
      if (gitModules == null) return;

      myLogger.message("Checkout submodules in " + repositoryDir);
      AgentGitFacade git = myGitFactory.create(repositoryDir);
      git.submoduleInit().call();
      git.submoduleSync().call();

      addSubmoduleUsernames(repositoryDir, gitModules);

      long start = System.currentTimeMillis();
      updateSubmodules(repositoryDir);

      if (recursiveSubmoduleCheckout()) {
        for (String submodulePath : getSubmodulePaths(gitModules)) {
          checkoutSubmodules(new File(repositoryDir, submodulePath));
        }
      }
      Loggers.VCS.info("Submodules update in " + repositoryDir.getAbsolutePath() + " is finished in " +
                       (System.currentTimeMillis() - start) + " ms");

    } catch (Exception e) {
      Loggers.VCS.error("Submodules checkout failed", e);
      throw new VcsException("Submodules checkout failed: " + e.getMessage(), e);
    }
  }


  protected boolean isForceUpdateSupported() {
    return !GIT_WITH_FORCE_SUBMODULE_UPDATE.isGreaterThan(myPluginConfig.getGitVersion());
  }


  private void addSubmoduleUsernames(@NotNull File repositoryDir, @NotNull Config gitModules)
    throws IOException, VcsException {
    if (!myPluginConfig.isUseMainRepoUserForSubmodules())
      return;

    Loggers.VCS.info("Update submodules credentials");

    AuthSettings auth = myRoot.getAuthSettings();
    final String userName = auth.getUserName();
    if (userName == null) {
      Loggers.VCS.info("Username is not specified in the main VCS root settings, skip updating submodule credentials");
      return;
    }

    Repository r = null;
    try {
      r = new RepositoryBuilder().setBare().setGitDir(getGitDir(repositoryDir)).build();
      StoredConfig gitConfig = r.getConfig();

      Set<String> submodules = gitModules.getSubsections("submodule");
      if (submodules.isEmpty()) {
        Loggers.VCS.info("No submodule sections found in " + new File(repositoryDir, ".gitmodules").getCanonicalPath()
                         + ", skip updating credentials");
        return;
      }
      File modulesDir = getModulesDir(r.getDirectory());
      for (String submoduleName : submodules) {
        //The 'git submodule sync' command executed before resolves relative submodule urls
        //from .gitmodules and writes them into .git/config. We should use resolved urls in
        //order to add parent repository username to submodules with relative urls.
        String url = gitConfig.getString("submodule", submoduleName, "url");
        if (url == null) {
          Loggers.VCS.info(".git/config doesn't contain an url for submodule '" + submoduleName + "', use url from .gitmodules");
          url = gitModules.getString("submodule", submoduleName, "url");
        }
        Loggers.VCS.info("Update credentials for submodule with url " + url);
        if (url == null || !isRequireAuth(url)) {
          Loggers.VCS.info("Url " + url + " does not require authentication, skip updating credentials");
          continue;
        }
        try {
          URIish uri = new URIish(url);
          String updatedUrl = uri.setUser(userName).toASCIIString();
          gitConfig.setString("submodule", submoduleName, "url", updatedUrl);
          String submodulePath = gitModules.getString("submodule", submoduleName, "path");
          if (submodulePath != null && myPluginConfig.isUpdateSubmoduleOriginUrl()) {
            File submoduleDir = new File(modulesDir, submodulePath);
            if (submoduleDir.isDirectory() && new File(submoduleDir, Constants.CONFIG).isFile())
              updateOriginUrl(submoduleDir, updatedUrl);
          }
          Loggers.VCS.debug("Submodule url " + url + " changed to " + updatedUrl);
        } catch (URISyntaxException e) {
          Loggers.VCS.warn("Error while parsing an url " + url + ", skip updating submodule credentials", e);
        } catch (Exception e) {
          Loggers.VCS.warn("Error while updating the '" + submoduleName + "' submodule url", e);
        }
      }
      gitConfig.save();
    } finally {
      if (r != null) {
        r.close();
      }
    }
  }

  @NotNull
  private File getModulesDir(final File repoDir) {
    return new File(repoDir, Constants.MODULES);
  }

  private void updateOriginUrl(@NotNull File repoDir, @NotNull String url) throws IOException {
    Repository r = null;
    try {
      r = new RepositoryBuilder().setBare().setGitDir(repoDir).build();
      StoredConfig config = r.getConfig();
      config.setString("remote", "origin", "url", url);
      config.save();
    } finally {
      if (r != null) {
        r.close();
      }
    }
  }


  @Nullable
  protected Config readGitModules(@NotNull File repoDir) throws VcsException {
    final File dotGitModules = new File(repoDir, ".gitmodules");
    if (!dotGitModules.exists()) return null;

    try {
      final String content = FileUtil.readText(dotGitModules);
      final Config config = new Config();
      config.fromText(content);
      return config;
    } catch (Exception e) {
      Loggers.VCS.error("Error while reading " + dotGitModules.getAbsolutePath() + ": " + e.getMessage());
      throw new VcsException("Error while reading " + dotGitModules.getAbsolutePath(), e);
    }
  }


  private boolean isRequireAuth(@NotNull String url) {
    try {
      final URIish result = new URIish(url);
      return result.getUser() == null && URIishHelperImpl.requiresCredentials(result);
    } catch (URISyntaxException e) {
      return false;
    }
  }


  protected Set<String> getSubmodulePaths(@NotNull Config config) {
    Set<String> paths = new HashSet<String>();
    Set<String> submodules = config.getSubsections("submodule");
    for (String submoduleName : submodules) {
      String submodulePath = config.getString("submodule", submoduleName, "path");
      if (submodulePath == null) {
        logWarn("No path found in .gitmodules for submodule " + submoduleName);
        continue;
      }
      paths.add(submodulePath.replaceAll("/", Matcher.quoteReplacement(File.separator)));
    }
    return paths;
  }

  private boolean recursiveSubmoduleCheckout() {
    return SubmodulesCheckoutPolicy.CHECKOUT.equals(myRoot.getSubmodulesCheckoutPolicy()) ||
           SubmodulesCheckoutPolicy.CHECKOUT_IGNORING_ERRORS.equals(myRoot.getSubmodulesCheckoutPolicy());
  }


  private void doClean(boolean branchChanged) throws VcsException {
    if (myRoot.getCleanPolicy() == AgentCleanPolicy.ALWAYS ||
        branchChanged && myRoot.getCleanPolicy() == AgentCleanPolicy.ON_BRANCH_CHANGE) {
      myLogger.message("Cleaning " + myRoot.getName() + " in " + myTargetDirectory + " the file set " + myRoot.getCleanFilesPolicy());
      cleanCommand().call();
      if (myRoot.isCheckoutSubmodules())
        cleanSubmodules(myTargetDirectory);
    }
  }

  @NotNull
  private CleanCommand cleanCommand() {
    final CleanCommand cmd = myGitFactory.create(myTargetDirectory).clean().setCleanPolicy(myRoot.getCleanFilesPolicy());
    if (CleanCommandUtil.isCleanEnabled(myRoot.getOriginalRoot()) && myPluginConfig.isCleanCommandRespectsOtherRoots()) {
      final String targetPath = getTargetPath(myTargetDirectory, myBuild.getCheckoutDirectory());

      for (VcsRootEntry otherRoot : myBuild.getVcsRootEntries()) {
        if (myRoot.getId() == otherRoot.getVcsRoot().getId()) continue;

        for (String path : getPathsToExclude(otherRoot, targetPath)) {
          cmd.addExclude("/" + path); // slash addresses only first-level paths, see TW-67483
        }
      }
    }
    return cmd;
  }

  /**
   * @param targetPath checkout directory of current vcs root
   * @param includeRule one of the include rules of other vcs root, such that targetPath starts with includeRule.getTo()
   * @param excludeRules exclude rules of other vcs root
   * @return if current vcs root can be checked out in the targetPath, without interfering with other vcs root(e.g. it won't delete files of other root with git clean)
   */
  private static boolean canCheckoutIntoSameDir(@NotNull String targetPath, @NotNull IncludeRule includeRule, @NotNull List<FileRule> excludeRules) {
    try {
      return excludeRules.stream().anyMatch(excludeRule -> {
        String exclude = excludeRule.getFrom();
        return (includeRule.getFrom().isEmpty() || Paths.get(exclude).startsWith(Paths.get(includeRule.getFrom()))) && //check if exclude rule affects include rule
               Paths.get(targetPath).startsWith(Paths.get(includeRule.getTo(), exclude.substring(includeRule.getFrom().length()))); // check that target path replaces directory excluded by the excludeRule
      });
    } catch (Throwable ignored) {
      return false;
    }
  }

  @NotNull
  public Collection<String> getPathsToExclude(@NotNull VcsRootEntry otherRoot, @NotNull String targetPath) {
    final SortedSet<String> clashingPaths = new TreeSet<>(); // we need an ordered tree here

    for (IncludeRule rule : otherRoot.getCheckoutRules().getRootIncludeRules()) {
      final String to = rule.getTo();
      if (targetPath.equals(to)) {
        myLogger
          .warning("Two VCS roots shouldn't be checked out into the same folder: performing git clean for " + myRoot.getName() +
                   " will remove files checked out for " + otherRoot.getVcsRoot().getName() +
                   " and vice versa. Please configure checkout to separate folders using Checkout rules.");
        return Collections.emptyList();
      }

      if (targetPath.isEmpty()) {
        clashingPaths.add(to);
      } else if (to.isEmpty() || targetPath.startsWith(to + "/")) {
        // check if the targetPath is excluded by checkout rules. In this case the contents of the directory can be managed and cleaned by myRoot, without accidentally cleaning files of otherRoot
        // For example we don't want to add warning if we have separate vcs root for a submodule and have it exluded in the main vcs root, see TW-82946
        if (!canCheckoutIntoSameDir(targetPath, rule, otherRoot.getCheckoutRules().getExcludeRules())) {
          // case when this root is "inside" the other root - we can't fix this using exclude, only report
          // (TBD: another option is not to run clean at all)
          myLogger
            .warning("Two VCS roots shouldn't be checked out into the same folder: performing git clean for " + myRoot.getName() +
                     " may remove files checked out for " + otherRoot.getVcsRoot().getName() +
                     ". Please configure checkout to separate folders using Checkout rules.");
        }
      } else if (to.startsWith(targetPath + "/")) {
        clashingPaths.add(to.substring(targetPath.length() + 1));
      }
    }

    final List<String> result = new ArrayList<>();
    clashingPaths.forEach(path -> {
      // here we preserve more common paths
      if (result.isEmpty() || !path.startsWith(result.get(result.size() - 1) + "/")) result.add(path);
    });

    if (result.isEmpty() || CleanCommandUtil.isCleanCommandSupportsExclude(myPluginConfig.getGitVersion())) {
      return result;
    }

    myLogger
      .warning("git version " + myPluginConfig.getGitVersion() + " doesn't support --exclude option for clean command: performing git clean for " + myRoot.getName() +
               " will remove files checked out for " + otherRoot.getVcsRoot().getName() +
               " and vice versa. Please either update git executable to a version above " + GitVersion.DEPRECATED + " or configure checkout to separate folders using Checkout rules.");
    return Collections.emptyList();
  }

  @NotNull
  private static String getTargetPath(@NotNull File targetDir, @NotNull File checkoutDirectory) {
    String path = FileUtil.getRelativePath(checkoutDirectory, targetDir);
    if (path == null) {
      path = targetDir.getAbsolutePath();
    }
    return ".".equals(path) ? "" : path.replace("\\", "/");
  }

  private void cleanSubmodules(@NotNull File repositoryDir) throws VcsException {
    final Config gitModules = readGitModules(repositoryDir);
    if (gitModules == null) return;

    for (String submodulePath : getSubmodulePaths(gitModules)) {
      File submoduleDir = new File(repositoryDir, submodulePath);
      try {
        myLogger.message("Cleaning files in " + submoduleDir + " the file set " + myRoot.getCleanFilesPolicy());
        myGitFactory.create(submoduleDir).clean().setCleanPolicy(myRoot.getCleanFilesPolicy()).call();
      } catch (Exception e) {
        Loggers.VCS.error("Error while cleaning files in " + submoduleDir.getAbsolutePath(), e);
      }
      if (recursiveSubmoduleCheckout())
        cleanSubmodules(submoduleDir);
    }
  }


  protected void removeUrlSections() throws VcsException {
    Repository r = null;
    try {
      r = new RepositoryBuilder().setWorkTree(myTargetDirectory).build();
      StoredConfig config = r.getConfig();
      Set<String> urlSubsections = config.getSubsections("url");
      for (String subsection : urlSubsections) {
        config.unsetSection("url", subsection);
      }
      config.save();
    } catch (IOException e) {
      String msg = "Error while remove url.* sections";
      LOG.error(msg, e);
      throw new VcsException(msg + ": " + e.getMessage(), e);
    } finally {
      if (r != null)
        r.close();
    }
  }


  private void removeLfsStorage() throws VcsException {
    Repository r = null;
    try {
      r = new RepositoryBuilder().setWorkTree(myTargetDirectory).build();
      StoredConfig config = r.getConfig();
      config.unsetSection("lfs", null);
      config.save();
    } catch (IOException e) {
      String msg = "Error while removing lfs.storage section";
      LOG.error(msg, e);
      throw new VcsException(msg + ": " + e.getMessage(), e);
    } finally {
      if (r != null)
        r.close();
    }
  }


  protected void disableAlternates() {
    FileUtil.delete(new File(myTargetDirectory, ".git" + File.separator + "objects" + File.separator + "info" + File.separator + "alternates"));
  }


  @Nullable
  protected Ref getRef(@NotNull File repositoryDir, @NotNull String ref) {
    Map<String, Ref> refs = myGitFactory.create(repositoryDir).showRef().setPattern(ref).call().getValidRefs();
    return refs.isEmpty() ? null : refs.get(ref);
  }


  /**
   * If some git process crashed in this repository earlier it can leave lock files for index.
   * This method delete such lock file if it exists (with warning message), otherwise git operation will fail.
   */
  private void removeIndexLock() {
    File indexLock = new File(myTargetDirectory, ".git" + File.separator + "index.lock");
    if (indexLock.exists()) {
      myLogger.warning("The .git/index.lock file exists. This probably means a git process crashed in this repository earlier. Deleting lock file");
      FileUtil.delete(indexLock);
    }
  }


  private void doFetch() throws VcsException {
    boolean outdatedRefsFound = removeOutdatedRefs(myTargetDirectory);
    ensureCommitLoaded(outdatedRefsFound);
  }


  protected void ensureCommitLoaded(boolean fetchRequired) throws VcsException {
    fetchFromOriginalRepository(fetchRequired);
  }


  protected void fetchFromOriginalRepository(boolean fetchRequired) throws VcsException {
    throwNoCommitFoundIfNecessary(getCommitLoader(myTargetDirectory).loadCommitInBranch(myRevision, myFullBranchName, fetchRequired));
  }

  protected void throwNoCommitFoundIfNecessary(boolean commitFound) throws RevisionNotFoundException {
    if (!commitFound) {
      throw new RevisionNotFoundException("Cannot find commit " + myRevision + " in the " + myRoot.getRepositoryFetchURL().toASCIIString() + " repository, " +
                                          "possible reason: " + myFullBranchName + " branch was updated and the commit selected for the build is not reachable anymore");

    }
  }

  private void checkAuthMethodIsSupported() throws VcsException {
    checkAuthMethodIsSupported(myRoot, myPluginConfig);
  }

  static void checkAuthMethodIsSupported(@NotNull GitVcsRoot root, @NotNull AgentPluginConfig config) throws VcsException {
    if ("git".equals(root.getRepositoryFetchURL().getScheme()))
      return;//anonymous protocol, don't check anything
    AuthSettings authSettings = root.getAuthSettings();
    switch (authSettings.getAuthMethod()) {
      case PASSWORD: case ACCESS_TOKEN:
        if ("http".equals(root.getRepositoryFetchURL().getScheme()) ||
            "https".equals(root.getRepositoryFetchURL().getScheme())) {
          GitVersion actualVersion = config.getGitVersion();
          GitVersion requiredVersion = getMinVersionForHttpAuth();
          if (actualVersion.isLessThan(requiredVersion)) {
            throw new VcsException("Password authentication requires git " + requiredVersion +
                                   ", found git version is " + actualVersion +
                                   ". Upgrade git or use different authentication method.");
          }
        } else {
          throw new VcsException("TeamCity doesn't support authentication method '" +
                                 root.getAuthSettings().getAuthMethod().uiName() +
                                 "' with agent checkout and non-http protocols. Please use different authentication method.");
        }
        break;
      case PRIVATE_KEY_FILE:
        throw new VcsException("TeamCity doesn't support authentication method '" +
                               root.getAuthSettings().getAuthMethod().uiName() +
                               "' with agent checkout. Please use different authentication method.");
    }
  }

  @NotNull
  private static GitVersion getMinVersionForHttpAuth() {
    //core.askpass parameter was added in 1.7.1, but
    //experiments show that it works only in 1.7.3 on linux
    //and msysgit 1.7.3.1-preview20101002.
    return new GitVersion(1, 7, 3);
  }

  /**
   * Clean and init directory and configure remote origin
   *
   * @throws VcsException if there are problems with initializing the directory
   */
  private void initDirectory(boolean removeTargetDir) throws VcsException {
    if (removeTargetDir) {
      BuildDirectoryCleanerCallback c = new BuildDirectoryCleanerCallback(myLogger, LOG);
      myDirectoryCleaner.cleanFolder(myTargetDirectory, c);
      //noinspection ResultOfMethodCallIgnored
      if (c.isHasErrors()) {
        throw new VcsException("Unable to clean directory " + myTargetDirectory + " for VCS root " + myRoot.getName());
      }
    }

    myTargetDirectory.mkdirs();
    myLogger.message("The .git directory is missing in '" + myTargetDirectory + "'. Running 'git init'...");
    final AgentGitFacade gitFacade = myGitFactory.create(myTargetDirectory);
    gitFacade.init().call();
    validateUrls();
    configureRemoteUrl(new File(myTargetDirectory, ".git"), myRoot.getRepositoryFetchURL());

    URIish fetchUrl = myRoot.getRepositoryFetchURL().get();
    URIish url = myRoot.getRepositoryPushURL().get();
    String pushUrl = url == null ? null : url.toString();
    if (pushUrl != null && !pushUrl.equals(fetchUrl.toString())) {
      gitFacade.setConfig().setPropertyName("remote.origin.pushurl").setValue(pushUrl).call();
    }
    setupNewRepository();
    configureSparseCheckout();
  }

  void configureRemoteUrl(@NotNull File gitDir, CommonURIish remoteUrl) throws VcsException {
    RemoteRepositoryConfigurator cfg = new RemoteRepositoryConfigurator();
    cfg.setGitDir(gitDir);
    cfg.setExcludeUsernameFromHttpUrls(myPluginConfig.isExcludeUsernameFromHttpUrl() && !myPluginConfig.getGitVersion().isLessThan(UpdaterImpl.CREDENTIALS_SECTION_VERSION));
    cfg.configure(remoteUrl);
  }


  private void configureSparseCheckout() throws VcsException {
    if (myCheckoutMode == CheckoutMode.SPARSE_CHECKOUT) {
      setupSparseCheckout();
    } else {
      myGitFactory.create(myTargetDirectory).setConfig().setPropertyName("core.sparseCheckout").setValue("false").call();
    }
  }

  private void setupSparseCheckout() throws VcsException {
    myGitFactory.create(myTargetDirectory).setConfig().setPropertyName("core.sparseCheckout").setValue("true").call();
    File sparseCheckout = new File(myTargetDirectory, ".git/info/sparse-checkout");
    boolean hasIncludeRules = false;
    StringBuilder sparseCheckoutContent = new StringBuilder();
    for (IncludeRule rule : myRules.getIncludeRules()) {
      if (isEmpty(rule.getFrom())) {
        sparseCheckoutContent.append("/*\n");
      } else {
        sparseCheckoutContent.append("/").append(rule.getFrom()).append("\n");
      }
      hasIncludeRules = true;
    }
    if (!hasIncludeRules) {
      sparseCheckoutContent.append("/*\n");
    }
    for (FileRule rule : myRules.getExcludeRules()) {
      sparseCheckoutContent.append("!/").append(rule.getFrom()).append("\n");
    }
    try {
      FileUtil.writeFileAndReportErrors(sparseCheckout, sparseCheckoutContent.toString());
    } catch (IOException e) {
      LOG.warn("Error while writing sparse checkout config, disable sparse checkout", e);
      myGitFactory.create(myTargetDirectory).setConfig().setPropertyName("core.sparseCheckout").setValue("false").call();
    }
  }


  private void validateUrls() {
    URIish fetch = myRoot.getRepositoryFetchURL().get();
    if (GitUtilsAgent.isAnonymousGitWithUsername(fetch))
      LOG.warn("Fetch URL '" + fetch.toString() + "' for root " + myRoot.getName() + " uses an anonymous git protocol and contains a username, fetch will probably fail");
    URIish push  = myRoot.getRepositoryPushURL().get();
    if (!fetch.equals(push) && GitUtilsAgent.isAnonymousGitWithUsername(push))
      LOG.warn("Push URL '" + push.toString() + "'for root " + myRoot.getName() + " uses an anonymous git protocol and contains a username, push will probably fail");
  }

  private boolean shouldRemoveOutdatedRefs() {
    String paramValue = myBuild.getSharedConfigParameters().get(ENABLE_REMOVAL_OF_OUTDATED_REFS);
    if (paramValue != null) {
      return Boolean.parseBoolean(paramValue);
    }
    return TeamCityProperties.getBooleanOrTrue(ENABLE_REMOVAL_OF_OUTDATED_REFS);
  }

  protected boolean removeOutdatedRefs(@NotNull File workingDir) throws VcsException {
    if (!shouldRemoveOutdatedRefs()) {
      return false;
    }
    boolean outdatedRefsRemoved = false;
    final AgentGitFacade git = myGitFactory.create(workingDir);

    final ShowRefCommand showRefCommand = git.showRef();
    showRefCommand.throwExceptionOnNonZeroExitCode(false);

    final ShowRefResult showRefResult = showRefCommand.call();
    final Set<String> invalidRefs = showRefResult.getInvalidRefs();
    if (showRefResult.isFailed() && invalidRefs.isEmpty()) {
      // show-ref command failed, but it reported no invalid refs (or we couldn't parse them),
      // we suspect, that there are invalid refs though and remove all refs, see TW-74592
      removeRefs(workingDir);
      return true;
    }
    final Refs localRefs = new Refs(showRefResult.getValidRefs());
    if (localRefs.isEmpty() && invalidRefs.isEmpty())
      return false;
    if (!invalidRefs.isEmpty()) {
      removeRefs(git, invalidRefs);
      outdatedRefsRemoved = true;
    }
    final Refs remoteRefs;
    try {
      remoteRefs = getRemoteRefs(workingDir);
    } catch (VcsException e) {
      if (CommandUtil.isCanceledError(e))
        throw e;
      String msg = "Failed to list remote repository refs, outdated local refs will not be cleaned";
      LOG.warn(msg);
      myLogger.warning(msg);
      return outdatedRefsRemoved;
    }
    //We remove both outdated local refs (e.g. refs/heads/topic) and outdated remote
    //tracking branches (refs/remote/origin/topic), while git remote origin prune
    //removes only the latter. We need that because in some cases git cannot handle
    //rename of the branch (TW-28735).
    final List<String> localRefsToDelete = new ArrayList<String>();
    for (Ref localRef : localRefs.list()) {
      Ref correspondingRemoteRef = createCorrespondingRemoteRef(localRef);
      if (remoteRefs.isOutdated(correspondingRemoteRef)) {
        localRefsToDelete.add(localRef.getName());
      }
    }
    if (!localRefsToDelete.isEmpty()) {
      removeRefs(git, localRefsToDelete);
      outdatedRefsRemoved = true;
    }
    return outdatedRefsRemoved;
  }

  private void removeRefs(@NotNull File workingDir) {
//    FileUtil.delete(new File(workingDir, ".git/HEAD"));
    FileUtil.delete(new File(workingDir, ".git/FETCH_HEAD"));
    FileUtil.delete(new File(workingDir, ".git/packed-refs"));
    recreateRefsFolder(workingDir);
  }

  private void recreateRefsFolder(@NotNull File workingDir) {
    final File refsDir = new File(workingDir, ".git/refs");
    FileUtil.delete(refsDir);
    try {
      FileUtil.createDir(refsDir); // ref folder must be present in a valid .git repo
    } catch (IOException e) {
      myLogger.warning("Failed to re-create refs folder");
    }
  }

  private void removeRefs(final AgentGitFacade git, final Collection<String> invalidRefs) throws VcsException {
    int size = invalidRefs.size();
    if (size == 0) return;
    if (size == 1 || myPluginConfig.getGitVersion().isLessThan(UpdaterImpl.GIT_UPDATE_REFS_STDIN)) {
      if (size > 100) {
        logWarn("Removing a lot of refs (" + size + ") may be inefficient using git " + myPluginConfig.getGitVersion()
                + ". It's recommended to update git to latest version");
      }
      for (String invalidRef : invalidRefs) {
        git.updateRef().setRef(invalidRef).delete().call();
      }
    } else {
      List<List<String>> batches = makeBatches(new ArrayList<String>(invalidRefs), 1000);
      for (final List<String> batch : batches) {
        if (batch.isEmpty()) continue;
        logInfo("Removing refs: " + batch);
        UpdateRefBatchCommand command = git.updateRefBatch();
        for (final String invalidRef : batch) {
          command.delete(invalidRef, null);
        }
        command.call();
      }
    }
  }

  private static <E> List<List<E>> makeBatches(List<E> source, int eachSize) {
    if (source.isEmpty()) return Collections.emptyList();
    final int size = source.size();
    if (size < eachSize) return Collections.singletonList(source);
    ArrayList<List<E>> result = new ArrayList<List<E>>();
    int start = 0;
    while (start < size) {
      List<E> sub = source.subList(start, Math.min(start + eachSize, size));
      if (!sub.isEmpty()) result.add(sub);
      start += eachSize;
    }
    return result;
  }

  @NotNull
  protected Refs getRemoteRefs(@NotNull File workingDir) throws VcsException {
    if (myRemoteRefs != null && myTargetDirectory.equals(workingDir))
      return myRemoteRefs;
    AgentGitFacade git = myGitFactory.create(workingDir);
    myRemoteRefs = new Refs(git.lsRemote().setAuthSettings(myRoot.getAuthSettings())
      .setUseNativeSsh(myPluginConfig.isUseNativeSSH())
      .setTimeout(myPluginConfig.getLsRemoteTimeoutSeconds())
      .setRetryAttempts(myPluginConfig.getRemoteOperationAttempts())
      .call());
    return myRemoteRefs;
  }


  private boolean isRemoteTrackingBranch(@NotNull Ref localRef) {
    return localRef.getName().startsWith("refs/remotes/origin");
  }

  @NotNull
  private Ref createCorrespondingRemoteRef(@NotNull Ref localRef) {
    if (!isRemoteTrackingBranch(localRef))
      return localRef;
    return new RefImpl("refs/heads" + localRef.getName().substring("refs/remotes/origin".length()),
                       localRef.getObjectId().name());
  }



  private interface VcsCommand {
    void call() throws VcsException;
  }


  /**
   * Removes .idx files which don't have a corresponding .pack file
   * @param ditGitDir git dir
   */
  void removeOrphanedIdxFiles(@NotNull File ditGitDir) {
    if ("false".equals(myBuild.getSharedConfigParameters().get("teamcity.git.removeOrphanedIdxFiles"))) {
      //looks like this logic is always needed, if no problems will be reported we can drop the option
      return;
    }
    File packDir = new File(new File(ditGitDir, "objects"), "pack");
    File[] files = packDir.listFiles();
    if (files == null || files.length == 0)
      return;

    Set<String> packs = new HashSet<String>();
    for (File f : files) {
      String name = f.getName();
      if (name.endsWith(".pack")) {
        packs.add(name.substring(0, name.length() - 5));
      }
    }

    for (File f : files) {
      String name = f.getName();
      if (name.endsWith(".idx")) {
        if (!packs.contains(name.substring(0, name.length() - 4)))
          FileUtil.delete(f);
      }
    }
  }


  private void checkNoDiffWithUpperLimitRevision() {
    if ("false".equals(myBuild.getSharedConfigParameters().get("teamcity.git.checkDiffWithUpperLimitRevision"))) {
      return;
    }

    String upperLimitRevision = getUpperLimitRevision();
    if (upperLimitRevision == null) {
      return;
    }

    String message = "Check no diff with upper limit revision " + upperLimitRevision;
    myLogger.activityStarted(message, GitBuildProgressLogger.GIT_PROGRESS_ACTIVITY);
    try {
      if (!ensureCommitLoaded(upperLimitRevision)) {
        myLogger.warning("Failed to fetch " + upperLimitRevision + ", will not analyze diff with upper limit revision");
        return;
      }

      if (myRevision.equals(upperLimitRevision)) {
        myLogger.message("Build revision is the same as the upper limit revision, skip checking diff");
        return;
      }

      List<String> pathsMatchedByRules = getChangedFilesMatchedByRules(upperLimitRevision);
      if (!pathsMatchedByRules.isEmpty()) {
        StringBuilder msg = new StringBuilder();
        msg.append("Files matched by checkout rules changed between build revision and upper-limit revision\n");
        msg.append("Checkout rules: '").append(myRules.getAsString()).append("'\n");
        msg.append("Build revision: '").append(myRevision).append("'\n");
        msg.append("Upper limit revision: '").append(upperLimitRevision).append("'\n");
        msg.append("Files:\n");
        for (String path : pathsMatchedByRules) {
          msg.append("\t").append(path).append("\n");
        }
        myLogger.error(msg.toString());
        String type = "UpperLimitRevisionDiff";
        myLogger.logBuildProblem(BuildProblemData.createBuildProblem(type + myRoot.getId(), type, "Diff with upper limit revision found"));
      } else {
        myLogger.message("No diff matched by checkout rules found");
      }
    } finally {
      myLogger.activityFinished(message, GitBuildProgressLogger.GIT_PROGRESS_ACTIVITY);
    }
  }

  private boolean ensureCommitLoaded(@NotNull String commit) {
    try {
      return getCommitLoader(myTargetDirectory).loadCommit(commit);
    } catch (VcsException e) {
      LOG.warn("Error while fetching commit " + commit, e);
      return false;
    }
  }

  @NotNull
  private List<String> getChangedFilesMatchedByRules(@NotNull String upperLimitRevision) {
    List<String> pathsMatchedByRules = new ArrayList<String>();
    List<String> changedFiles = getChangedFiles(upperLimitRevision);
    for (String file : changedFiles) {
      if (myRules.map(file) != null) {
        pathsMatchedByRules.add(file);
      }
    }
    return pathsMatchedByRules;
  }

  @NotNull
  private List<String> getChangedFiles(@NotNull String upperLimitRevision) {
    try {
      return myGitFactory.create(myTargetDirectory).diff()
        .setFormat("--name-only")
        .setStartCommit(upperLimitRevision)
        .setExcludedCommits(Collections.singleton(myRevision))
        .call();
    } catch (VcsException e) {
      myLogger.warning("Error while computing changed files between build and upper limit revisions: " + e.toString());
      return Collections.emptyList();
    }
  }

  @Nullable
  private String getUpperLimitRevision() {
    String rootExtId = getVcsRootExtId();
    return rootExtId != null ? myBuild.getSharedConfigParameters().get("teamcity.upperLimitRevision." + rootExtId) : null;
  }

  @Nullable
  private String getVcsRootExtId() {
    // We don't have vcs root extId on the agent, deduce it from vcs.number parameters
    String revisionParamPrefix = "build.vcs.number.";
    String vcsRootExtId = null;
    Map<String, String> params = myBuild.getSharedConfigParameters();
    for (Map.Entry<String, String> param : params.entrySet()) {
      if (param.getKey().startsWith(revisionParamPrefix) && myRevision.equals(param.getValue())) {
        String extId = param.getKey().substring(revisionParamPrefix.length());
        if (StringUtil.isNotEmpty(extId) && Character.isDigit(extId.charAt(0))) {
          // We have build.vcs.number.<extId> and build.vcs.number.<root number>, ignore the latter (extId cannot start with digit)
          continue;
        }
        if (vcsRootExtId != null) {
          LOG.debug("Build has more than one VCS root with same revision " + myRevision + ": " + vcsRootExtId + " and " +
                    extId + ", cannot deduce VCS root extId");
          return null;
        } else {
          vcsRootExtId = extId;
        }
      }
    }
    return vcsRootExtId;
  }

  @NotNull
  protected AgentCommitLoader getCommitLoader(@NotNull File repo) {
    return AgentCommitLoaderFactory.getCommitLoader(myRoot, repo, myGitFactory, myPluginConfig, myLogger);
  }
}