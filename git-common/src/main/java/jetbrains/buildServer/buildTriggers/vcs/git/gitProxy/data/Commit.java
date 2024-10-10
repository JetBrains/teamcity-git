package jetbrains.buildServer.buildTriggers.vcs.git.gitProxy.data;

public class Commit {
  public String id;
  public CommitInfo info;

  public Commit(String id, CommitInfo info) {
    this.id = id;
    this.info = info;
  }
}
