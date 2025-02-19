package jetbrains.buildServer.buildTriggers.vcs.git.submodules;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public class MissingSubmoduleCommitInfo {

  private final HashSet<MissingCommit> myMissingSubmoduleCommits;
  private final List<SubmodulePathPair> myCurrentSubmodulePath; // pair (submodule revision, relative path)

  public MissingSubmoduleCommitInfo() {
    myMissingSubmoduleCommits = new HashSet<>();
    myCurrentSubmodulePath = new ArrayList<>();
  }

  private MissingSubmoduleCommitInfo(@NotNull HashSet<MissingCommit> missingSubmoduleCommits, @NotNull List<SubmodulePathPair> currentSubmodulePath) {
    myMissingSubmoduleCommits = missingSubmoduleCommits;
    myCurrentSubmodulePath = currentSubmodulePath;
  }

  public void addMissingSubmoduleCommit(@NotNull String revisionId, @NotNull String relativePath) {
    List<SubmodulePathPair> fullPath = new ArrayList<>(myCurrentSubmodulePath);
    fullPath.add(new SubmodulePathPair(relativePath, revisionId));
    myMissingSubmoduleCommits.add(new MissingCommit(fullPath));
  }

  public MissingSubmoduleCommitInfo createInfoForSubmodule(@NotNull String submoduleRevision, @NotNull String relativeSubmodulePath) {
    List<SubmodulePathPair> newPath = new ArrayList<>(myCurrentSubmodulePath);
    newPath.add(new SubmodulePathPair(relativeSubmodulePath, submoduleRevision));
    return new MissingSubmoduleCommitInfo(myMissingSubmoduleCommits, newPath);
  }

  public List<MissingCommit> getMissingSubmoduleCommits() {
    return new ArrayList<>(myMissingSubmoduleCommits);
  }

  public static class MissingCommit {

    @NotNull
    private final List<SubmodulePathPair> myPath;

    private MissingCommit(@NotNull List<SubmodulePathPair> submodulePath) {
      myPath = submodulePath;
    }

    @NotNull
    public List<SubmodulePathPair> getPath() {
      return myPath;
    }

    @Override
    public int hashCode() {
      return myPath.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof MissingCommit && myPath.equals(((MissingCommit)obj).myPath);
    }
  }

  public static class SubmodulePathPair {
    @NotNull
    private final String myRelativePath;
    @NotNull
    private final String mySubmoduleRevision;

    private SubmodulePathPair(@NotNull String relativePath, @NotNull String submoduleRevision) {
      myRelativePath = relativePath;
      mySubmoduleRevision = submoduleRevision;
    }

    @NotNull
    public String getRelativePath() {
      return myRelativePath;
    }

    @NotNull
    public String getSubmoduleRevision() {
      return mySubmoduleRevision;
    }

    @Override
    public int hashCode() {
      return Objects.hash(myRelativePath, mySubmoduleRevision);
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof SubmodulePathPair && myRelativePath.equals(((SubmodulePathPair)other).myRelativePath) && mySubmoduleRevision.equals(((SubmodulePathPair)other).mySubmoduleRevision);
    }
  }
}
