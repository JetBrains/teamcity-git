

package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.buildTriggers.vcs.git.github.GitHubListFilesSupport;
import jetbrains.buildServer.vcs.ListDirectChildrenPolicy;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsFileData;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class ListFilesDispatcher implements ListDirectChildrenPolicy {

  private final GitVcsSupport myVcs;
  private final CommitLoader myCommitLoader;
  private final ServerPluginConfig myConfig;
  private ListDirectChildrenPolicy myImplementation;

  public ListFilesDispatcher(@NotNull GitVcsSupport vcs,
                             @NotNull CommitLoader commitLoader,
                             @NotNull ServerPluginConfig config) {
    myVcs = vcs;
    myCommitLoader = commitLoader;
    myConfig = config;
  }

  @NotNull
  public Collection<VcsFileData> listFiles(@NotNull VcsRoot root, @NotNull String directoryPath) throws VcsException {
    synchronized (this) {
      if (myImplementation == null)
        myImplementation = getPolicy(root);
    }
    return myImplementation.listFiles(root, directoryPath);
  }

  private ListDirectChildrenPolicy getPolicy(@NotNull VcsRoot root) {
    OperationContext ctx = myVcs.createContext(root, "list files dispatch");
    GitListFilesSupport genericListFiles = new GitListFilesSupport(myVcs, myCommitLoader, myConfig);
    try {
      if (GitServerUtil.isCloned(ctx.getRepository()))
        return genericListFiles;
      VcsHostingRepo ghRepo = WellKnownHostingsUtil.getGitHubRepo(ctx.getGitRoot().getRepositoryFetchURL().get());
      if (ghRepo == null)
        return genericListFiles;
      return new GitHubListFilesSupport(myVcs, genericListFiles, ghRepo.owner(), ghRepo.repoName());
    } catch (Exception e) {
      //LOG
      return genericListFiles;
    } finally {
      ctx.close();
    }
  }
}