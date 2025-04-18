package jetbrains.buildServer.buildTriggers.vcs.git.tests.gitProxy;

import com.intellij.openapi.util.Pair;
import java.util.*;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.buildTriggers.vcs.git.gitProxy.*;
import jetbrains.buildServer.buildTriggers.vcs.git.gitProxy.data.*;
import jetbrains.buildServer.buildTriggers.vcs.git.gitProxy.data.ChangeType;
import jetbrains.buildServer.serverSide.MockParameter;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.serverSide.parameters.ParameterFactory;
import jetbrains.buildServer.vcs.*;
import org.assertj.core.data.MapEntry;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.util.Util.map;
import static org.assertj.core.api.BDDAssertions.then;

public class GitProxyServiceTests extends BaseServerTestCase {

  private VcsRootInstance myVcsRootInstance;
  private GitRepoApi myGitRepoApi;
  private GitCollectChangesPolicy myCollectChangesPolicy;

  @BeforeMethod(alwaysRun = true)
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myFixture.registerVcsSupport("jetbrains.git");
    SVcsRoot root = myProject.createVcsRoot("jetbrains.git", "", map("url", "http://jetbrains.team/project/repo"));
    myBuildType.addVcsRoot(root);

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
    ServerPluginConfig serverPluginConfig = Mockito.mock(ServerPluginConfig.class);
    Mockito.doReturn(true).when(serverPluginConfig).treatMissingBranchTipAsRecoverableError();
    myCollectChangesPolicy = new GitCollectChangesPolicy(mockGitVcsSupport,
                                                         Mockito.mock(VcsOperationProgressProvider.class),
                                                         serverPluginConfig,
                                                         Mockito.mock(RepositoryManager.class),
                                                         Mockito.mock(CheckoutRulesLatestRevisionCache.class),
                                                         gitApiClientFactory,
                                                         factory,
                                                         new ChangesCollectorCache());
  }

  @Test
  public void testCreatesModificationDataCorrectly() throws Exception {
    CommitList commitList = new CommitList();
    commitList.commits = Arrays.asList(new Commit("rev5", new CommitInfo("rev5", "", "commit5", new Person("user", "user@email.com"), 1, new Person("user2", "user2@email.com"), 2,
                                                                         Arrays.asList("rev4"))),
                                       new Commit("rev4", new CommitInfo("rev4", "", "commit4", new Person("user", "user@email.com"), 1, new Person("user", "user@email.com"), 2,
                                                                         Arrays.asList("rev3"))),
                                       new Commit("rev3", new CommitInfo("rev3", "", "commit3", new Person("user", "user@email.com"), 1, new Person("user", "user@email.com"), 2,
                                                                         Arrays.asList("rev2")))
                                       );
    List<CommitChange> changes = Arrays.asList(new CommitChange("rev5", "rev4",false, Arrays.asList(new FileChange(ChangeType.Modified, "file1", "file1", EntryType.File),
                                                                                                                new FileChange(ChangeType.Modified, "file2", "file2", EntryType.File))),
                                               new CommitChange("rev4", "rev3", false, Arrays.asList(new FileChange(ChangeType.Added, "file2", "file2", EntryType.File))),
                                               new CommitChange("rev3", "rev2", false, Arrays.asList(new FileChange(ChangeType.Deleted, null, "file0", EntryType.File)))
    );
    Mockito.doReturn(commitList).when(myGitRepoApi).listCommits(Arrays.asList(new Pair<>("id-range", asSet(Arrays.asList( "^rev2", "^rev1", "rev5")))), 0, 1000, false, true);
    Mockito.doReturn(changes).when(myGitRepoApi).listChanges(Arrays.asList("rev5", "rev4", "rev3"), false, false, false, false, 10_000);

    List<ModificationData> changesData = myCollectChangesPolicy.collectChanges(myVcsRootInstance,
                                                                         RepositoryStateData.createVersionState("master", map("master", "rev1", "branch1", "rev2")),
                                                                         RepositoryStateData.createVersionState("master", map("master", "rev1", "branch1", "rev5")),
                                                                         new CheckoutRules(""));
    ModificationData rev5data = new ModificationData(new Date(1L * 1000),
                                                     Arrays.asList(new VcsChange(VcsChangeInfo.Type.CHANGED, null, "file1", "file1", "rev4", "rev5"),
                                                                                        new VcsChange(VcsChangeInfo.Type.CHANGED, null, "file2", "file2", "rev4", "rev5")),
                                                    "commit5",
                                                     "user",
                                                           myVcsRootInstance,
                                                           "rev5", "rev5");
    rev5data.setParentRevisions(Collections.singletonList("rev4"));
    rev5data.setAttribute("teamcity.commit.user", "user2");
    rev5data.setAttribute("teamcity.commit.time", "2000");

    ModificationData rev4data = new ModificationData(new Date(1L * 1000),
                                                     Arrays.asList(new VcsChange(VcsChangeInfo.Type.ADDED, null, "file2", "file2", "rev3", "rev4")),
                                                     "commit4",
                                                     "user",
                                                     myVcsRootInstance,
                                                     "rev4", "rev4");
    rev4data.setParentRevisions(Collections.singletonList("rev3"));
    rev4data.setAttribute("teamcity.commit.time", "2000");


    ModificationData rev3data = new ModificationData(new Date(1L * 1000),
                                                     Arrays.asList(new VcsChange(VcsChangeInfo.Type.REMOVED, null, "file0", "file0", "rev2", "rev3")),
                                                     "commit3",
                                                     "user",
                                                     myVcsRootInstance,
                                                     "rev3", "rev3");
    rev3data.setParentRevisions(Collections.singletonList("rev2"));
    rev3data.setAttribute("teamcity.commit.time", "2000");

    List<ModificationData> expected = Arrays.asList(rev5data, rev4data, rev3data);

    assertModificationDataEqual(expected, changesData);
  }

  @Test
  public void testCreatesModificationDataCorrectlyForMergeCommitWithChanges() throws VcsException {

    CommitList commitList = new CommitList();
    commitList.commits = Arrays.asList(new Commit("rev5", new CommitInfo("rev5", "", "merge rev4, rev3, rev2", new Person("user", "user@email.com"), 1, new Person("user", "user@email.com"), 2,
                                                                         Arrays.asList("rev4", "rev3", "rev2"))));
    List<CommitChange> changes = Arrays.asList(new CommitChange("rev5", "rev4",false, Arrays.asList(new FileChange(ChangeType.Added, "file1", "file1", EntryType.File),
                                                                                                    new FileChange(ChangeType.Modified, "file2", "file2", EntryType.File))),
                                               new CommitChange("rev5", "rev3", false, Arrays.asList(new FileChange(ChangeType.Modified, "file2", "file2", EntryType.File))),
                                               new CommitChange("rev5", "rev2", false, Arrays.asList(new FileChange(ChangeType.Modified, "file2", "file2", EntryType.File)))
    );
    Mockito.doReturn(commitList).when(myGitRepoApi).listCommits(Arrays.asList(new Pair<>("id-range", asSet(Arrays.asList( "^rev4", "rev5")))), 0, 1000, false, true);
    Mockito.doReturn(changes).when(myGitRepoApi).listChanges(Arrays.asList("rev5"), false, false, false, false, 10_000);

    List<ModificationData> changesData = myCollectChangesPolicy.collectChanges(myVcsRootInstance,
                                                                             RepositoryStateData.createVersionState("master", map("master", "rev4")),
                                                                             RepositoryStateData.createVersionState("master", map("master", "rev5")),
                                                                             new CheckoutRules(""));
    ModificationData rev5data = new ModificationData(new Date(1L * 1000),
                                                     Arrays.asList(new VcsChange(VcsChangeInfo.Type.CHANGED, null, "file2", "file2", "rev4", "rev5")),
                                                     "merge rev4, rev3, rev2",
                                                     "user",
                                                     myVcsRootInstance,
                                                     "rev5", "rev5");
    rev5data.setParentRevisions(Arrays.asList("rev4", "rev3", "rev2"));
    rev5data.setAttribute("teamcity.commit.time", "2000");
    rev5data.setAttribute("teamcity.transient.changedFiles.rev4", "file1\nfile2");
    rev5data.setAttribute("teamcity.transient.changedFiles.rev3", "file2");
    rev5data.setAttribute("teamcity.transient.changedFiles.rev2", "file2");

    List<ModificationData> expected = Arrays.asList(rev5data);

    assertModificationDataEqual(expected, changesData);
  }

  @Test
  public void testCreatesModificationDataCorrectlyForMergeCommitWithoutChanges() throws VcsException {

    CommitList commitList = new CommitList();
    commitList.commits = Arrays.asList(new Commit("rev5", new CommitInfo("rev5", "", "merge rev4, rev3, rev2", new Person("user", "user@email.com"), 1, new Person("user", "user@email.com"), 2,
                                                                         Arrays.asList("rev4", "rev3", "rev2"))));
    List<CommitChange> changes = Arrays.asList(new CommitChange("rev5", "rev4",false, Arrays.asList(new FileChange(ChangeType.Added, "file1", "file1", EntryType.File),
                                                                                                    new FileChange(ChangeType.Modified, "file2", "file2", EntryType.File))),
                                               new CommitChange("rev5", "rev3", false, Arrays.asList(new FileChange(ChangeType.Modified, "file2", "file2", EntryType.File))),
                                               new CommitChange("rev5", "rev2", false, Arrays.asList(new FileChange(ChangeType.Modified, "file3", "file3", EntryType.File)))
    );
    Mockito.doReturn(commitList).when(myGitRepoApi).listCommits(Arrays.asList(new Pair<>("id-range", asSet(Arrays.asList( "^rev4", "rev5")))), 0, 1000, false, true);
    Mockito.doReturn(changes).when(myGitRepoApi).listChanges(Arrays.asList("rev5"), false, false, false, false, 10_000);

    List<ModificationData> changesData = myCollectChangesPolicy.collectChanges(myVcsRootInstance,
                                                                               RepositoryStateData.createVersionState("master", map("master", "rev4")),
                                                                               RepositoryStateData.createVersionState("master", map("master", "rev5")),
                                                                               new CheckoutRules(""));
    ModificationData rev5data = new ModificationData(new Date(1L * 1000),
                                                     Arrays.asList(),
                                                     "merge rev4, rev3, rev2",
                                                     "user",
                                                     myVcsRootInstance,
                                                     "rev5", "rev5");
    rev5data.setParentRevisions(Arrays.asList("rev4", "rev3", "rev2"));
    rev5data.setAttribute("teamcity.commit.time", "2000");
    rev5data.setAttribute("teamcity.transient.changedFiles.rev4", "file1\nfile2");
    rev5data.setAttribute("teamcity.transient.changedFiles.rev3", "file2");
    rev5data.setAttribute("teamcity.transient.changedFiles.rev2", "file3");

    List<ModificationData> expected = Arrays.asList(rev5data);

    assertModificationDataEqual(expected, changesData);
  }

  @Test
  public void testShouldReuseChangesCollectionResultWithEnabledCache() throws VcsException {
    setInternalProperty(GitProxyChangesCollector.GIT_PROXY_CACHING_PROPERTY, true);
    CommitList commitList = new CommitList();
    commitList.commits = Arrays.asList(new Commit("rev5", new CommitInfo("rev5", "", "commit5", new Person("user", "user@email.com"), 1, new Person("user2", "user2@email.com"), 2,
                                                                         Arrays.asList("rev4"))),
                                       new Commit("rev4", new CommitInfo("rev4", "", "commit4", new Person("user", "user@email.com"), 1, new Person("user", "user@email.com"), 2,
                                                                         Arrays.asList("rev3"))),
                                       new Commit("rev3", new CommitInfo("rev3", "", "commit3", new Person("user", "user@email.com"), 1, new Person("user", "user@email.com"), 2,
                                                                         Arrays.asList("rev2")))
    );
    List<CommitChange> changes = Arrays.asList(new CommitChange("rev5", "rev4",false, Arrays.asList(new FileChange(ChangeType.Modified, "file1", "file1", EntryType.File),
                                                                                                    new FileChange(ChangeType.Modified, "file2", "file2", EntryType.File))),
                                               new CommitChange("rev4", "rev3", false, Arrays.asList(new FileChange(ChangeType.Added, "file2", "file2", EntryType.File))),
                                               new CommitChange("rev3", "rev2", false, Arrays.asList(new FileChange(ChangeType.Deleted, null, "file0", EntryType.File)))
    );

    Mockito.doReturn(commitList).when(myGitRepoApi).listCommits(Arrays.asList(new Pair<>("id-range", asSet(Arrays.asList( "^rev2", "^rev1", "rev5")))), 0, 1000, false, true);
    Mockito.doReturn(changes).when(myGitRepoApi).listChanges(Arrays.asList("rev5", "rev4", "rev3"), false, false, false, false, 10_000);

    List<ModificationData> changesData = myCollectChangesPolicy.collectChanges(myVcsRootInstance,
                                                                               RepositoryStateData.createVersionState("master", map("master", "rev1", "branch1", "rev2")),
                                                                               RepositoryStateData.createVersionState("master", map("master", "rev1", "branch1", "rev5")),
                                                                               new CheckoutRules(""));

    List<ModificationData> changesData2 = myCollectChangesPolicy.collectChanges(myVcsRootInstance,
                                                                               RepositoryStateData.createVersionState("master", map("master", "rev1", "branch1", "rev2")),
                                                                               RepositoryStateData.createVersionState("master", map("master", "rev1", "branch1", "rev5")),
                                                                               new CheckoutRules(""));
    assertModificationDataEqual(changesData, changesData2);
    Mockito.verify(myGitRepoApi, Mockito.times(1)).listChanges(Mockito.any(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyBoolean(), Mockito.anyInt());
    Mockito.verify(myGitRepoApi, Mockito.times(1)).listCommits(Mockito.any(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyBoolean(), Mockito.anyBoolean());

  }

  @Test
  public void testShouldReturnOnlyBranchesWithoutTipRevision() throws VcsException {
    CommitList commitList = new CommitList();
    commitList.commits = Arrays.asList(new Commit("branch2_rev2", new CommitInfo("branch2_rev2", "", "branch2_commit2", new Person("user", "user@email.com"), 1, new Person("user", "user@email.com"), 2, Arrays.asList("branch2_rev1"))),
                                       new Commit("branch2_rev1", new CommitInfo("branch2_rev1", "", "branch2_commit1", new Person("user", "user@email.com"), 1, new Person("user", "user@email.com"), 2, Arrays.asList("rev0")))
    );
    List<CommitChange> changes = Arrays.asList(new CommitChange("branch2_rev2", "branch2_rev1",false, Arrays.asList(new FileChange(ChangeType.Modified, "file1", "file1", EntryType.File),
                                                                                                    new FileChange(ChangeType.Modified, "file2", "file2", EntryType.File))),
                                               new CommitChange("branch2_rev1", "rev0", false, Arrays.asList(new FileChange(ChangeType.Added, "file2", "file2", EntryType.File)))
    );
    CommitList existingCommitsResponse = new CommitList();
    existingCommitsResponse.commits = Arrays.asList(new Commit("branch3_rev2", null));

    Mockito.doReturn(commitList).when(myGitRepoApi).listCommits(Arrays.asList(new Pair<>("id-range", asSet(Arrays.asList( "^rev0", "branch1_rev2", "branch2_rev2", "branch3_rev2")))), 0, 1000, false, true);
    Mockito.doReturn(existingCommitsResponse).when(myGitRepoApi).listCommits(Arrays.asList(new Pair<>("id", Arrays.asList( "branch1_rev2", "branch3_rev2"))), 0, Integer.MAX_VALUE, false, false);
    Mockito.doReturn(changes).when(myGitRepoApi).listChanges(Arrays.asList("branch2_rev2", "branch2_rev1"), false, false, false, false, 10_000);

    ChangesCollectionResult result = myCollectChangesPolicy.collectChangesExtended(null,
                                                                                   RepositoryStateData.createVersionState("master", map("master", "rev0")),
                                                                                   myVcsRootInstance,
                                                                                   RepositoryStateData.createVersionState("master", map("master", "rev0",
                                                                                                                      "branch1", "branch1_rev2",
                                                                                                                      "branch2", "branch2_rev2",
                                                                                                                      "branch3", "branch3_rev2",
                                                                                                                      "branch4", "branch1_rev2")),
                                                                                   new CheckoutRules(""));


    then(result.getCollectChangesResult()).hasSize(2);

    // branch2 had tip revision in the collection result, branch3 didn't have the revision in the result, but the tip still exists in the repo(which means it was just excluded by some other branch)
    then(result.getUpToDateState())
      .hasSize(2)
      .containsOnly(MapEntry.entry("branch1", null), MapEntry.entry("branch4", null));
  }

  @Test
  public void testShouldThrowExceptionIfMissingTipsAreNotAllowed() throws VcsException {
    setInternalProperty(GitProxyChangesCollector.ENABLE_JGIT_FALLBACK_CHANGES_COLLECTION, "false");
    CommitList commitList = new CommitList();
    commitList.commits = Arrays.asList(new Commit("branch2_rev2", new CommitInfo("branch2_rev2", "", "branch2_commit2", new Person("user", "user@email.com"), 1, new Person("user", "user@email.com"), 2, Arrays.asList("branch2_rev1"))),
                                       new Commit("branch2_rev1", new CommitInfo("branch2_rev1", "", "branch2_commit1", new Person("user", "user@email.com"), 1, new Person("user", "user@email.com"), 2, Arrays.asList("rev0")))
    );
    List<CommitChange> changes = Arrays.asList(new CommitChange("branch2_rev2", "branch2_rev1",false, Arrays.asList(new FileChange(ChangeType.Modified, "file1", "file1", EntryType.File),
                                                                                                                    new FileChange(ChangeType.Modified, "file2", "file2", EntryType.File))),
                                               new CommitChange("branch2_rev1", "rev0", false, Arrays.asList(new FileChange(ChangeType.Added, "file2", "file2", EntryType.File)))
    );
    CommitList existingCommitsResponse = new CommitList();
    existingCommitsResponse.commits = Arrays.asList(new Commit("branch3_rev2", null));

    Mockito.doReturn(commitList).when(myGitRepoApi).listCommits(Arrays.asList(new Pair<>("id-range", asSet(Arrays.asList( "^rev0", "branch1_rev2", "branch2_rev2", "branch3_rev2")))), 0, 1000, false, true);
    Mockito.doReturn(existingCommitsResponse).when(myGitRepoApi).listCommits(Arrays.asList(new Pair<>("id", Arrays.asList( "branch1_rev2", "branch3_rev2"))), 0, Integer.MAX_VALUE, false, false);
    Mockito.doReturn(changes).when(myGitRepoApi).listChanges(Arrays.asList("branch2_rev2", "branch2_rev1"), false, false, false, false, 10_000);

    try {
      myCollectChangesPolicy.collectChanges(myVcsRootInstance,
                                                    RepositoryStateData.createVersionState("master", map("master", "rev0")),
                                                    RepositoryStateData.createVersionState("master", map("master", "rev0",
                                                                                                "branch1", "branch1_rev2",
                                                                                                "branch2", "branch2_rev2",
                                                                                                "branch3", "branch3_rev2",
                                                                                                "branch4", "branch1_rev2")),
                                                    new CheckoutRules(""));
      fail("should throw VcsException");
    } catch (VcsException e) {
      then(e.getMessage()).contains("Revisions missing in the local repository", "branch1_rev2");
    }
  }

  private void assertModificationDataEqual(@NotNull List<ModificationData> expected, @NotNull List<ModificationData> actual) {
    then(actual).hasSameSizeAs(expected);
    for (int i = 0; i < expected.size(); i++) {
      ModificationData expectedData = expected.get(i);
      ModificationData actualData = actual.get(i);

      then(actualData.getParentRevisions()).isEqualTo(expectedData.getParentRevisions());
      then(actualData.getChanges()).isEqualTo(expectedData.getChanges());
      then(actualData.getDescription()).isEqualTo(expectedData.getDescription());
      then(actualData.getDisplayVersion()).isEqualTo(expectedData.getDisplayVersion());
      then(actualData.getChangeCount()).isEqualTo(expectedData.getChangeCount());
      then(actualData.getUserName()).isEqualTo(expectedData.getUserName());
      then(actualData.getVcsRoot()).isEqualTo(expectedData.getVcsRoot());
      then(actualData.getVcsDate()).isEqualTo(expectedData.getVcsDate());
      then(actualData.getVersion()).isEqualTo(expectedData.getVersion());
      then(actualData.getAttributes()).isEqualTo(expectedData.getAttributes());
    }
  }

  private static <T> LinkedHashSet<T> asSet(Collection<T> collection) {
    return new LinkedHashSet<>(collection);
  }
}
