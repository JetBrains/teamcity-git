

package jetbrains.buildServer.buildTriggers.vcs.git.agent;


import java.io.File;
import java.io.IOException;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.SmartDirectoryCleaner;
import jetbrains.buildServer.agent.oauth.AgentTokenStorage;
import jetbrains.buildServer.buildTriggers.vcs.git.MirrorManager;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.SubmoduleUpdateCommand;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.jetbrains.annotations.NotNull;

public class ShallowUpdater extends UpdaterImpl {

  public ShallowUpdater(@NotNull final FS fs,
                        @NotNull final AgentPluginConfig pluginConfig,
                        @NotNull final MirrorManager mirrorManager,
                        @NotNull final SmartDirectoryCleaner directoryCleaner,
                        @NotNull final GitFactory gitFactory,
                        @NotNull final AgentRunningBuild build,
                        @NotNull final VcsRoot root,
                        @NotNull final String version,
                        @NotNull final File targetDir,
                        @NotNull final CheckoutRules rules,
                        @NotNull final CheckoutMode checkoutMode,
                        final SubmoduleManager submoduleManager,
                        @NotNull AgentTokenStorage tokenStorage) throws VcsException {
    super(fs, pluginConfig, mirrorManager, directoryCleaner, gitFactory, build, root, version, targetDir, rules, checkoutMode, submoduleManager, tokenStorage);
  }

  @Override
  protected void ensureCommitLoaded(final boolean fetchRequired) throws VcsException {
    throwNoCommitFoundIfNecessary(getCommitLoader(myTargetDirectory).loadCommitPreferShallow(myRevision, myFullBranchName));
  }

  @Override
  protected void updateSubmodules(@NotNull final File repositoryDir) throws VcsException, ConfigInvalidException, IOException {
    AgentGitFacade git = myGitFactory.create(repositoryDir);
    SubmoduleUpdateCommand submoduleUpdate = git.submoduleUpdate()
                                                .setAuthSettings(myRoot.getAuthSettings())
                                                .setUseNativeSsh(myPluginConfig.isUseNativeSSH())
                                                .setTimeout(myPluginConfig.getSubmoduleUpdateTimeoutSeconds())
                                                .setForce(isForceUpdateSupported())
                                                .setDepth(myPluginConfig.getSubmodulesShallowDepth());
    submoduleUpdate.addConfig("protocol.file.allow", "always");
    submoduleUpdate.call();
  }
}