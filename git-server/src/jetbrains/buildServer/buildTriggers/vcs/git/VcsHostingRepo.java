

package jetbrains.buildServer.buildTriggers.vcs.git;

import org.jetbrains.annotations.NotNull;

public class VcsHostingRepo {
  private final String myOwner;
  private final String myRepoName;
  private final String myRepositoryUrl;

  public VcsHostingRepo(@NotNull String repositoryUrl, @NotNull String owner, @NotNull String repoName) {
    myOwner = owner;
    myRepoName = repoName;
    myRepositoryUrl = repositoryUrl;
  }

  public String repositoryUrl() {
    return myRepositoryUrl;
  }

  @NotNull
  public String owner() {
    return myOwner;
  }
  @NotNull
  public String repoName() {
    return myRepoName;
  }
}