package jetbrains.buildServer.buildTriggers.vcs.git;

import com.intellij.openapi.util.Pair;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitConfigCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.InitCommandResult;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.impl.configsRepo.RepositoryInitializingExtension;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.CommitSettings;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import org.eclipse.jgit.lib.PersonIdent;
import org.jetbrains.annotations.NotNull;

public class GitRepositoryInitializingExtension implements RepositoryInitializingExtension {

  private final GitVcsSupport myVcs;
  private final GitRepoOperations myGitRepoOperations;

  public GitRepositoryInitializingExtension(GitVcsSupport vcs, GitRepoOperations gitRepoOperations) {
    myVcs = vcs;
    myGitRepoOperations = gitRepoOperations;
  }

  @NotNull
  @Override
  public Map<String, String> initialize(@NotNull String repositoryPath, @NotNull CommitSettings commitSettings, @NotNull List<String> ignoredPaths) throws VcsException {

    Path dir = Paths.get(repositoryPath);
    try {
      patchGitIgnore(dir, ignoredPaths);
    } catch (IOException e) {
      throw new VcsException("Could not create .gitignore file at path: " + repositoryPath, e);
    }

    Path gitDir = dir.resolve(".git");
    final boolean gitDirExisted = Files.exists(gitDir);

    File tmpRepoDir = null;
    try {
      String repoPath;
      if (gitDirExisted) {
        repoPath = repositoryPath;
      } else {
        tmpRepoDir = FileUtil.createTempDirectory("git-repo-init", "");
        repoPath = tmpRepoDir.getAbsolutePath();
      }

      InitCommandResult initCommandResult = myGitRepoOperations.initCommand().init(repoPath, false);
      Map<String, String> props = myVcs.getDefaultVcsProperties();
      props.put(Constants.FETCH_URL, GitUtils.toURL(new File(repositoryPath)));
      props.put(Constants.BRANCH_NAME, initCommandResult.getDefaultBranch());
      VcsRootImpl dummyRoot = new VcsRootImpl(-1, Constants.VCS_NAME, props);

      if (gitDirExisted) {
        myGitRepoOperations.commitCommand().commit(repositoryPath, commitSettings, Collections.emptyList());
        return props;
      }
      OperationContext operationContext = myVcs.createContext(dummyRoot, "Repository initialization");
      PersonIdent personIdent = PersonIdentFactory.getTagger(operationContext.getGitRoot(), operationContext.getRepository());

      List<Pair<String, String>> configProps = Arrays.asList(
        Pair.create("user.name", personIdent.getName()),
        Pair.create("user.email", personIdent.getEmailAddress()),
        Pair.create("core.autocrlf", "false"),
        Pair.create("receive.denyCurrentBranch", "ignore"),
        Pair.create("core.worktree", repositoryPath)
      );

      for (Pair<String, String> configProp : configProps) {
        myGitRepoOperations.configCommand().addConfigParameter(repoPath, GitConfigCommand.Scope.LOCAL, configProp.getFirst(), configProp.getSecond());
      }
      myGitRepoOperations.commitCommand().commit(repoPath, commitSettings, Collections.emptyList());
      List<String> errors = new ArrayList<>();
      FileUtil.moveDirWithContent(tmpRepoDir, dir.toFile(), errors::add);
      if (!errors.isEmpty()) {
        throw new IOException("Failed to initialize Git repository at " + repositoryPath
                              + ". Some files were not copied to the destination directory from temp .git directory. The following errors were reported: " + errors);
      }
      return props;
    } catch (Exception e) {
      //if any exception during initialization occurs looks like it's better to remove initialized repository if it was created just now (i.e. directory didn't exist on method start)
      if (!gitDirExisted) {
        File file = gitDir.toFile();
        Loggers.VCS.warn("Removing the partially initialized Git repository at path: " + file.getAbsolutePath());
        FileUtil.delete(file);
      }
      if (tmpRepoDir != null) {
        FileUtil.delete(tmpRepoDir);
      }
      throw new VcsException(e);
    }
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
