package jetbrains.buildServer.buildTriggers.vcs.git.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface StatusCommand extends BaseCommand {

  StatusResult call() throws VcsException;

  class StatusResult {

    @Nullable
    private final String myBranch;
    private final List<FileLine> myModifiedLines;

    public StatusResult(@Nullable String branch, @NotNull List<FileLine> modifiedLines) {
      myBranch = branch;
      myModifiedLines = new ArrayList<>(modifiedLines);
    }

    @Nullable
    public String getBranch() {
      return myBranch;
    }

    public List<FileLine> getModifiedLines() {
      return Collections.unmodifiableList(myModifiedLines);
    }
  }

  class FileLine {
    private final String myStatusCode;
    private final String myPath;

    public FileLine(String statusCode, String path) {
      myStatusCode = statusCode;
      myPath = path;
    }

    public String getStatusCode() {
      return myStatusCode;
    }

    public String getPath() {
      return myPath;
    }
  }
}
