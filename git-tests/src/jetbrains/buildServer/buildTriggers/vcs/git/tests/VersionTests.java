package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsSupport;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.ModificationData;
import jetbrains.buildServer.vcs.VcsChange;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * The tests for version detection funcitonality
 */
@Test
public class VersionTests extends Assert {
    /**
     * The version of "version-test" HEAD
     */
    private static final String VERSION_TEST_HEAD = GitUtils.makeVersion("2276eaf76a658f96b5cf3eb25f3e1fda90f6b653", 1237391915000L);
    /**
     * The versio that contains add/remove/update changes
     */
    private static final String CUD1_VERSION = GitUtils.makeVersion("ad4528ed5c84092fdbe9e0502163cf8d6e6141e7", 1238072086000L);
    /**
     * The merge head version
     */
    private static final String MERGE_VERSION = GitUtils.makeVersion("f3f826ce85d6dad25156b2d7550cedeb1a422f4c", 1238086450000L);
    /**
     * The merge branch version
     */
    private static final String MERGE_BRANCH_VERSION = GitUtils.makeVersion("ee886e4adb70fbe3bdc6f3f6393598b3f02e8009", 1238085489000L);
    /**
     * The source directory
     */
    protected File mySourceRep;
    /**
     * The source directory
     */
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
     *
     * @param path the file path relatively to data directory
     * @return the IO file object (the file is absolute)
     */
    protected File dataFile(String... path) {
        File f = new File("git-tests", "data");
        for (String p : path) {
            f = new File(f, p);
        }
        return f.getAbsoluteFile();
    }

    /**
     * Create a VCS root for the current parameters and specified branch
     *
     * @param branchName the branch name
     * @return a created vcs root object
     * @throws IOException if the root could not be created
     */
    protected VcsRoot getRoot(String branchName) throws IOException {
        VcsRootImpl myRoot = new VcsRootImpl(1, Constants.VCS_NAME);
        myRoot.addProperty(Constants.URL, GitUtils.toURL(mySourceRep));
        myRoot.addProperty(Constants.PATH, myCloneRep.getPath());
        if (branchName != null) {
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
     *
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
     *
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
        } catch (VcsException ex) {
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
     *
     * @throws Exception in case of bug
     */
    @Test
    public void testGetContent() throws Exception {
        GitVcsSupport support = getSupport();
        VcsRoot root = getRoot("version-test");
        String version = support.getCurrentVersion(root);
        byte[] data1 = support.getContent("readme.txt", root, version);
        byte[] data2 = FileUtil.loadFileBytes(dataFile("content", "readme.txt"));
        Assert.assertEquals(data1, data2);
    }

    /**
     * Test getting changes for the build
     *
     * @throws Exception in case of IO problem
     */
    @Test
    public void testCollectBuildChanges() throws Exception {
        GitVcsSupport support = getSupport();
        VcsRoot root = getRoot("master");
        // ensure that all revisions reachable from master are fetched
        support.getCurrentVersion(root);
        final List<ModificationData> ms = support.collectBuildChanges(root, VERSION_TEST_HEAD, CUD1_VERSION, null);
        assertEquals(2, ms.size());
        ModificationData m2 = ms.get(1);
        assertEquals("The second commit\n", m2.getDescription());
        assertEquals(3, m2.getChanges().size());
        for (VcsChange ch : m2.getChanges()) {
            assertEquals(VcsChange.Type.ADDED, ch.getType());
            assertEquals("dir/", ch.getFileName().substring(0, 4));
        }
        ModificationData m1 = ms.get(0);
        assertEquals("more changes\n", m1.getDescription());
        assertEquals(CUD1_VERSION, m1.getVersion());
        assertEquals(3, m1.getChanges().size());
        VcsChange ch10 = m1.getChanges().get(0);
        assertEquals("dir/a.txt", ch10.getFileName());
        assertEquals(CUD1_VERSION, ch10.getAfterChangeRevisionNumber());
        assertEquals(m2.getVersion(), ch10.getBeforeChangeRevisionNumber());
        assertEquals(VcsChange.Type.CHANGED, ch10.getType());
        VcsChange ch11 = m1.getChanges().get(1);
        assertEquals("dir/c.txt", ch11.getFileName());
        assertEquals(VcsChange.Type.ADDED, ch11.getType());
        VcsChange ch12 = m1.getChanges().get(2);
        assertEquals("dir/tr.txt", ch12.getFileName());
        assertEquals(VcsChange.Type.REMOVED, ch12.getType());
        // now check merge commit relatively to the branch
        final List<ModificationData> mms0 = support.collectBuildChanges(root, MERGE_BRANCH_VERSION, MERGE_VERSION, null);
        assertEquals(2, mms0.size());
        // no check the merge commit relatively to the fork
        final List<ModificationData> mms1 = support.collectBuildChanges(root, CUD1_VERSION, MERGE_VERSION, null);
        assertEquals(3, mms1.size());
        ModificationData md1 = mms1.get(0);
        assertEquals("merge commit\n", md1.getDescription());
        assertEquals(MERGE_VERSION, md1.getVersion());
        assertEquals(3, md1.getChanges().size());
        VcsChange ch20 = md1.getChanges().get(0);
        assertEquals("dir/a.txt", ch20.getFileName());
        assertEquals(VcsChange.Type.REMOVED, ch20.getType());
        VcsChange ch21 = md1.getChanges().get(1);
        assertEquals("dir/b.txt", ch21.getFileName());
        assertEquals(VcsChange.Type.CHANGED, ch21.getType());
        VcsChange ch22 = md1.getChanges().get(2);
        assertEquals("dir/q.txt", ch22.getFileName());
        assertEquals(VcsChange.Type.ADDED, ch22.getType());
        ModificationData md2 = mms1.get(1);
        assertEquals("b-mod, d-add\n", md2.getDescription());
        assertEquals(MERGE_BRANCH_VERSION, md2.getVersion());
        assertEquals(2, md2.getChanges().size());
        ModificationData md3 = mms1.get(2);
        assertEquals("a-mod, c-rm\n", md3.getDescription());
        assertEquals(2, md3.getChanges().size());
    }
}
