package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.vcs.VcsRoot;

import java.io.File;

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
    public Settings(VcsRoot root) {
        repositoryURL = root.getProperty(Constants.URL);
        final String p = root.getProperty(Constants.PATH);
        repositoryPath = p == null ? null : new File(p);
        branch = root.getProperty(Constants.BRANCH_NAME);
        username = root.getProperty(Constants.USERNAME);
        password = root.getProperty(Constants.PASSWORD);
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
