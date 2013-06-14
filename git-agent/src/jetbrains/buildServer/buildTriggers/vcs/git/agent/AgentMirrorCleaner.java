package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.agent.DirectoryCleanersProvider;
import jetbrains.buildServer.agent.DirectoryCleanersProviderContext;
import jetbrains.buildServer.agent.DirectoryCleanersRegistry;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsRoot;
import jetbrains.buildServer.buildTriggers.vcs.git.MirrorManager;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.VcsRootEntry;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AgentMirrorCleaner implements DirectoryCleanersProvider {

  private final static Logger ourLog = Logger.getInstance(AgentMirrorCleaner.class.getName());
  private final MirrorManager myMirrorManager;

  public AgentMirrorCleaner(@NotNull MirrorManager mirrorManager) {
    myMirrorManager = mirrorManager;
  }

  @NotNull
  public String getCleanerName() {
    return "Git mirrors cleaner";
  }

  public void registerDirectoryCleaners(@NotNull DirectoryCleanersProviderContext context,
                                        @NotNull DirectoryCleanersRegistry registry) {
    Set<String> repositoriesUsedInBuild = getRunningBuildRepositories(context);
    for (Map.Entry<String, File> entry : myMirrorManager.getMappings().entrySet()) {
      String repository = entry.getKey();
      File mirror = entry.getValue();
      if (!repositoriesUsedInBuild.contains(repository)) {
        ourLog.debug("Register cleaner for mirror " + mirror.getAbsolutePath());
        registry.addCleaner(mirror, new Date(myMirrorManager.getLastUsedTime(mirror)));
      }
    }
  }

  private Set<String> getRunningBuildRepositories(@NotNull DirectoryCleanersProviderContext context) {
    Set<String> repositories = new HashSet<String>();
    for (VcsRootEntry entry : context.getRunningBuild().getVcsRootEntries()) {
      VcsRoot root = entry.getVcsRoot();
      if (!Constants.VCS_NAME.equals(root.getVcsName()))
        continue;
      try {
        GitVcsRoot gitRoot = new GitVcsRoot(myMirrorManager, root);
        String repositoryUrl = gitRoot.getRepositoryFetchURL().toString();
        ourLog.debug("Repository " + repositoryUrl + " is used in the build, its mirror won't be cleaned");
        repositories.add(gitRoot.getRepositoryFetchURL().toString());
      } catch (VcsException e) {
        ourLog.warn("Error while creating git root " + root.getName());
      }
    }
    return repositories;
  }
}
