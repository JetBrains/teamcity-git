/*
 * Copyright 2000-2018 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.log.LogInitializer;
import jetbrains.buildServer.serverSide.BasePropertiesModel;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.patches.PatchBuilderImpl;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.jetbrains.annotations.NotNull;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.testng.Assert;
import org.testng.annotations.*;

import static java.util.Arrays.asList;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitSupportBuilder.gitSupport;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.copyRepository;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.dataFile;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static org.assertj.core.api.BDDAssertions.then;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

@Test
public class MapFullPathTest {
  private TempFiles myTempFiles;
  private Mockery myContext;
  private File myRemoteRepositoryDir;
  private File myRemoteRepositoryDir2;
  private GitVcsSupport myGit;
  private GitMapFullPath myMapFullPath;
  private VcsRoot myRoot;
  private VcsRootEntry myRootEntry;
  private VcsRoot myRoot2;
  private VcsRootEntry myRootEntry2;
  private ServerPaths myServerPaths;

  @BeforeClass
  public void setUpClass() {
    LogInitializer.setUnitTest(true);
  }

  @BeforeMethod
  public void setUp() throws IOException {
    new TeamCityProperties() {{
      setModel(new BasePropertiesModel() {});
    }};
    myTempFiles = new TempFiles();
    myContext = new Mockery() {{
      setImposteriser(ClassImposteriser.INSTANCE);
    }};
    myRemoteRepositoryDir = myTempFiles.createTempDir();
    copyRepository(dataFile("repo_for_fetch.1"), myRemoteRepositoryDir);
    myRemoteRepositoryDir2 = myTempFiles.createTempDir();
    copyRepository(dataFile("repo.git"), myRemoteRepositoryDir2);

    myServerPaths = new ServerPaths(myTempFiles.createTempDir().getAbsolutePath());
    GitSupportBuilder gitBuilder = gitSupport().withServerPaths(myServerPaths);
    myGit = gitBuilder.build();
    myMapFullPath = gitBuilder.getMapFullPath();
    myRoot = vcsRoot().withId(1).withFetchUrl(myRemoteRepositoryDir.getAbsolutePath()).build();
    myRootEntry = new VcsRootEntry(myRoot, CheckoutRules.DEFAULT);
    myRoot2 = vcsRoot().withId(2).withFetchUrl(myRemoteRepositoryDir2.getAbsolutePath()).build();
    myRootEntry2 = new VcsRootEntry(myRoot2, CheckoutRules.DEFAULT);
  }

  @AfterMethod
  public void tearDown() {
    myTempFiles.cleanup();
  }


  @TestFor(issues = "TW-21185")
  @Test
  public void mapFullPath_should_report_up_to_date_info() throws Exception {
    RepositoryStateData state0 = RepositoryStateData.createSingleVersionState("a7274ca8e024d98c7d59874f19f21d26ee31d41d");
    RepositoryStateData state1 = myGit.getCurrentState(myRoot);
    myGit.getCollectChangesPolicy().collectChanges(myRoot, state0, state1, CheckoutRules.DEFAULT);

    Collection<String> paths = myGit.mapFullPath(myRootEntry, "d47dda159b27b9a8c4cee4ce98e4435eb5b17168||.");
    assertTrue(paths.isEmpty());
    paths = myGit.mapFullPath(myRootEntry, "252771029d6ac61aaa78d282d5818d210812a4e5||.");
    assertTrue(paths.isEmpty());

    remoteRepositoryUpdated();

    RepositoryStateData state2 = myGit.getCurrentState(myRoot);
    myGit.getCollectChangesPolicy().collectChanges(myRoot, state1, state2, CheckoutRules.DEFAULT);//now we have d47dda159b27b9a8c4cee4ce98e4435eb5b17168
    paths = myGit.mapFullPath(myRootEntry, "d47dda159b27b9a8c4cee4ce98e4435eb5b17168||.");
    assertFalse("mapFullPath returns outdated info", paths.isEmpty());
    paths = myGit.mapFullPath(myRootEntry, "252771029d6ac61aaa78d282d5818d210812a4e5||.");
    assertFalse("mapFullPath returns outdated info", paths.isEmpty());
  }


  public void should_not_do_unnecessary_commit_lookup_after_fetch() throws Exception {
    final String existingCommit = "a7274ca8e024d98c7d59874f19f21d26ee31d41d";
    final String nonExistingCommit = "abababababababababababababababababababab";

    final CommitLoader commitLoader = myContext.mock(CommitLoader.class);
    final RevCommit commit = myContext.mock(RevCommit.class);
    myMapFullPath.setCommitLoader(commitLoader);
    myContext.checking(new Expectations() {{
      //ask for existing commit only once:
      one(commitLoader).loadCommit(with(any(OperationContext.class)), with(any(GitVcsRoot.class)), with(existingCommit)); will(returnValue(commit));
      one(commitLoader).loadCommit(with(any(OperationContext.class)), with(any(GitVcsRoot.class)), with(nonExistingCommit)); will(throwException(new RevisionNotFoundException()));
    }});

    RepositoryStateData state0 = RepositoryStateData.createSingleVersionState("a7274ca8e024d98c7d59874f19f21d26ee31d41d");
    RepositoryStateData state1 = myGit.getCurrentState(myRoot);
    myGit.getCollectChangesPolicy().collectChanges(myRoot, state0, state1, CheckoutRules.DEFAULT);//fetch repository, so mapFullPath works

    OperationContext context = myGit.createContext(myRoot, "map full path");
    myMapFullPath.mapFullPath(context, myRootEntry, existingCommit + "||.");
    myMapFullPath.mapFullPath(context, myRootEntry, nonExistingCommit + "||.");

    remoteRepositoryUpdated();

    RepositoryStateData state2 = myGit.getCurrentState(myRoot);
    myGit.getCollectChangesPolicy().collectChanges(myRoot, state1, state2, CheckoutRules.DEFAULT);//this fetch should not cause new commit lookup
    myGit.mapFullPath(myRootEntry, existingCommit + "||.");
    myGit.mapFullPath(myRootEntry, nonExistingCommit + "||.");
    myContext.assertIsSatisfied();
  }


  public void should_not_do_unnecessary_commit_lookup_when_repository_does_not_have_hint_revision() throws Exception {
    //root1 contains the commit
    //root2 doesn't
    final CommitLoader commitLoader = myContext.mock(CommitLoader.class);
    final RevCommit commit = myContext.mock(RevCommit.class);
    myMapFullPath.setCommitLoader(commitLoader);

    final String hintCommit = "a7274ca8e024d98c7d59874f19f21d26ee31d41d";
    final String lastCommonCommit1 = "add81050184d3c818560bdd8839f50024c188586";
    final String lastCommonCommit2 = "d47dda159b27b9a8c4cee4ce98e4435eb5b17168";
    final String remoteUrl1 = myRemoteRepositoryDir.getAbsolutePath();
    final String remoteUrl2 = myRemoteRepositoryDir2.getAbsolutePath();
    myContext.checking(new Expectations() {{
      one(commitLoader).loadCommit(with(any(OperationContext.class)), with(rootWithUrl(remoteUrl1)), with(hintCommit)); will(returnValue(commit));
      //only single check for repository which doesn't contain a hint commit:
      one(commitLoader).loadCommit(with(any(OperationContext.class)), with(rootWithUrl(remoteUrl2)), with(hintCommit)); will(throwException(new RevisionNotFoundException()));
    }});

    String fullPath1 = hintCommit + "-" + lastCommonCommit1 + "||.";
    String fullPath2 = hintCommit + "-" + lastCommonCommit2 + "||.";

    OperationContext ctx = myGit.createContext(myRoot, "map full path");
    OperationContext ctx2 = myGit.createContext(myRoot2, "map full path");

    assertFalse(myMapFullPath.mapFullPath(ctx, myRootEntry, fullPath1).isEmpty());
    assertTrue(myMapFullPath.mapFullPath(ctx2, myRootEntry2, fullPath1).isEmpty());

    assertFalse(myMapFullPath.mapFullPath(ctx, myRootEntry, fullPath2).isEmpty());
    assertTrue(myMapFullPath.mapFullPath(ctx2, myRootEntry2, fullPath2).isEmpty());
    assertTrue(myMapFullPath.mapFullPath(ctx2, myRootEntry2, hintCommit + "-" + "any_other_commit").isEmpty());

    myContext.assertIsSatisfied();
  }


  public void bulk() throws Exception {
    //clone repository for myRoot and root3
    RepositoryStateData state0 = RepositoryStateData.createSingleVersionState("a7274ca8e024d98c7d59874f19f21d26ee31d41d");
    RepositoryStateData state1 = myGit.getCurrentState(myRoot);
    myGit.getCollectChangesPolicy().collectChanges(myRoot, state0, state1, CheckoutRules.DEFAULT);

    VcsRoot root3 = vcsRoot().withId(3).withFetchUrl(myRemoteRepositoryDir.getAbsolutePath()).build();//tracks same repo as myRoot1
    VcsRoot root4 = vcsRoot().withId(4).withFetchUrl(myRemoteRepositoryDir2.getAbsolutePath()).build();//tracks same repo as myRoot2

    List<Boolean> result = myGit.checkSuitable(asList(
      new VcsRootEntry(myRoot, new CheckoutRules("-:dir1")),
      new VcsRootEntry(myRoot, new CheckoutRules("+:dir1")),
      new VcsRootEntry(myRoot, new CheckoutRules("+:dir2")),
      new VcsRootEntry(myRoot2, new CheckoutRules("+:dir2")),
      new VcsRootEntry(root3, new CheckoutRules("+:dir1")),
      new VcsRootEntry(root4, new CheckoutRules("+:dir4")),
      new VcsRootEntry(root3, new CheckoutRules("+:dir5")),
      new VcsRootEntry(root4, new CheckoutRules("+:dir6"))
                                               ), asList(
      "a7274ca8e024d98c7d59874f19f21d26ee31d41d-add81050184d3c818560bdd8839f50024c188586||dir1/text1.txt",//affects root and root3
      "abababababababababababababababababababab||.")//affects no repo
    );

    then(result).containsExactly(false, true, false, false, true, false, false, false);
  }


  @DataProvider
  public Object[][] fetchAction() {
    return new Object[][]{
      new Object[] {new FetchAction("collect changes") {
        @Override
        public void run() throws Exception {
          RepositoryStateData state0 = RepositoryStateData.createSingleVersionState("9ef3a588831557040e81e4063ecf27d5442837f4");
          RepositoryStateData state1 = myGit.getCurrentState(myRoot);
          myGit.getCollectChangesPolicy().collectChanges(myRoot, state0, state1, CheckoutRules.DEFAULT);
        }
      }},

      new Object[] {new FetchAction("build patch") {
        @Override
        public void run() throws Exception {
          ByteArrayOutputStream output = new ByteArrayOutputStream();
          PatchBuilderImpl builder = new PatchBuilderImpl(output);
          myGit.buildPatch(myRoot, null, "add81050184d3c818560bdd8839f50024c188586", builder, CheckoutRules.DEFAULT);
        }
      }},

      new Object[] {new FetchAction("get content") {
        @Override
        public void run() throws Exception {
          myGit.getContentProvider().getContent("readme", myRoot, "add81050184d3c818560bdd8839f50024c188586");
        }
      }}
    };
  }

  private abstract class FetchAction {
    private final String myName;
    public FetchAction(@NotNull String name) {
      myName = name;
    }

    public abstract void run() throws Exception;

    @Override
    public String toString() {
      return myName;
    }
  }

  @Test(dataProvider = "fetchAction")
  public void should_reset_cached_revision_after_fetch(@NotNull FetchAction fetchAction) throws Exception {
    //run map full path before fetch
    final String existingCommit = "a7274ca8e024d98c7d59874f19f21d26ee31d41d";
    OperationContext context = myGit.createContext(myRoot, "map full path");

    assertFalse(context.getRepository().getObjectDatabase().has(ObjectId.fromString(existingCommit)));

    // after TW-72017 fix mapFullPath should perform fetch itself if it needs some commit
    //then(myMapFullPath.mapFullPath(context, myRootEntry, existingCommit + "||.")).isEmpty();//will not find revision in empty repo

    fetchAction.run();

    final GitSupportBuilder gitBuilder = gitSupport().withServerPaths(myServerPaths).withFetchCommand(new FetchCommand() {
      @Override
      public void fetch(@NotNull Repository db, @NotNull URIish fetchURI, @NotNull Collection<RefSpec> refspecs, @NotNull FetchSettings settings) throws IOException, VcsException {
        Assert.fail(); // no fetch expected
      }
    });
    gitBuilder.build();
    then(gitBuilder.getMapFullPath().mapFullPath(context, myRootEntry, existingCommit + "||.")).isNotEmpty();
  }


  private void remoteRepositoryUpdated() throws IOException {
    FileUtil.delete(myRemoteRepositoryDir);
    copyRepository(dataFile("repo_for_fetch.2"), myRemoteRepositoryDir);
  }


  private GitVcsRootMatcher rootWithUrl(@NotNull String remoteUrl) {
    return new GitVcsRootMatcher(remoteUrl);
  }

  private class GitVcsRootMatcher extends TypeSafeMatcher<GitVcsRoot> {
    private final String myExpectedUrl;
    private GitVcsRootMatcher(@NotNull String remoteRepositoryUrl) {
      myExpectedUrl = remoteRepositoryUrl;
    }

    @Override
    public boolean matchesSafely(GitVcsRoot root) {
      return myExpectedUrl.equals(root.getRepositoryFetchURL().toString());
    }

    public void describeTo(Description description) {
      description.appendText(" repository with remote url ").appendValue(myExpectedUrl);
    }
  }
}
