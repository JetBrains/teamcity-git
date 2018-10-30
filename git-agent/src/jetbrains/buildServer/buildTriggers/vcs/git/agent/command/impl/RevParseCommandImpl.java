package jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl;

import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.RevParseCommand;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class RevParseCommandImpl extends BaseCommandImpl implements RevParseCommand {
    private String ref;

    public RevParseCommandImpl(@NotNull GitCommandLine myCmd) {
        super(myCmd);
    }

    @Override
    public RevParseCommand setRef(final String ref) {
        this.ref = ref;
        return this;
    }

    @Override
    public String call() throws VcsException {
        GitCommandLine cmd = getCmd();
        cmd.addParameter("rev-parse");
        if (ref != null)
            cmd.addParameter(ref);

        ExecResult r = CommandUtil.runCommand(cmd);
        return r.getStdout().trim();
    }
}
