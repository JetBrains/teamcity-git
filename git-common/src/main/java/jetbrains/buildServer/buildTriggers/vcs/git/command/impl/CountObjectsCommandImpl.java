package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.buildTriggers.vcs.git.command.CountObjectsCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class CountObjectsCommandImpl extends BaseCommandImpl implements CountObjectsCommand {
  public CountObjectsCommandImpl(@NotNull GitCommandLine cmd) {
    super(cmd);
  }

  @Override
  public Map<String, Long> call() throws VcsException {
    GitCommandLine cmd = getCmd();
    cmd.addParameter("count-objects");
    cmd.addParameter("-v");


    ExecResult result = CommandUtil.runCommand(cmd);
    String stdout = result.getStdout().trim();
    List<String> countObjectsRecords = Arrays.asList(stdout.split("\n"));

    return countObjectsRecords.stream().map(it -> it.split(":")).filter(r -> r.length == 2).collect(Collectors.toMap(r -> r[0].trim(), r->Long.parseLong(r[1].trim())));
  }

  public static int calculateRepositorySizeGiB(@NotNull Map<String, Long> countObjects, @NotNull String repositopryPath) throws VcsException {
    Long packsSizeKiB = countObjects.get("size-pack");
    Long looseObjectsSizeKiB = countObjects.get("size");

    if (packsSizeKiB == null || looseObjectsSizeKiB == null) {
      throw new VcsException("Failed to count objects in the repository " + repositopryPath);
    }

    return (int)((packsSizeKiB + looseObjectsSizeKiB) / 1024.0 / 1024.0); // KiB to GiB
  }
}
