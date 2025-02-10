package jetbrains.buildServer.buildTriggers.vcs.git.tests.gitProxy;

import java.util.*;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.buildTriggers.vcs.git.gitProxy.*;
import jetbrains.buildServer.buildTriggers.vcs.git.gitProxy.data.*;
import jetbrains.buildServer.buildTriggers.vcs.git.gitProxy.data.ChangeType;
import jetbrains.buildServer.buildTriggers.vcs.git.tests.util.BaseGitServerTestCase;
import jetbrains.buildServer.serverSide.MockParameter;
import jetbrains.buildServer.serverSide.parameters.ParameterFactory;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.impl.SVcsRootImpl;
import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * class for local experiments on performance.
 * This test is not intended to be executed in CI
 */
public class GitProxyPerformanceTest extends BaseGitServerTestCase {

  private VcsRootInstance myVcsRootInstance;
  private GitRepoApi myGitRepoApi;
  private GitCollectChangesPolicy myCollectChangesPolicy;

  @BeforeMethod(alwaysRun = true)
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    SVcsRootImpl root = myFixture.addVcsRoot("jetbrains.git", "", myBuildType);
    root.addProperty("url", "http://jetbrains.team/project/repo");
    myVcsRootInstance = myBuildType.getVcsRootInstanceForParent(root);

    ParameterFactory factory = myFixture.getSingletonService(ParameterFactory.class);
    myProject.addParameter(new MockParameter("teamcity.internal.git.gitProxy.changesCollection.enabled", "true"));
    myProject.addParameter(new MockParameter("teamcity.internal.git.gitProxy.url", "aaaa"));
    myProject.addParameter(new MockParameter("teamcity.internal.git.gitProxy.auth", "bbbb"));

    GitApiClientFactory gitApiClientFactory = Mockito.mock(GitApiClientFactory.class);
    myGitRepoApi = Mockito.mock(GitRepoApi.class);
    GitApiClient<GitRepoApi> gitApiClient = new GitApiClient<>(requestData -> myGitRepoApi);
    Mockito.doReturn(gitApiClient).when(gitApiClientFactory).createRepoApi(Mockito.any(), Mockito.any(), Mockito.any());

    OperationContext mockOperationContext = Mockito.mock(OperationContext.class);
    Mockito.doReturn(new GitVcsRoot(Mockito.mock(MirrorManager.class), myVcsRootInstance, new URIishHelperImpl())).when(mockOperationContext).getGitRoot();
    GitVcsSupport mockGitVcsSupport = Mockito.mock(GitVcsSupport.class);
    Mockito.doReturn(mockOperationContext).when(mockGitVcsSupport).createContext(myVcsRootInstance, "collecting changes");
    myCollectChangesPolicy = new GitCollectChangesPolicy(mockGitVcsSupport,
                                                         Mockito.mock(VcsOperationProgressProvider.class),
                                                         Mockito.mock(ServerPluginConfig.class),
                                                         Mockito.mock(RepositoryManager.class),
                                                         Mockito.mock(CheckoutRulesLatestRevisionCache.class),
                                                         gitApiClientFactory,
                                                         factory,
                                                         new ChangesCollectorCache());
  }

  @Test
  public void testPerformanceLargeStateWithCaching() throws VcsException {
    setInternalProperty(GitProxyChangesCollector.GIT_PROXY_CACHING_PROPERTY, "true");
    int n = 50;
    int stateSize = 100_000;
    Map<String, String> fromState = new HashMap<>();
    Map<String, String> toState = new HashMap<>();
    for (int j = 0; j < stateSize; j++) {
      fromState.put("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" + j, "4b325275b0b04fdd5078ac46f97f6cdfa94fd481");
      toState.put("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" + j, "4b325275b0b04fdd5078ac46f97f6cdfa94fd481");
    }
    fromState.put("master", "11125275b0b04fdd5078ac46f97f6cdfa94fd481");
    toState.put("master", "22225275b0b04fdd5078ac46f97f6cdfa94fd481");

    CommitList commitList = new CommitList();
    commitList.commits = Arrays.asList(new Commit("rev5", new CommitInfo("rev5", "", "commit5", new Person("user", "user@email.com"), 1, new Person("user2", "user2@email.com"), 2, Arrays.asList("rev4"))));
    List<CommitChange> changes = Arrays.asList(new CommitChange("rev5", "rev4", false, Arrays.asList(new FileChange(ChangeType.Modified, "file1", "file1", EntryType.File), new FileChange(ChangeType.Modified, "file2", "file2", EntryType.File))));
    Mockito.doReturn(commitList).when(myGitRepoApi).listCommits(Mockito.anyList(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyBoolean(), Mockito.anyBoolean());
    Mockito.doReturn(changes).when(myGitRepoApi).listChanges(Mockito.anyList(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyInt());

    long start = System.currentTimeMillis();
    for (int i = 0; i < n; i++) {
      myCollectChangesPolicy.collectChanges(myVcsRootInstance,
                                           RepositoryStateData.createVersionState("master", fromState),
                                           RepositoryStateData.createVersionState("master", toState),
                                           new CheckoutRules(""));
    }

    long total = System.currentTimeMillis() - start;
    System.out.println("Total time: " + total + " ms. Average time: " + total / n + " ms");
  }

  @Test
  public void testLargeResultWithoutCaching() throws VcsException {
    setInternalProperty(GitProxyChangesCollector.GIT_PROXY_CACHING_PROPERTY, "false");

    int n = 50;
    int stateSize = 100_000;
    int cntCommits = 1000;
    int filesPerCommit = 1000;

    Map<String, String> fromState = new HashMap<>();
    Map<String, String> toState = new HashMap<>();
    for (int j = 0; j < stateSize; j++) {
      fromState.put("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" + j, "4b325275b0b04fdd5078ac46f97f6cdfa94fd481");
      toState.put("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" + j, "4b325275b0b04fdd5078ac46f97f6cdfa94fd481");
    }
    fromState.put("master", "11125275b0b04fdd5078ac46f97f6cdfa94fd481");
    toState.put("master", "22225275b0b04fdd5078ac46f97f6cdfa94fd481");

    CommitList commitList = new CommitList();
    commitList.commits = new ArrayList<>();
    List<CommitChange> changes = new ArrayList<>();

    for (int j = 1; j < cntCommits + 1; j++) {
      commitList.commits.add(new Commit("rev" + j, new CommitInfo("rev" + j, "", "commit" + j, new Person("user", "user@email.com"), 1, new Person("user2", "user2@email.com"), 2, Arrays.asList("rev" + (j - 1)))));
      List<FileChange> fileChanges = new ArrayList<>();
      for (int k = 0; k < filesPerCommit; k++) {
        fileChanges.add(new FileChange(ChangeType.Modified, "bbbbbbbbbbbbbbbbbbbbbbb" + k, "aaaaaaaaaaaaaaaaaa" + k, EntryType.File));
      }
      changes.add(new CommitChange("rev" + j, "rev" + (j - 1), false, fileChanges));
    }
    Mockito.doReturn(commitList).when(myGitRepoApi).listCommits(Mockito.anyList(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyBoolean(), Mockito.anyBoolean());
    Mockito.doReturn(changes).when(myGitRepoApi).listChanges(Mockito.anyList(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyInt());

    long start = System.currentTimeMillis();
    for (int i = 0; i < n; i++) {
      myCollectChangesPolicy.collectChanges(myVcsRootInstance,
                                            RepositoryStateData.createVersionState("master", fromState),
                                            RepositoryStateData.createVersionState("master", toState),
                                            new CheckoutRules(""));
    }

    long total = System.currentTimeMillis() - start;
    System.out.println("Total time: " + total + " ms. Average time: " + total / n + " ms");
  }

  @Test
  public void testLargeResultWithCaching() throws VcsException {
    setInternalProperty(GitProxyChangesCollector.GIT_PROXY_CACHING_PROPERTY, "true");

    int n = 50;
    int stateSize = 100_000;
    int cntCommits = 1000;
    int filesPerCommit = 1000;

    Map<String, String> fromState = new HashMap<>();
    Map<String, String> toState = new HashMap<>();
    for (int j = 0; j < stateSize; j++) {
      fromState.put("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" + j, "4b325275b0b04fdd5078ac46f97f6cdfa94fd481");
      toState.put("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" + j, "4b325275b0b04fdd5078ac46f97f6cdfa94fd481");
    }
    fromState.put("master", "11125275b0b04fdd5078ac46f97f6cdfa94fd481");
    toState.put("master", "22225275b0b04fdd5078ac46f97f6cdfa94fd481");

    CommitList commitList = new CommitList();
    commitList.commits = new ArrayList<>();
    List<CommitChange> changes = new ArrayList<>();

    for (int j = 1; j < cntCommits + 1; j++) {
      commitList.commits.add(new Commit("rev" + j, new CommitInfo("rev" + j, "", "commit" + j, new Person("user", "user@email.com"), 1, new Person("user2", "user2@email.com"), 2, Arrays.asList("rev" + (j - 1)))));
      List<FileChange> fileChanges = new ArrayList<>();
      for (int k = 0; k < filesPerCommit; k++) {
        fileChanges.add(new FileChange(ChangeType.Modified, "bbbbbbbbbbbbbbbbbbbbbbb" + k, "aaaaaaaaaaaaaaaaaa" + k, EntryType.File));
      }
      changes.add(new CommitChange("rev" + j, "rev" + (j - 1), false, fileChanges));
    }
    Mockito.doReturn(commitList).when(myGitRepoApi).listCommits(Mockito.anyList(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyBoolean(), Mockito.anyBoolean());
    Mockito.doReturn(changes).when(myGitRepoApi).listChanges(Mockito.anyList(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyInt());

    long start = System.currentTimeMillis();
    for (int i = 0; i < n; i++) {
      myCollectChangesPolicy.collectChanges(myVcsRootInstance,
                                            RepositoryStateData.createVersionState("master", fromState),
                                            RepositoryStateData.createVersionState("master", toState),
                                            new CheckoutRules(""));
    }

    long total = System.currentTimeMillis() - start;
    System.out.println("Total time: " + total + " ms. Average time: " + total / n + " ms");
  }
}
