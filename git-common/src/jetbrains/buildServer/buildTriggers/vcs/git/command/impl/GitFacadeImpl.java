package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import com.intellij.openapi.util.SystemInfo;
import java.io.File;
import jetbrains.buildServer.buildTriggers.vcs.git.command.*;
import jetbrains.buildServer.buildTriggers.vcs.git.command.credentials.ScriptGen;
import jetbrains.buildServer.ssh.VcsRootSshKeyManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GitFacadeImpl implements GitFacade {
  private final ScriptGen myScriptGen;
  private final File myRepositoryDir;
  private final Context myCtx;
  private VcsRootSshKeyManager mySshKeyManager;

  public GitFacadeImpl(@NotNull File repositoryDir,
                       @NotNull Context ctx) {
    myCtx = ctx;
    myScriptGen = makeScriptGen();
    myRepositoryDir = repositoryDir;
  }

  public void setSshKeyManager(@Nullable VcsRootSshKeyManager sshKeyManager) {
    mySshKeyManager = sshKeyManager;
  }

  @NotNull
  private ScriptGen makeScriptGen() {
    final File tempDir = myCtx.getTempDir();
    return SystemInfo.isUnix ? new UnixScriptGen(tempDir, new EscapeEchoArgumentUnix()) : new WinScriptGen(tempDir, new EscapeEchoArgumentWin());
  }

  @NotNull
  public VersionCommand version() {
    return new VersionCommandImpl(createCommandLine());
  }

  @NotNull
  public FetchCommand fetch() {
    return new FetchCommandImpl(createCommandLine());
  }

  @NotNull
  public LsRemoteCommand lsRemote() {
    return new LsRemoteCommandImpl(createCommandLine());
  }

  @NotNull
  @Override
  public RemoteCommand remote() {
    return new RemoteCommandImpl(createCommandLine());
  }

  @NotNull
  protected GitCommandLine createCommandLine() {
    final GitCommandLine cmd = makeCommandLine();
    cmd.setExePath(myCtx.getGitExec().getPath());
    cmd.setWorkingDirectory(myRepositoryDir);
    cmd.setSshKeyManager(mySshKeyManager);
    for (String config : myCtx.getCustomConfig()) {
      cmd.addParameters("-c", config);
    }
    return cmd;
  }

  @NotNull
  protected GitCommandLine makeCommandLine() {
    return new GitCommandLine(myCtx, myScriptGen);
  }

  @NotNull
  public ScriptGen getScriptGen() {
    return myScriptGen;
  }


  @NotNull
  protected Context getCtx() {
    return myCtx;
  }
}
