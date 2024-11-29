package jetbrains.buildServer.buildTriggers.vcs.git.gitProxy.data;

public class FileChange {
  public ChangeType changeType;
  public String newPath;
  public String oldPath;
  public EntryType entryType;

  public FileChange(ChangeType changeType, String newPath, String oldPath, EntryType entryType) {
    this.changeType = changeType;
    this.newPath = newPath;
    this.oldPath = oldPath;
    this.entryType = entryType;
  }

  public String getDisplayPath() {
    return newPath != null ? newPath : oldPath;
  }
}
