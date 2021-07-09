package jetbrains.buildServer.buildTriggers.vcs.git.agent.command;

import jetbrains.buildServer.buildTriggers.vcs.git.command.BaseCommand;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface LsTreeCommand extends BaseCommand {

    @NotNull
    LsTreeCommand setRevision(String revision);

    @NotNull
    LsTreeCommand setPath(String path);

    @Nullable
    LsTreeResult call() throws VcsException;
}
