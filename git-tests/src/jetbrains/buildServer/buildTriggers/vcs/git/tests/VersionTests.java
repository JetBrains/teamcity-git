package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsSupport;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.AfterMethod;
import org.testng.Assert;

import java.io.File;
import java.io.IOException;

/**
 * The tests for version detection funcitonality
 */
@Test
public class VersionTests {
    /** The source directory */
    protected File mySourceRep;
    /** The source directory */
    protected File myCloneRep;
    /**
     * Temporary files
     */
    protected static TempFiles myTempFiles = new TempFiles();
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
          public void run() {
            myTempFiles.cleanup();
          }
        }));
    }


    /**
     * Test data file
     * @param path the file path relatively to data directory
     * @return the IO file object (the file is absolute)
     */
    protected File dataFile(String path) {
        return new File("git-tests"+File.separatorChar+"data", path).getAbsoluteFile();
    }

    /**
     * Create a VCS root for the current parameters and specified branch
     * @param branchName the branch name
     * @return a created vcs root object
     * @throws IOException if the root could not be created
     */
    protected VcsRoot getRoot(String branchName) throws IOException {
        VcsRootImpl myRoot = new VcsRootImpl(1, Constants.VCS_NAME);
        myRoot.addProperty(Constants.URL, GitUtils.toURL(mySourceRep));
        myRoot.addProperty(Constants.PATH, myCloneRep.getPath());
        if(branchName != null) {
            myRoot.addProperty(Constants.BRANCH_NAME, branchName);
        }
        return myRoot;
    }

    /**
     * @return a created vcs support object
     */
    protected GitVcsSupport getSupport() {
      return new GitVcsSupport(null);
    }

    


    /**
     * Setup test environment
     * @throws IOException in case of IO problem
     */
    @BeforeMethod
    public void setUp() throws IOException {
        File masterRep = dataFile("repo.git");
        mySourceRep = myTempFiles.createTempDir();
        FileUtil.copyDir(masterRep, mySourceRep);
        myCloneRep = myTempFiles.createTempDir();

        // TODO create temp local repository
        // TODO setup root
    }



    /**
     * Tear down test environment
     */
    @AfterMethod
    public void tearDown() {
        // clear root
        myTempFiles.cleanup();
    }

    /**
     * The connection test
     * @throws Exception in case of IO problem
     */
    @Test
    public void testConnection() throws Exception {
        GitVcsSupport support = getSupport();
        VcsRoot root = getRoot("version-test");
        support.testConnection(root);
        try {
            root = getRoot("no-such-branch");
            support.testConnection(root);
            Assert.fail("The connection should have failed");
        } catch(VcsException ex) {
            // test successful
        }
    }
}
