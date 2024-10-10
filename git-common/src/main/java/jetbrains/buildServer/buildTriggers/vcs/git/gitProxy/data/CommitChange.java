package jetbrains.buildServer.buildTriggers.vcs.git.gitProxy.data;

import java.util.List;
import org.jetbrains.annotations.Nullable;

public class CommitChange {
    public String revision;
    @Nullable
    public String compareTo;
    public Boolean limitReached;
    public List<FileChange> changes;

    public CommitChange(String revision, @Nullable String compareTo, Boolean limitReached, List<FileChange> changes) {
        this.revision = revision;
        this.compareTo = compareTo;
        this.limitReached = limitReached;
        this.changes = changes;
    }
}
