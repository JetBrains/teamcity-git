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
    private final List<FileLine> myModifiedFiles;

    public StatusResult(@Nullable String branch, @NotNull List<FileLine> modifiedFiles) {
      myBranch = branch;
      myModifiedFiles = new ArrayList<>(modifiedFiles);
    }

    @Nullable
    public String getBranch() {
      return myBranch;
    }

    public List<FileLine> getModifiedFiles() {
      return Collections.unmodifiableList(myModifiedFiles);
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
      //see man git status for --porcelain v1 version for details
      return myStatusCode;
    }

    public String getPath() {
      return myPath;
    }
  }
}
