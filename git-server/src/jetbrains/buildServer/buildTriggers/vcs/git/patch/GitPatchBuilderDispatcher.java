/*
 * Copyright 2000-2018 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.buildTriggers.vcs.git.patch;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.LineAwareByteArrayOutputStream;
import jetbrains.buildServer.SimpleCommandLineProcessRunner;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;


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

  public GitPatchBuilderDispatcher(@NotNull ServerPluginConfig config,
                                   @NotNull VcsRootSshKeyManager sshKeyManager,
                                   @NotNull OperationContext context,
                                   @NotNull PatchBuilder builder,
                                   @Nullable String fromRevision,
                                   @NotNull String toRevision,
                                   @NotNull CheckoutRules rules,
                                   @NotNull String trustedCertificatesDir) throws VcsException {
    myConfig = config;
    mySshKeyManager = sshKeyManager;
    myContext = context;
    myGitRoot = context.getGitRoot();
    myBuilder = builder;
    myFromRevision = fromRevision;
    myToRevision = toRevision;
    myRules = rules;
    myTrustedCertificatesDir = trustedCertificatesDir;
  }

  public void buildPatch() throws Exception {
    if (myConfig.isSeparateProcessForPatch()) {
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
    GeneralCommandLine patchCmd = createPatchCommandLine();
    File patchFile = FileUtil.createTempFile("git", "patch");
    File internalProperties = getPatchPropertiesFile();
    try {
      byte[] patchProcessInput = getInput(patchFile, internalProperties);
      LineAwareByteArrayOutputStream.LineListener listener = new NoOpLineListener();
      ByteArrayOutputStream stdout = new LineAwareByteArrayOutputStream(Charset.forName("UTF-8"), listener, false);
      ByteArrayOutputStream stderr = new ByteArrayOutputStream();
      ExecResult result = SimpleCommandLineProcessRunner.runCommandSecure(patchCmd, patchCmd.getCommandLineString(), patchProcessInput,
                                                                          new PatchProcessEventsHandler(), stdout, stderr);
      if (GitServerUtil.isCannotCreateJvmError(result)) {
        String configuredXmx = myConfig.getExplicitFetchProcessMaxMemory();
        Long xmxBytes = GitServerUtil.convertMemorySizeToBytes(configuredXmx);
        Long physicalMemory = GitServerUtil.getFreePhysicalMemorySize();
        if (xmxBytes != null && physicalMemory != null && xmxBytes > physicalMemory)
          LOG.warn("Not enough memory for git patch, teamcity.git.fetch.process.max.memory=" + configuredXmx);
      }
      VcsException patchError = CommandLineUtil.getCommandLineError("build patch", result);
      if (patchError != null)
        throw patchError;
      new LowLevelPatcher(new FileInputStream(patchFile)).applyPatch(new NoExitLowLevelPatchTranslator(((PatchBuilderEx)myBuilder).getLowLevelBuilder()));
    } finally {
      FileUtil.delete(patchFile);
      FileUtil.delete(internalProperties);
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
    props.put(Constants.GIT_TRUST_STORE_PROVIDER, myTrustedCertificatesDir);
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
    new GitPatchBuilder(myContext, myBuilder, myFromRevision, myToRevision, myRules, myConfig.verboseTreeWalkLog())
      .buildPatch();
  }

  private GeneralCommandLine createPatchCommandLine() throws VcsException {
    GeneralCommandLine cmd = new GeneralCommandLine();
    cmd.setWorkingDirectory(myContext.getRepository(myGitRoot).getDirectory());
    cmd.setExePath(myConfig.getFetchProcessJavaPath());
    cmd.addParameters(myConfig.getOptionsForSeparateProcess());
    cmd.addParameters("-Xmx" + myConfig.getFetchProcessMaxMemory(),
                     "-cp", myConfig.getPatchClasspath(),
                     myConfig.getPatchBuilderClassName(),
                     myGitRoot.getRepositoryFetchURL().toString());
    cmd.setPassParentEnvs(myConfig.passEnvToChildProcess());
    return cmd;
  }


  private final class PatchProcessEventsHandler extends SimpleCommandLineProcessRunner.RunCommandEventsAdapter {
    @Nullable
    @Override
    public Integer getOutputIdleSecondsTimeout() {
      return myConfig.getPatchProcessIdleTimeoutSeconds();
    }
  }


  private final class NoOpLineListener implements LineAwareByteArrayOutputStream.LineListener {
    public void newLineDetected(@NotNull final String line) {
    }
  }


  private final class NoExitLowLevelPatchTranslator extends LowLevelPatchTranslator {
    public NoExitLowLevelPatchTranslator(@NotNull final LowLevelPatchBuilder builder) {
      super(builder);
    }
    @Override
    public void exit(@NotNull final String message) throws IOException {
    }
  }
}
