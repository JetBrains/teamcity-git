package jetbrains.buildServer.buildTriggers.vcs.git;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import jetbrains.buildServer.buildTriggers.vcs.git.command.InitCommandResult;
import jetbrains.buildServer.serverSide.impl.configsInGit.RepositoryInitializingExtension;
import jetbrains.buildServer.vcs.CommitSettings;
import jetbrains.buildServer.vcs.VcsException;

public class GitRepositoryInitializingExtension implements RepositoryInitializingExtension {

  private final GitVcsSupport myVcs;
  private final GitRepoOperations myGitRepoOperations;

  public GitRepositoryInitializingExtension(GitVcsSupport vcs, GitRepoOperations gitRepoOperations) {
    myVcs = vcs;
    myGitRepoOperations = gitRepoOperations;
  }

  @Override
  public Map<String, String> initialize(String remotePath, CommitSettings commitSettings, List<String> ignoredPaths) throws VcsException {

    Path dir = Paths.get(remotePath);
    try {
      patchGitIgnore(dir, ignoredPaths);
    } catch (IOException e) {
      throw new VcsException("Can not create .gitignore file in path " + remotePath, e);
    }
    InitCommandResult initCommandResult = myGitRepoOperations.initCommand().initAndCommit(remotePath, commitSettings);
    Map<String, String> props = myVcs.getDefaultVcsProperties();
    props.put(Constants.FETCH_URL, "file://" + remotePath);
    props.put(Constants.BRANCH_NAME, initCommandResult.getDefaultBranch());
    return props;
  }

  private void patchGitIgnore(Path dir, List<String> ignoredPaths) throws IOException {
    Path gitIgnore = dir.resolve(".gitignore");

    List<String> newPaths = new ArrayList<>();
    if (!Files.exists(gitIgnore)) {
      newPaths.addAll(ignoredPaths);
    } else {
      newPaths.addAll(Files.readAllLines(gitIgnore));
      Set<String> existingPaths = new HashSet<>(newPaths);
      for (String ignoredPath : ignoredPaths) {
        if (!existingPaths.contains(ignoredPath)) {
          newPaths.add(ignoredPath);
        }
      }
    }
    if (!newPaths.isEmpty()) {
      Files.write(gitIgnore, newPaths);
    }
  }
}
