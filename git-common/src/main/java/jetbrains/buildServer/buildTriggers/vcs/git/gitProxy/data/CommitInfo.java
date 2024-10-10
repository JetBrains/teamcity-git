package jetbrains.buildServer.buildTriggers.vcs.git.gitProxy.data;

import java.util.List;

public class CommitInfo {
  public String id;
  public String treeId;
  public String fullMessage;
  public Person author;
  public int authorTime;
  public Person committer;
  public int commitTime;
  public List<String> parents;
  public List<String> refs;
  
  public CommitInfo(String id, String treeId, String fullMessage, Person author, int authorTime, Person committer, int commitTime, List<String> parents, List<String> refs) {
    this.id = id;
    this.treeId = treeId;
    this.fullMessage = fullMessage;
    this.author = author;
    this.authorTime = authorTime;
    this.committer = committer;
    this.commitTime = commitTime;
    this.parents = parents;
    this.refs = refs;
  }
}
