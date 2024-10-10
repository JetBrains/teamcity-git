package jetbrains.buildServer.buildTriggers.vcs.git.gitProxy.data;

public class FileChange {
  public ChangeType changeType;
  public String newPath;
  public String oldPath;
  public String newBlob;
  public String oldBlob;
  public EntryType entryType;

  public FileChange(ChangeType changeType, String newPath, String oldPath, String newBlob, String oldBlob, EntryType entryType) {
    this.changeType = changeType;
    this.newPath = newPath;
    this.oldPath = oldPath;
    this.newBlob = newBlob;
    this.oldBlob = oldBlob;
    this.entryType = entryType;
  }
}
