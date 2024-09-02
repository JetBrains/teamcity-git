

package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import com.intellij.openapi.diagnostic.Logger;
import java.util.*;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.LsRemoteCommand;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.Ref;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.buildTriggers.vcs.git.command.impl.CommandUtil.splitByLines;

public class LsRemoteCommandImpl extends BaseAuthCommandImpl<LsRemoteCommand> implements LsRemoteCommand {

  private boolean myPeelRefs = false;
  private boolean myTags = false;

  private final List<String> myLsRemoteBranches = new ArrayList<>();
  private static final Logger LOG = Logger.getInstance(LsRemoteCommandImpl.class);

  public LsRemoteCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }

  @NotNull
  public LsRemoteCommand peelRefs() {
    myPeelRefs = true;
    return this;
  }

  @NotNull
  @Override
  public LsRemoteCommand setTags() {
    myTags = true;
    return this;
  }

  @NotNull
  @Override
  public LsRemoteCommand setBranches(String... lsRemoteBranches) {
    myLsRemoteBranches.addAll(Arrays.asList(lsRemoteBranches));
    return this;
  }

  @NotNull
  public List<Ref> call() throws VcsException {
    GitCommandLine cmd = getCmd();
    cmd.addParameter("ls-remote");
    if (myTags) {
      cmd.addParameter("--tags");
    }
    cmd.addParameter("origin");

    if (!myLsRemoteBranches.isEmpty()) {
      cmd.addParameters(myLsRemoteBranches);
    }

    ExecResult res = runCmd(cmd.stdErrLogLevel("debug"));
    String repoUrl = TeamCityProperties.getPropertyOrNull("teamcity.git.native.lsRemote.logCommandOutputForUrl");
    if (repoUrl != null && getRepoUrl().toString().contains(repoUrl)) {
      LOG.debug("[ls-remote " + getRepoUrl().toString() + "] stdout: " + res.getStdout());
      LOG.debug("[ls-remote " + getRepoUrl().toString() + "] stderr: " + res.getStderr());
      LOG.debug("[ls-remote " + getRepoUrl().toString() + "] exit code: " + res.getExitCode());
    }

    return parse(res.getStdout());
  }

  private List<Ref> parse(@NotNull final String str) throws VcsException {
    final Map<String, Ref> refs = new HashMap<>();
    for (String line : splitByLines(str)) {

      final String objectId = line.substring(0, 40);
      String name = line.substring(40).trim();

      if (myPeelRefs && name.endsWith("^{}")) {
        name = name.substring(0, name.length() - 3);
        final Ref prior = refs.get(name);
        if (prior == null) throw new VcsException(String.format("Advertisement of %s^'{}' came before %s", name, name));
      }

      refs.put(name, new RefImpl(name, objectId));
    }
    return new ArrayList<>(refs.values());
  }
}