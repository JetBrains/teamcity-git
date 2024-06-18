package jetbrains.buildServer.buildTriggers.vcs.git;

import com.intellij.openapi.util.Pair;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitConfigCommand;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.impl.configsRepo.CentralRepositoryConfiguration;
import jetbrains.buildServer.serverSide.impl.configsRepo.RepositoryInitializingExtension;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.CommitSettings;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.RefSpec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GitRepositoryInitializingExtension implements RepositoryInitializingExtension {

  private final GitVcsSupport myVcs;
  private final GitRepoOperations myGitRepoOperations;
  private final ServerPaths myServerPaths;

  public GitRepositoryInitializingExtension(GitVcsSupport vcs, GitRepoOperations gitRepoOperations, ServerPaths serverPaths) {
    myVcs = vcs;
    myGitRepoOperations = gitRepoOperations;
    myServerPaths = serverPaths;
  }

  @Override
  public void createSelfHostedRepository(Path path) throws Exception {
    if (!Files.exists(path)) {
      Files.createDirectories(path);
    }
    myGitRepoOperations.initCommand().init(path.toString(), true);
  }

  @NotNull
  @Override
  public Map<String, String> commitAllChanges(@NotNull CentralRepositoryConfiguration repositoryConfiguration,
                                              @NotNull CommitSettings commitSettings,
                                              @NotNull List<String> ignoredPaths,
                                              @Nullable FilesProcessor filesProcessor) throws VcsException {
    String repositoryUrl = repositoryConfiguration.getRepositoryUrl();
    String fullBranch = repositoryConfiguration.getBranch();
    if (fullBranch == null) {
      throw new RuntimeException("Branch isn't specified for central repository");
    }
    Path configDir = Paths.get(myServerPaths.getConfigDir());
    try {
      patchGitIgnore(configDir, ignoredPaths);
    } catch (IOException e) {
      throw new VcsException("Could not create .gitignore file at path: " + repositoryUrl, e);
    }

    File tmpRepoDir = null;
    try {
      tmpRepoDir = FileUtil.createTempDirectory("git-repo-init", "");
      String repoPath = tmpRepoDir.getAbsolutePath();

      myGitRepoOperations.initCommand().init(repoPath, false);

      Map<String, String> props = myVcs.getDefaultVcsProperties();
      props.put(Constants.FETCH_URL, repositoryUrl);
      props.put(Constants.BRANCH_NAME, repositoryConfiguration.getBranch());
      VcsRootImpl dummyRoot = new VcsRootImpl(-1, Constants.VCS_NAME, props);

      OperationContext operationContext = myVcs.createContext(dummyRoot, "Repository initialization");
      GitVcsRoot gitRoot = operationContext.getGitRoot();

      PersonIdent personIdent = PersonIdentFactory.getTagger(gitRoot, operationContext.getRepository());
      List<Pair<String, String>> configProps = Arrays.asList(
        Pair.create("user.name", personIdent.getName()),
        Pair.create("user.email", personIdent.getEmailAddress()),
        Pair.create("core.autocrlf", "false"),
        Pair.create("receive.denyCurrentBranch", "ignore")
      );

      for (Pair<String, String> configProp : configProps) {
        myGitRepoOperations.configCommand().addConfigParameter(repoPath, GitConfigCommand.Scope.LOCAL, configProp.getFirst(), configProp.getSecond());
      }

      Repository db = FileRepositoryBuilder.create(new File(tmpRepoDir, ".git"));
      StoredConfig config = db.getConfig();
      config.setString("remote", "origin", "url", repositoryUrl);
      config.setString("remote", "origin", "fetch", "+refs/*:refs/*");
      config.save();
      Git git = new Git(db);
      RefSpec refSpec = new RefSpec().setSourceDestination("refs/*", "refs/*").setForceUpdate(true);
      myGitRepoOperations.fetchCommand(repositoryUrl).fetch(db,
                                                            gitRoot.getRepositoryFetchURL().get(),
                                                            new FetchSettings(gitRoot.getAuthSettings(),
                                                                              Collections.singleton(refSpec)));
      boolean originExisted = false;
      for (Ref branchRef : git.branchList().call()) {
        if (branchRef.getName().equals(repositoryConfiguration.getBranch())) {
          originExisted = true;
          break;
        }
      }
      String shortBranch = fullBranch.replace("refs/heads/", "");
      String initBranch = "tempInitializationBranch";
      if (originExisted) {
        git.checkout().
           setCreateBranch(true).
           setName(initBranch).
           setStartPoint(shortBranch).
           call();
      }
      RevCommit lastCommit = getLastCommit(db, fullBranch);

      myGitRepoOperations.configCommand().addConfigParameter(repoPath, GitConfigCommand.Scope.LOCAL, "core.worktree", configDir.toString());
      if (filesProcessor == null) {
        myGitRepoOperations.addCommand().add(repoPath, Collections.emptyList());
        myGitRepoOperations.commitCommand().commit(repoPath, commitSettings);
      } else {
        Files.walkFileTree(configDir, new FileVisitor<Path>() {
          @Override
          public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            return processByProcessor(dir, filesProcessor, repoPath);
          }

          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            return processByProcessor(file, filesProcessor, repoPath);
          }

          @Override
          public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            throw exc;
          }

          @Override
          public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
            return FileVisitResult.CONTINUE;
          }
        });
        myGitRepoOperations.addCommand().add(repoPath, Collections.emptyList());
        myGitRepoOperations.commitCommand().commit(repoPath, commitSettings);

        if (!db.getRefDatabase().hasRefs()) {
          //no refs exist, there were nothing to commit
          return props;
        }

        if (!originExisted) {
          git.branchRename().setNewName(initBranch).call();
        }
      }
      RevCommit createdCommit = getLastCommit(db, GitUtils.expandRef(initBranch));
      myGitRepoOperations.pushCommand(repositoryUrl).push(db, gitRoot, repositoryConfiguration.getBranch(), createdCommit.name(), lastCommit.name());

      return props;
    } catch (Exception e) {
      throw new VcsException(e);
    } finally {
      if (tmpRepoDir != null) {
        FileUtil.delete(tmpRepoDir);
      }
    }
  }

  private RevCommit getLastCommit(Repository db, String branch) throws VcsException {
    try {
      Ref ref = db.exactRef(branch);
      RevWalk revWalk = new RevWalk(db);
      if (ref == null) {
        return revWalk.lookupCommit(ObjectId.zeroId());
      }
      return revWalk.parseCommit(ref.getObjectId());
    } catch (Exception e) {
      throw new VcsException("Can't load last commit from repository", e);
    }
  }

  @NotNull
  private FileVisitResult processByProcessor(Path path, @NotNull FilesProcessor filesProcessor, String repoPath) {
    ProcessResult process = filesProcessor.process(path);
    FileVisitResult result = process == ProcessResult.STEP_INSIDE ? FileVisitResult.CONTINUE : FileVisitResult.SKIP_SUBTREE;
    if (process == ProcessResult.COMMIT) {
      try {
        myGitRepoOperations.addCommand().add(repoPath, Collections.singletonList(path.toAbsolutePath().toString()));
      } catch (VcsException e) {
        if (!Files.exists(path)) {
          return result;
        }
        throw new RuntimeException("VCs exception occurred during performing commit for directory: " + path, e);
      }
    }
    return result;
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
