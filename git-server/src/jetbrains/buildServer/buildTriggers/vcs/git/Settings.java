package jetbrains.buildServer.buildTriggers.vcs.git;

import com.intellij.openapi.util.text.StringUtil;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.spearce.jgit.transport.URIish;

import java.io.File;
import java.net.URISyntaxException;

/**
 * Git Vcs Settings
 */
public class Settings {
  /**
   * The url for the repository
   */
  private String repositoryURL;
  /**
   * The current branch
   */
  private String branch;
  /**
   * The repository path
   */
  private File repositoryPath;
  /**
   * The repository user name
   */
  private String username;
  /**
   * The repository password
   */
  private String password;

  /**
   * The constructor from the root object
   *
   * @param root the root
   */
  public Settings(VcsRoot root) throws VcsException {
    final String p = root.getProperty(Constants.PATH);
    repositoryPath = p == null ? null : new File(p);
    branch = root.getProperty(Constants.BRANCH_NAME);
    username = root.getProperty(Constants.USERNAME);
    password = root.getProperty(Constants.PASSWORD);
    final String remote = root.getProperty(Constants.URL);
    URIish uri;
    try {
      uri = new URIish(remote);
    } catch (URISyntaxException e) {
      throw new VcsException("Invalid URI: " + remote);
    }
    if (!StringUtil.isEmptyOrSpaces(username)) {
      uri.setUser(username);
    }
    if (!StringUtil.isEmpty(password)) {
      uri.setUser(password);
    }
    repositoryURL = uri.toPrivateString();
  }

  /**
   * @return the local repository path
   */
  public File getRepositoryPath() {
    return repositoryPath;
  }

  /**
   * Set repository path
   *
   * @param file the path to set
   */
  public void setRepositoryPath(File file) {
    repositoryPath = file;
  }

  /**
   * @return the URL for the repository
   */
  public String getRepositoryURL() {
    return repositoryURL;
  }

  /**
   * @return the branch name
   */
  public String getBranch() {
    return branch == null || branch.length() == 0 ? "master" : branch;
  }
}
