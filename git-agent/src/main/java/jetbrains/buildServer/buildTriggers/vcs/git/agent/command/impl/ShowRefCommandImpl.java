

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl;

import com.intellij.openapi.util.text.StringUtil;
import java.util.*;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.ShowRefCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.ShowRefResult;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.BaseCommandImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.CommandUtil;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.RefImpl;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.Ref;
import org.jetbrains.annotations.NotNull;

public class ShowRefCommandImpl extends BaseCommandImpl implements ShowRefCommand {

  private final static String INVALID_REF_PREFIX = "error: ";
  private final static String INVALID_REF_SUFFIX = " does not point to a valid object!";
  private String myPattern;
  private boolean myShowTags;

  public ShowRefCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }

  @NotNull
  public ShowRefCommand setPattern(@NotNull String pattern) {
    myPattern = pattern;
    return this;
  }

  @NotNull
  public ShowRefCommand showTags() {
    myShowTags = true;
    return this;
  }


  @NotNull
  public ShowRefResult call() {
    GitCommandLine cmd = getCmd();
    cmd.addParameter("show-ref");
    if (myPattern != null)
      cmd.addParameters(myPattern);
    if (myShowTags)
      cmd.addParameter("--tags");
    try {
      ExecResult result = CommandUtil.runCommand(cmd);
      return new ShowRefResult(parseValidRefs(result.getStdout()), parseInvalidRefs(result.getStderr()), result.getExitCode());
    } catch (VcsException e) {
      getCmd().getContext().getLogger().warning("show-ref command failed, empty result will be returned: " + e.getMessage());
      return new ShowRefResult();
    }
  }

  @NotNull
  private Map<String, Ref> parseValidRefs(String str) {
    Map<String, Ref> result = new HashMap<String, Ref>();
    for (String line : StringUtil.splitByLines(str)) {
      if (line.length() < 41)
        continue;//a valid line of show-ref output contains 40 symbols of hash + space + branch name
      String commit = line.substring(0, 40);
      String ref = line.substring(41, line.length());
      result.put(ref, new RefImpl(ref, commit));
    }
    return result;
  }

  @NotNull
  private Set<String> parseInvalidRefs(@NotNull String strerr) {
    if (strerr.isEmpty())
      return Collections.emptySet();
    Set<String> result = new HashSet<String>();
    for (String line : StringUtil.splitByLines(strerr)) {
      line = line.trim();
      if (line.startsWith(INVALID_REF_PREFIX) && line.endsWith(INVALID_REF_SUFFIX)) {
        String ref = line.substring(INVALID_REF_PREFIX.length(), line.length() - INVALID_REF_SUFFIX.length());
        result.add(ref);
      }
    }
    return result;
  }
}