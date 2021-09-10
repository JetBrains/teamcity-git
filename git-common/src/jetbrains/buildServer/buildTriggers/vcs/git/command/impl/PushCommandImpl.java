package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import java.util.HashSet;
import java.util.Set;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.PushCommand;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class PushCommandImpl extends BaseAuthCommandImpl<PushCommand> implements PushCommand {

  private final Set<String> myRefSpecs = new HashSet<>();
  private String myRemoteUrl;

  public PushCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }

  @NotNull
  public PushCommand setRefspec(@NotNull String refspec) {
    myRefSpecs.add(refspec);
    return this;
  }

  @NotNull
  @Override
  public PushCommand setRemote(@NotNull String remoteUrl) {
    myRemoteUrl = remoteUrl;
    return this;
  }

  @Override
  public void call() throws VcsException {
    final GitCommandLine cmd = getCmd();

    cmd.addParameter("push");
    if (Loggers.VCS.isDebugEnabled()) {
      cmd.addParameter("-v");
    }
    cmd.addParameter(getRemote());
    myRefSpecs.forEach(refSpec -> cmd.addParameter(refSpec));

    runCmd(cmd);
  }

  @NotNull
  private String getRemote() {
    return myRemoteUrl == null ? "origin" : myRemoteUrl;
  }
}
