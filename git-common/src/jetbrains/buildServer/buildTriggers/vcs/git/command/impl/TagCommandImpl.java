package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.TagCommand;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class TagCommandImpl extends BaseCommandImpl implements TagCommand {
  private String myName;
  private String myCommit;
  private boolean myDelete;
  private boolean myForce;
  private boolean myAnnotate;
  private String myTaggerName;
  private String myTaggerEmail;
  private String myMessage;

  public TagCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }

  @NotNull
  @Override
  public TagCommand setName(@NotNull String name) {
    myName = name;
    return this;
  }

  @NotNull
  @Override
  public TagCommand setCommit(@NotNull String sha) {
    myCommit = sha;
    return this;
  }

  @NotNull
  @Override
  public TagCommand force(boolean force) {
    myForce = force;
    return this;
  }

  @NotNull
  @Override
  public TagCommand delete(boolean delete) {
    myDelete = delete;
    return this;
  }

  @NotNull
  @Override
  public TagCommand annotate(@NotNull String name, @NotNull String email, @NotNull String message) {
    myAnnotate = true;
    myTaggerName = name;
    myTaggerEmail= email;
    myMessage = message;
    return this;
  }

  @Override
  public void call() throws VcsException {
    GitCommandLine cmd = getCmd().stdErrExpected(false);
    cmd.addParameter("tag");
    if (myDelete) {
      cmd.addParameter("-d");
    }

    if (myAnnotate) {
      cmd.addParameters("-a", "-m", myMessage);
      if (StringUtil.isNotEmpty(myTaggerName)) {
        cmd.addEnvParam("GIT_COMMITTER_NAME", myTaggerName);
      }
      if (StringUtil.isNotEmpty(myTaggerEmail)) {
        cmd.addEnvParam("GIT_COMMITTER_EMAIL", myTaggerEmail);
      }
    }

    if (myForce) {
      cmd.addParameter("-f");
    }

    if (myName != null) {
      cmd.addParameter(myName);
    }
    if (myCommit != null) {
      cmd.addParameter(myCommit);
    }
    CommandUtil.runCommand(cmd.stdErrExpected(false), 60);
  }
}
