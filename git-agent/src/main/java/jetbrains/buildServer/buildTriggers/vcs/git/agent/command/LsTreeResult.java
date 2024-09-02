package jetbrains.buildServer.buildTriggers.vcs.git.agent.command;

import org.jetbrains.annotations.NotNull;

public class LsTreeResult {

    private final String type;
    private final String mode;
    private final String object;
    private final String file;

    public LsTreeResult(@NotNull String type, @NotNull String mode, @NotNull String object, @NotNull String file) {

        this.type = type;
        this.mode = mode;
        this.object = object;
        this.file = file;
    }

    public String getType() {
        return type;
    }

    public String getMode() {
        return mode;
    }

    public String getObject() {
        return object;
    }

    public String getFile() {
        return file;
    }
}
