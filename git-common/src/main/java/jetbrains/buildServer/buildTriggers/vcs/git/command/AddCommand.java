package jetbrains.buildServer.buildTriggers.vcs.git.command;

import java.util.List;
import jetbrains.buildServer.vcs.VcsException;

public interface AddCommand extends BaseCommand {

  AddCommand setPaths(List<String> paths);

  AddCommand setAddAll(boolean addAll);

  void call() throws VcsException;

}
