package jetbrains.buildServer.buildTriggers.vcs.git.agent.command;

import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface RevParseCommand extends BaseCommand {
    @NotNull
    RevParseCommand setRef(String ref);

    @NotNull
    RevParseCommand setParams(String... params);

    @Nullable
    String call() throws VcsException;
}
