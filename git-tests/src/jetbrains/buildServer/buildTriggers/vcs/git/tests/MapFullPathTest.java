/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.buildTriggers.vcs.git.GitMapFullPath;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsSupport;
import jetbrains.buildServer.buildTriggers.vcs.git.OperationContext;
import jetbrains.buildServer.log.Log4jFactory;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.RepositoryStateData;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.VcsRootEntry;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.jetbrains.annotations.NotNull;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.copyRepository;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.dataFile;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

@Test
public class MapFullPathTest {

  static {
    Logger.setFactory(new Log4jFactory());
  }

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

  @BeforeMethod
  public void setUp() throws IOException {
    myTempFiles = new TempFiles();
    myContext = new Mockery() {{
      setImposteriser(ClassImposteriser.INSTANCE);
    }};
    myRemoteRepositoryDir = myTempFiles.createTempDir();
    copyRepository(dataFile("repo_for_fetch.1"), myRemoteRepositoryDir);
    myRemoteRepositoryDir2 = myTempFiles.createTempDir();
    copyRepository(dataFile("repo.git"), myRemoteRepositoryDir2);

    ServerPaths paths = new ServerPaths(myTempFiles.createTempDir().getAbsolutePath());
    GitSupportBuilder gitBuilder = new GitSupportBuilder();
    myGit = gitBuilder.withServerPaths(paths).build();
    myMapFullPath = gitBuilder.getMapFullPath();
    myRoot = vcsRoot().withFetchUrl(myRemoteRepositoryDir.getAbsolutePath()).build();
    myRootEntry = new VcsRootEntry(myRoot, CheckoutRules.DEFAULT);
    myRoot2 = vcsRoot().withFetchUrl(myRemoteRepositoryDir2.getAbsolutePath()).build();
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

    remoteRepositoryUpdated();

    RepositoryStateData state2 = myGit.getCurrentState(myRoot);
    myGit.getCollectChangesPolicy().collectChanges(myRoot, state1, state2, CheckoutRules.DEFAULT);//now we have d47dda159b27b9a8c4cee4ce98e4435eb5b17168
    paths = myGit.mapFullPath(myRootEntry, "d47dda159b27b9a8c4cee4ce98e4435eb5b17168||.");
    assertFalse("mapFullPath returns outdated info", paths.isEmpty());
  }


  public void should_not_do_unnecessary_commit_lookup_after_fetch() throws Exception {
    final String existingCommit = "a7274ca8e024d98c7d59874f19f21d26ee31d41d";

    final GitVcsSupport git = myContext.mock(GitVcsSupport.class);
    final RevCommit commit = myContext.mock(RevCommit.class);
    myMapFullPath.setGitVcs(git);
    myContext.checking(new Expectations() {{
      //ask for existing commit only once:
      one(git).getCommit(with(any(Repository.class)), with(existingCommit)); will(returnValue(commit));
    }});

    RepositoryStateData state0 = RepositoryStateData.createSingleVersionState("a7274ca8e024d98c7d59874f19f21d26ee31d41d");
    RepositoryStateData state1 = myGit.getCurrentState(myRoot);
    myGit.getCollectChangesPolicy().collectChanges(myRoot, state0, state1, CheckoutRules.DEFAULT);//fetch repository, so mapFullPath works

    OperationContext context = myGit.createContext(myRoot, "map full path");
    myMapFullPath.mapFullPath(context, myRootEntry, "a7274ca8e024d98c7d59874f19f21d26ee31d41d||.");

    remoteRepositoryUpdated();

    RepositoryStateData state2 = myGit.getCurrentState(myRoot);
    myGit.getCollectChangesPolicy().collectChanges(myRoot, state1, state2, CheckoutRules.DEFAULT);//this fetch should not cause new commit lookup
    myGit.mapFullPath(myRootEntry, "a7274ca8e024d98c7d59874f19f21d26ee31d41d||.");
    myContext.assertIsSatisfied();
  }


  public void should_not_do_unnecessary_commit_lookup_when_repository_does_not_have_hint_revision() throws Exception {
    //root1 contains the commit
    //root2 doesn't
    final GitVcsSupport git = myContext.mock(GitVcsSupport.class);
    final RevCommit commit = myContext.mock(RevCommit.class);
    myMapFullPath.setGitVcs(git);

    final String hintCommit = "a7274ca8e024d98c7d59874f19f21d26ee31d41d";
    final String lastCommonCommit1 = "add81050184d3c818560bdd8839f50024c188586";
    final String lastCommonCommit2 = "d47dda159b27b9a8c4cee4ce98e4435eb5b17168";
    final String remoteUrl1 = myRemoteRepositoryDir.getAbsolutePath();
    final String remoteUrl2 = myRemoteRepositoryDir2.getAbsolutePath();
    myContext.checking(new Expectations() {{
      one(git).getCommit(with(repositoryWithUrl(remoteUrl1)), with(hintCommit)); will(returnValue(commit));
      one(git).getCommit(with(repositoryWithUrl(remoteUrl1)), with(lastCommonCommit1)); will(returnValue(commit));
      one(git).getCommit(with(repositoryWithUrl(remoteUrl1)), with(lastCommonCommit2)); will(returnValue(commit));
      //only single check for repository which doesn't contain a hint commit:
      one(git).getCommit(with(repositoryWithUrl(remoteUrl2)), with(hintCommit)); will(returnValue(null));
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


  private void remoteRepositoryUpdated() throws IOException {
    FileUtil.delete(myRemoteRepositoryDir);
    copyRepository(dataFile("repo_for_fetch.2"), myRemoteRepositoryDir);
  }


  private RepositoryMatcher repositoryWithUrl(@NotNull String remoteUrl) {
    return new RepositoryMatcher(remoteUrl);
  }

  private class RepositoryMatcher extends TypeSafeMatcher<Repository> {
    private final String myExpectedUrl;
    private RepositoryMatcher(@NotNull String remoteRepositoryUrl) {
      myExpectedUrl = remoteRepositoryUrl;
    }

    @Override
    public boolean matchesSafely(Repository repository) {
      String actualUrl = repository.getConfig().getString("teamcity", null, "remote");
      return myExpectedUrl.equals(actualUrl);
    }

    public void describeTo(Description description) {
      description.appendText(" repository with remote url ").appendValue(myExpectedUrl);
    }
  }
}
