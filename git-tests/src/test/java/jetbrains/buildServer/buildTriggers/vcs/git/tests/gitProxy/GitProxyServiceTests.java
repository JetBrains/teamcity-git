package jetbrains.buildServer.buildTriggers.vcs.git.tests.gitProxy;

import com.intellij.openapi.util.Pair;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.buildTriggers.vcs.git.gitProxy.*;
import jetbrains.buildServer.buildTriggers.vcs.git.gitProxy.data.*;
import jetbrains.buildServer.buildTriggers.vcs.git.gitProxy.data.ChangeType;
import jetbrains.buildServer.serverSide.MockParameter;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.serverSide.parameters.ParameterFactory;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.impl.SVcsRootImpl;
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
    Mockito.doReturn(commitList).when(myGitRepoApi).listCommits(Arrays.asList(new Pair<>("id-range", Arrays.asList( "^rev2", "^rev1", "rev5"))), 0, 1000, false, true);
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
    Mockito.doReturn(commitList).when(myGitRepoApi).listCommits(Arrays.asList(new Pair<>("id-range", Arrays.asList( "^rev4", "rev5"))), 0, 1000, false, true);
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
    Mockito.doReturn(commitList).when(myGitRepoApi).listCommits(Arrays.asList(new Pair<>("id-range", Arrays.asList( "^rev4", "rev5"))), 0, 1000, false, true);
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

    Mockito.doReturn(commitList).when(myGitRepoApi).listCommits(Arrays.asList(new Pair<>("id-range", Arrays.asList( "^rev2", "^rev1", "rev5"))), 0, 1000, false, true);
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
}
