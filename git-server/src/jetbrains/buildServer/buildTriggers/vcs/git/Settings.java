package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.vcs.VcsRoot;

import java.io.File;

/**
 * Git Vcs Settings
 */
public class Settings {
    /** The url for the repository */
    private String repositoryURL;
    /** The current branch */
    private String branch;
    /** The repository path */
    private String repositoryPath;
    /** The repository user name */
    private String username;
    /** The repository password */
    private String password;

    /**
     * The constructor from the root object
     * @param root the root
     */
    public Settings(VcsRoot root) {
        repositoryURL = root.getProperty(Constants.URL);
        repositoryPath = root.getProperty(Constants.PATH);
        branch = root.getProperty(Constants.BRANCH_NAME);
        username = root.getProperty(Constants.USERNAME);
        password = root.getProperty(Constants.PASSWORD);
    }

    /**
     * @return the repository path 
     */
    public File getRepositoryPath() {
        return new File(repositoryPath);
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
