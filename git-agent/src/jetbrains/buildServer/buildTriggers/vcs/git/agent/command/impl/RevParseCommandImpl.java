package jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.AgentGitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.RevParseCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.CommandUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class RevParseCommandImpl extends BaseCommandImpl implements RevParseCommand {
    private String ref;
    List<String> myParams = new ArrayList<>();

    public RevParseCommandImpl(@NotNull AgentGitCommandLine myCmd) {
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
    public RevParseCommand setParams(final String... params) {
        myParams.addAll(Arrays.asList(params));
        return this;
    }

    @Override
    public String call() throws VcsException {
        AgentGitCommandLine cmd = getCmd();
        cmd.addParameter("rev-parse");
        cmd.addParameters(myParams);
        if (ref != null)
            cmd.addParameter(ref);

        ExecResult r = CommandUtil.runCommand(cmd);
        return r.getStdout().trim();
    }
}
