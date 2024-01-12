

package jetbrains.buildServer.buildTriggers.vcs.git.patch;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.diagnostic.Logger;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.LineAwareByteArrayOutputStream;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.buildTriggers.vcs.git.process.GitProcessExecutor;
import jetbrains.buildServer.buildTriggers.vcs.git.process.RepositoryXmxStorage;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.ssh.TeamCitySshKey;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsUtil;
import jetbrains.buildServer.vcs.patches.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public final class GitPatchBuilderDispatcher {

  private final static Logger LOG = Logger.getInstance(GitPatchBuilderDispatcher.class.getName());

  private final ServerPluginConfig myConfig;
  private final VcsRootSshKeyManager mySshKeyManager;
  private final OperationContext myContext;
  private final GitVcsRoot myGitRoot;
  private final PatchBuilder myBuilder;
  private final String myFromRevision;
  private final String myToRevision;
  private final CheckoutRules myRules;
  private final String myTrustedCertificatesDir;
  private final boolean myUseSeparateProcessForPatch;
  private final SshSessionMetaFactory mySshMetaFactory;

  public GitPatchBuilderDispatcher(@NotNull ServerPluginConfig config,
                                   @NotNull VcsRootSshKeyManager sshKeyManager,
                                   @NotNull OperationContext context,
                                   @NotNull PatchBuilder builder,
                                   @Nullable String fromRevision,
                                   @NotNull String toRevision,
                                   @NotNull CheckoutRules rules,
                                   @Nullable String trustedCertificatesDir,
                                   boolean useSeparateProcessForPatch,
                                   @NotNull SshSessionMetaFactory sshMetaFactory) throws VcsException {
    myConfig = config;
    mySshKeyManager = sshKeyManager;
    myContext = context;
    myGitRoot = context.getGitRoot();
    myBuilder = builder;
    myFromRevision = fromRevision;
    myToRevision = toRevision;
    myRules = rules;
    myTrustedCertificatesDir = trustedCertificatesDir;
    myUseSeparateProcessForPatch = useSeparateProcessForPatch;
    mySshMetaFactory = sshMetaFactory;
  }

  public void buildPatch() throws Exception {
    if (myUseSeparateProcessForPatch) {
      LOG.info("Build patch in separate process, root: " + LogUtil.describe(myGitRoot) +
               ", fromRevision: " + myFromRevision +
               ", toRevision: " + myToRevision);
      buildPatchInSeparateProcess();
    } else {
      LOG.info("Build patch in server process, root: " + LogUtil.describe(myGitRoot) +
               ", fromRevision: " + myFromRevision +
               ", toRevision: " + myToRevision);
      buildPatchInSameProcess();
    }
  }

  private void buildPatchInSeparateProcess() throws Exception {
    final String rootStr = LogUtil.describe(myGitRoot);
    final ProcessXmxProvider xmxProvider = new ProcessXmxProvider(new RepositoryXmxStorage(myContext.getRepository(), "patch"), myConfig, "patch", "(root: " + rootStr + ")");
    Integer xmx = xmxProvider.getNextXmx();
    while (xmx != null) {
      final GeneralCommandLine patchCmd = createPatchCommandLine(xmx);
      final File patchFile = FileUtil.createTempFile("git", "patch");
      final File internalProperties = getPatchPropertiesFile();
      try {
        final ByteArrayOutputStream stdout = new LineAwareByteArrayOutputStream(Charset.forName("UTF-8"), new NoOpLineListener(), false);
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        final GitProcessExecutor.GitExecResult gitResult = new GitProcessExecutor(patchCmd).runProcess(
          getInput(patchFile, internalProperties),
          myConfig.getPatchProcessIdleTimeoutSeconds(),
          stdout, stderr,
          new GitProcessExecutor.ProcessExecutorAdapter());

        VcsException patchError = CommandLineUtil.getCommandLineError("build patch", gitResult.getExecResult());
        if (patchError != null) {
          if (gitResult.isOutOfMemoryError()) {
            final Integer nextXmx = xmxProvider.getNextXmx();
            if (nextXmx != null) {
              xmx = nextXmx;
              FileUtil.delete(patchFile);
              continue;
            }
            throw new VcsException("There is not enough memory for git patch (last attempted -Xmx" + xmx + "M). Please contact your system administrator", patchError);

          } else if (gitResult.isTimeout()) {
            throw new VcsException("git patch for root " + rootStr + " was idle for more than " + myConfig.getFetchTimeout() + " second(s), try increasing the timeout using the " + PluginConfigImpl.TEAMCITY_GIT_IDLE_TIMEOUT_SECONDS + " property", patchError);
          }
          throw patchError;
        }

        new LowLevelPatcher(new FileInputStream(patchFile)).applyPatch(new NoExitLowLevelPatchTranslator(((PatchBuilderEx)myBuilder).getLowLevelBuilder()));
        break;
      } finally {
        FileUtil.delete(patchFile);
        FileUtil.delete(internalProperties);
      }
    }
  }

  @NotNull
  private File getPatchPropertiesFile() throws IOException {
    File internalProperties = FileUtil.createTempFile("gitPatch", "props");
    GitServerUtil.writeAsProperties(internalProperties, getPatchProcessProperties());
    return internalProperties;
  }

  private byte[] getInput(@NotNull File patchFile, @NotNull File internalProperties) throws IOException {
    Map<String, String> props = new HashMap<String, String>();
    props.put(Constants.FETCHER_INTERNAL_PROPERTIES_FILE, internalProperties.getCanonicalPath());
    if (myFromRevision != null)
      props.put(Constants.PATCHER_FROM_REVISION, myFromRevision);
    props.put(Constants.PATCHER_TO_REVISION, myToRevision);
    props.put(Constants.PATCHER_CHECKOUT_RULES, myRules.getAsString());
    props.put(Constants.PATCHER_CACHES_DIR, myConfig.getCachesDir().getCanonicalPath());
    props.put(Constants.PATCHER_PATCH_FILE, patchFile.getCanonicalPath());
    props.put(Constants.PATCHER_UPLOADED_KEY, getUploadedKey());
    props.put(Constants.VCS_DEBUG_ENABLED, String.valueOf(Loggers.VCS.isDebugEnabled()));
    if (myTrustedCertificatesDir != null) {
      props.put(Constants.GIT_TRUST_STORE_PROVIDER, myTrustedCertificatesDir);
    }
    props.putAll(myGitRoot.getProperties());
    return VcsUtil.propertiesToStringSecure(props).getBytes("UTF-8");
  }


  @NotNull
  private Map<String, String> getPatchProcessProperties() {
    Map<String, String> result = new HashMap<String, String>();
    result.putAll(myConfig.getFetcherProperties());
    result.put("teamcity.git.fetch.separate.process", "false");
    result.put(PluginConfigImpl.MAP_FULL_PATH_PERSISTENT_CACHES, "false");
    return result;
  }


  private String getUploadedKey() {
    if (myGitRoot.getAuthSettings().getAuthMethod() != AuthenticationMethod.TEAMCITY_SSH_KEY)
      return null;
    TeamCitySshKey key = mySshKeyManager.getKey(myGitRoot.getOriginalRoot());
    if (key == null)
      return null;
    return new String(key.getPrivateKey());
  }

  private void buildPatchInSameProcess() throws Exception {
    new GitPatchBuilder(myContext, myBuilder, myFromRevision, myToRevision, myRules, myConfig.verboseTreeWalkLog(), mySshMetaFactory)
      .buildPatch();
  }

  private GeneralCommandLine createPatchCommandLine(int xmx) throws VcsException {
    GeneralCommandLine cmd = new GeneralCommandLine();
    cmd.setWorkingDirectory(myContext.getRepository(myGitRoot).getDirectory());
    cmd.setExePath(myConfig.getFetchProcessJavaPath());
    cmd.addParameters(myConfig.getOptionsForSeparateProcess());
    cmd.addParameters("-Xmx" + xmx + "M",
                     "-cp", myConfig.getPatchClasspath(),
                     myConfig.getPatchBuilderClassName(),
                     myGitRoot.getRepositoryFetchURL().toString());
    cmd.setPassParentEnvs(myConfig.passEnvToChildProcess());
    cmd.setEnvParams(Collections.singletonMap("JDK_JAVA_OPTIONS", null)); // TW-64719
    return cmd;
  }

  private static final class NoOpLineListener implements LineAwareByteArrayOutputStream.LineListener {
    public void newLineDetected(@NotNull final String line) {
    }
  }


  private static final class NoExitLowLevelPatchTranslator extends LowLevelPatchTranslator {
    public NoExitLowLevelPatchTranslator(@NotNull final LowLevelPatchBuilder builder) {
      super(builder);
    }
    @Override
    public void exit(@NotNull final String message) throws IOException {
    }
  }
}