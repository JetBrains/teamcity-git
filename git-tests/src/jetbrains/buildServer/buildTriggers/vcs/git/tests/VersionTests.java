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
    /**
     * The version of "version-test" HEAD
     */
    private static final String VERSION_TEST_HEAD = GitUtils.makeVersion("2276eaf76a658f96b5cf3eb25f3e1fda90f6b653", 1237391915L*1000);

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
    protected File dataFile(String... path) {
        File f = new File("git-tests", "data");
        for(String p : path) {
            f = new File(f, p);
        }
        return f.getAbsoluteFile();
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
        // TODO The directory is deleted because JGIT wants to create a git directory itself
        //noinspection ResultOfMethodCallIgnored
        myCloneRep.delete();
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

    /**
     * The current version test
     * 
     * @throws Exception in case of IO problem
     */
    @Test
    public void testCurrentVersion() throws Exception {
        GitVcsSupport support = getSupport();
        VcsRoot root = getRoot("version-test");
        String version = support.getCurrentVersion(root);
        Assert.assertEquals(VERSION_TEST_HEAD, version);
    }

    /**
     * Test get content for the file
     * @throws Exception in case of bug
     */
    @Test
    public void testGetContent() throws Exception {
        GitVcsSupport support = getSupport();
        VcsRoot root = getRoot("version-test");
        String version = support.getCurrentVersion(root);
        byte[] data1 = support.getContent("readme.txt", root, version);
        byte[] data2 = FileUtil.loadFileBytes(dataFile("content","readme.txt"));
        Assert.assertEquals(data1, data2);
    }
}
