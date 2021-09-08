package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import java.io.File;
import jetbrains.buildServer.buildTriggers.vcs.git.FetchCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVersion;
import jetbrains.buildServer.buildTriggers.vcs.git.ServerPluginConfig;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitExec;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitRepoOperations;
import jetbrains.buildServer.buildTriggers.vcs.git.command.NativeGitFetchCommand;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class GitRepoOperationsImpl implements GitRepoOperations{
  private final VcsRootSshKeyManager mySshKeyManager;
  private final ServerPluginConfig myConfig;
  private final FetchCommand myJGitFetchCommand;
  private GitExec myGitExec;

  public GitRepoOperationsImpl(@NotNull ServerPluginConfig config, @NotNull VcsRootSshKeyManager sshKeyManager, @NotNull FetchCommand jGitFetchCommand) {
    myConfig = config;
    mySshKeyManager = sshKeyManager;
    myJGitFetchCommand = jGitFetchCommand;
  }

  @NotNull
  @Override
  public FetchCommand fetchCommand() {
    if (TeamCityProperties.getBoolean("teamcity.git.nativeOperationsEnabled")) {
      return new NativeGitFetchCommand(myConfig, this::detectGit, mySshKeyManager);
    }
    return myJGitFetchCommand;
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
}
