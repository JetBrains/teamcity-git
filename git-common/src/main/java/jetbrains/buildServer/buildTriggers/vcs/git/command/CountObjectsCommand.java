package jetbrains.buildServer.buildTriggers.vcs.git.command;

import java.util.Map;
import jetbrains.buildServer.vcs.VcsException;

public interface CountObjectsCommand extends BaseCommand {
  Map<String, Long> call() throws VcsException;
}
