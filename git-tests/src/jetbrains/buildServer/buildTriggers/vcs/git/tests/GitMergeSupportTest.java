/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import jetbrains.buildServer.buildTriggers.vcs.git.GitMergeSupport;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsSupport;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.vcs.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitSupportBuilder.gitSupport;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static org.testng.AssertJUnit.assertTrue;

@Test
public class GitMergeSupportTest extends BaseRemoteRepositoryTest {

  private ServerPaths myPaths;

  public GitMergeSupportTest() {
    super("merge");
  }

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myPaths = new ServerPaths(myTempFiles.createTempDir().getAbsolutePath());
  }

  @AfterMethod
  public void tearDown() {
    super.tearDown();
  }


  public void should_fetch_missing_source_commit_for_merge() throws Exception {
    GitSupportBuilder builder = gitSupport().withServerPaths(myPaths);
    GitVcsSupport git = builder.build();
    MergeSupport mergeSupport = new GitMergeSupport(git, builder.getCommitLoader(), builder.getRepositoryManager(), builder.getTransportFactory());
    VcsRoot root = vcsRoot().withFetchUrl(getRemoteRepositoryDir("merge")).build();
    //cc69c22bd5d25779e58ad91008e685cbbe7f700a is not reachable from refs/heads/master
    MergeResult result = mergeSupport.merge(root, "cc69c22bd5d25779e58ad91008e685cbbe7f700a", "refs/heads/master", "merge", new MergeOptions());
    assertTrue(result.isSuccess());
  }

}
