package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import java.util.*;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RefSpec;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitSupportBuilder.gitSupport;
import static org.assertj.core.api.BDDAssertions.then;

@Test
public class FetchSettingsFactoryImplTest extends BaseTestCase {

  private FetchSettingsFactoryImpl myFactory;
  private PluginConfigBuilder myPluginConfigBuilder;
  private OperationContext myContext;
  private static final TempFiles myTemp = new TempFiles();


  @BeforeMethod(alwaysRun = true)
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFactory = new FetchSettingsFactoryImpl();
    myPluginConfigBuilder = new PluginConfigBuilder(new ServerPaths(myTemp.createTempDir().getAbsolutePath()));
    GitVcsSupport git = gitSupport().withPluginConfig(myPluginConfigBuilder).build();
    myContext = git.createContext(VcsRootBuilder.vcsRoot().withFetchUrl("https://mygit.com/owner/repo").build(), "fetch");
  }

  public void refspecs_all_branches() throws VcsException {
    FetchSettings settings = myFactory.getFetchSettings(myContext, true);
    then(settings.getRefSpecs()).containsExactly(new RefSpec("+refs/*:refs/*"));
    then(settings.getFetchMode()).isEqualTo(FetchSettings.FetchMode.FETCH_ALL_REFS);
  }

  public void refspecs_specific_branches() throws VcsException {
    FetchSettings settings = myFactory.getFetchSettings(myContext,
                                                        Arrays.asList(commit("refs/heads/main", "1"), commit("refs/heads/dev", "2")),
                                                        Arrays.asList(commit("refs/heads/main", "1"), commit("refs/heads/dev", "2")),
                                                        new HashSet<String>(Arrays.asList("refs/heads/main", "refs/heads/dev")), false);
    then(settings.getRefSpecs()).containsExactly(new RefSpec("+refs/heads/main:refs/heads/main"), new RefSpec("+refs/heads/dev:refs/heads/dev"));
    then(settings.getFetchMode()).isEqualTo(FetchSettings.FetchMode.FETCH_REF_SPECS);
  }

  public void refspecs_selected_branches() throws VcsException {
    FetchSettings settings = myFactory.getFetchSettings(myContext,
                                                        Arrays.asList(commit("refs/heads/main", "1"), commit("refs/heads/release", "3")),
                                                        Arrays.asList(commit("refs/heads/main", "1"), commit("refs/heads/dev", "2"), commit("refs/heads/release", "3")),
                                                        new HashSet<String>(Arrays.asList("refs/heads/release", "refs/pull/1/head", "refs/heads/main", "refs/heads/dev")), false);
    then(settings.getRefSpecs()).containsExactly(new RefSpec("+refs/heads/main:refs/heads/main"), new RefSpec("+refs/heads/release:refs/heads/release"));
    then(settings.getFetchMode()).isEqualTo(FetchSettings.FetchMode.FETCH_REF_SPECS);
  }

  public void refspecs_selected_branches_missing_remote_branch() throws VcsException {
    FetchSettings settings = myFactory.getFetchSettings(myContext,
                                                        Arrays.asList(commit("refs/heads/main", "1"), commit("refs/heads/release", "3")),
                                                        Arrays.asList(commit("refs/heads/main", "1"), commit("refs/heads/release", "3")),
                                                        new HashSet<String>(Arrays.asList("refs/pull/1/head", "refs/heads/main", "refs/heads/dev")), false);
    then(settings.getRefSpecs()).containsExactly(new RefSpec("+refs/heads/main:refs/heads/main"));
    then(settings.getFetchMode()).isEqualTo(FetchSettings.FetchMode.FETCH_REF_SPECS);
  }

  public void refspecs_specific_branches_over_threshold() throws VcsException {
    myPluginConfigBuilder.setFetchRemoteBranchesThreshold(1);
    FetchSettings settings = myFactory.getFetchSettings(myContext,
                                                        Arrays.asList(commit("refs/heads/main", "1"), commit("refs/heads/dev", "2")),
                                                        Arrays.asList(commit("refs/heads/main", "1"), commit("refs/heads/dev", "2")),
                                                        new HashSet<String>(Arrays.asList("refs/heads/main", "refs/heads/dev")), false);
    then(settings.getRefSpecs()).containsExactly(new RefSpec("+refs/heads/main:refs/heads/main"), new RefSpec("+refs/heads/dev:refs/heads/dev"));
    then(settings.getFetchMode()).isEqualTo(FetchSettings.FetchMode.FETCH_ALL_REFS_EXCEPT_TAGS);
  }

  public void refspecs_specific_branches_over_threshold_tags() throws VcsException {
    myPluginConfigBuilder.setFetchRemoteBranchesThreshold(1);
    FetchSettings settings = myFactory.getFetchSettings(myContext,
                                                        Arrays.asList(commit("refs/heads/main", "1"), commit("refs/heads/dev", "2")),
                                                        Arrays.asList(commit("refs/heads/main", "1"), commit("refs/heads/dev", "2")),
                                                        new HashSet<String>(Arrays.asList("refs/heads/main", "refs/heads/dev")), true);
    then(settings.getRefSpecs()).containsExactly(new RefSpec("+refs/heads/main:refs/heads/main"), new RefSpec("+refs/heads/dev:refs/heads/dev"));
    then(settings.getFetchMode()).isEqualTo(FetchSettings.FetchMode.FETCH_ALL_REFS);
  }

  public void refspecs_many_branches_in_state() throws VcsException {
    int numBranches = 1000;
    Set<String> remoteRefs = new HashSet<>();
    List<RefCommit> branchRevisions = new ArrayList<>();
    for (int i=0; i<numBranches; i++) {
      String ref = "refs/pull/" + i + "/head";
      branchRevisions.add(commit(ref, String.valueOf(i)));
      remoteRefs.add(ref);
    }

    List<RefSpec> expectedRefSpecs = new ArrayList<>();
    List<RefCommit> refsToFetch = new ArrayList<>();
    for (int i=0; i<3; i++) {
      String ref = "refs/pull/" + i + "/head";
      refsToFetch.add(commit(ref, String.valueOf(i)));
      expectedRefSpecs.add(new RefSpec("+"+ref + ":" + ref));
    }

    FetchSettings settings = myFactory.getFetchSettings(myContext, refsToFetch, branchRevisions, remoteRefs, false);
    then(settings.getRefSpecs()).hasSize(expectedRefSpecs.size());
    for (RefSpec expected: expectedRefSpecs) {
      then(settings.getRefSpecs()).contains(expected);
    }
    then(settings.getFetchMode()).isEqualTo(FetchSettings.FetchMode.FETCH_REF_SPECS);

    myPluginConfigBuilder.setFetchRemoteBranchesThreshold(1);
    settings = myFactory.getFetchSettings(myContext, refsToFetch, branchRevisions, remoteRefs, false);
    then(settings.getFetchMode()).isEqualTo(FetchSettings.FetchMode.FETCH_ALL_REFS_EXCEPT_TAGS);
  }


  private RefCommit commit(@NotNull String ref, @NotNull String commit) {
    return new MockRefCommit(ref, commit, true);
  }

  private RefCommit commit(@NotNull String ref, @NotNull String commit, boolean tip) {
    return new MockRefCommit(ref, commit, tip);
  }

  private class MockRefCommit implements RefCommit {

    private final String myRef;
    private final String myCommit;
    private final boolean myTip;

    MockRefCommit(@NotNull String ref, @NotNull String commit, boolean tip) {
      myRef = ref;
      myCommit = commit;
      myTip = tip;
    }

    @NotNull
    @Override
    public String getRef() {
      return myRef;
    }

    @NotNull
    @Override
    public String getCommit() {
      return myCommit;
    }

    @Override
    public boolean isRefTip() {
      return myTip;
    }
  }
}
