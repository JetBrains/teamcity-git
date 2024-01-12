

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import jetbrains.buildServer.agent.oauth.AgentTokenStorage;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.connections.ExpiringAccessToken;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;

import java.io.File;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Agent Git plugin settings class
 */
public class AgentGitVcsRoot extends GitVcsRoot {

  /**
   * The clean policy for the agent
   */
  private final AgentCleanPolicy myCleanPolicy;
  /**
   * The policy for cleaning files
   */
  private final AgentCleanFilesPolicy myCleanFilesPolicy;
  /**
   * Local repository directory
   */
  private final File myLocalRepositoryDir;

  private final AgentTokenStorage myTokenStorage;

  public AgentGitVcsRoot(MirrorManager mirrorManager, VcsRoot root, AgentTokenStorage tokenStorage) throws VcsException {
    super(mirrorManager, root, new URIishHelperImpl(), true);
    myLocalRepositoryDir = getRepositoryDir();
    String clean = getProperty(Constants.AGENT_CLEAN_POLICY);
    myCleanPolicy = clean == null ? AgentCleanPolicy.ON_BRANCH_CHANGE : AgentCleanPolicy.valueOf(clean);
    String cleanFiles = getProperty(Constants.AGENT_CLEAN_FILES_POLICY);
    myCleanFilesPolicy = cleanFiles == null ? AgentCleanFilesPolicy.ALL_UNTRACKED : AgentCleanFilesPolicy.valueOf(cleanFiles);
    myTokenStorage = tokenStorage;
  }


  public AgentGitVcsRoot(MirrorManager mirrorManager, File localRepositoryDir, VcsRoot root, AgentTokenStorage tokenStorage) throws VcsException {
    super(mirrorManager, root, new URIishHelperImpl(), true);
    myLocalRepositoryDir = localRepositoryDir;
    String clean = getProperty(Constants.AGENT_CLEAN_POLICY);
    myCleanPolicy = clean == null ? AgentCleanPolicy.ON_BRANCH_CHANGE : AgentCleanPolicy.valueOf(clean);
    String cleanFiles = getProperty(Constants.AGENT_CLEAN_FILES_POLICY);
    myCleanFilesPolicy = cleanFiles == null ? AgentCleanFilesPolicy.ALL_UNTRACKED : AgentCleanFilesPolicy.valueOf(cleanFiles);
    myTokenStorage = tokenStorage;
  }

  /**
   * @return the clean policy for the agent
   */
  public AgentCleanPolicy getCleanPolicy() {
    return myCleanPolicy;
  }

  /**
   * @return specifies which files should be cleaned after checkout
   */
  public AgentCleanFilesPolicy getCleanFilesPolicy() {
    return myCleanFilesPolicy;
  }

  /**
   * @return the local repository directory
   */
  public File getLocalRepositoryDir() {
    return myLocalRepositoryDir;
  }

  public File getRepositoryDir() {
    //ignore custom clone path on server
    String fetchUrl = getRepositoryFetchURL().toString();
    return myMirrorManager.getMirrorDir(fetchUrl);
  }

  /**
   * @return debug information
   */
  public String debugInfo() {
    return "(" + getName() + ", " + getLocalRepositoryDir() + "," + getRepositoryFetchURL().toString() + ")";
  }

  @Nullable
  protected ExpiringAccessToken getOrRefreshToken(@NotNull String tokenId) {
    return myTokenStorage == null ? null : myTokenStorage.getOrRefreshToken(tokenId);
  }
}