package jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.RevParseCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.BaseCommandImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.CommandUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class RevParseCommandImpl extends BaseCommandImpl implements RevParseCommand {
    private String ref;
    private boolean myShallowRepository = false;
    private String myVerifyParam;

    public RevParseCommandImpl(@NotNull GitCommandLine myCmd) {
        super(myCmd);
    }

    @NotNull
    @Override
    public RevParseCommand setRef(final String ref) {
        this.ref = ref;
        return this;
    }

    @NotNull
    @Override
    public RevParseCommand setShallow(boolean isShallow) {
        myShallowRepository = isShallow;
        return this;
    }

    @NotNull
    @Override
    public RevParseCommand verify(String param) {
        myVerifyParam = param;
        return this;
    }

    @Override
    public String call() throws VcsException {
        GitCommandLine cmd = getCmd();
        cmd.addParameter("rev-parse");
        if (myShallowRepository) {
            cmd.addParameter("--is-shallow-repository");
        }

        if (myVerifyParam != null) {
            cmd.addParameters("--verify", myVerifyParam);
        }

        if (ref != null)
            cmd.addParameter(ref);

        ExecResult r = CommandUtil.runCommand(cmd);
        return r.getStdout().trim();
    }
}
