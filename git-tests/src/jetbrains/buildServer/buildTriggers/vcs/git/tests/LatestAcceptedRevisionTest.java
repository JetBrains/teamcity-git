/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

import jetbrains.buildServer.TestLogger;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsSupport;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.RepositoryStateData;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.apache.log4j.Level;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitSupportBuilder.gitSupport;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static org.assertj.core.api.BDDAssertions.then;

@Test
public class LatestAcceptedRevisionTest extends BaseRemoteRepositoryTest {
  private TestLogger myLogger;
  private PluginConfigBuilder myConfig;
  private File myRepo;

  public LatestAcceptedRevisionTest() {
    super("repo_for_checkout_rules");
  }


  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myLogger = new TestLogger();
    myLogger.setLogLevel(Level.INFO);
    myConfig = new PluginConfigBuilder(new ServerPaths(myTempFiles.createTempDir().getAbsolutePath()));
    myRepo = getRemoteRepositoryDir("repo_for_checkout_rules");
  }

  public void test_include_all_exclude_all() throws IOException, VcsException {
    GitVcsSupport support = git();
    VcsRoot root = vcsRoot().withFetchUrl(myRepo).build();

    ensureFetchPerformed(support, root, "refs/heads/master", "bbdf67dc5d1d2fa1ce08a0c7db7371f14cd918bf");
    String rev = support.getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:."),
                                                                                            "bbdf67dc5d1d2fa1ce08a0c7db7371f14cd918bf",
                                                                                            Collections.emptyList());
    then(rev).isEqualTo("bbdf67dc5d1d2fa1ce08a0c7db7371f14cd918bf");

    rev = support.getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:."),
                                                                                     "bbdf67dc5d1d2fa1ce08a0c7db7371f14cd918bf",
                                                                                     Collections.singleton("bbdf67dc5d1d2fa1ce08a0c7db7371f14cd918bf"));
    then(rev).isNull();

    rev = support.getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("-:."),
                                                                                     "bbdf67dc5d1d2fa1ce08a0c7db7371f14cd918bf",
                                                                                     Collections.emptyList());
    then(rev).isNull();
  }

  private void ensureFetchPerformed(@NotNull final GitVcsSupport support, @NotNull final VcsRoot root, @NotNull String branchName, @NotNull String branchTipRevision) throws VcsException {
    support.getCollectChangesPolicy().collectChanges(root, RepositoryStateData.createVersionState(branchName, Collections.emptyMap()),
                                                     RepositoryStateData.createVersionState(branchName, Collections.singletonMap(branchName, branchTipRevision)),
                                                     CheckoutRules.DEFAULT);
  }

  public void test_search_by_path() throws IOException, VcsException {
    GitVcsSupport support = git();
    VcsRoot root = vcsRoot().withFetchUrl(myRepo).build();

    ensureFetchPerformed(support, root, "refs/heads/br1", "d5a9a3c51fd53b1aec5e3746f521dc78355d7c78");
    String rev = support.getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:src"),
                                                                                            "d5a9a3c51fd53b1aec5e3746f521dc78355d7c78",
                                                                                            Collections.emptyList());
    then(rev).isEqualTo("a4bc5909156143a5590adadb2c20eaf71f2a3f8f");

    rev = support.getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:test"),
                                                                                     "bb6ab65d23fa0ffbaa61d44c8241f127cf0f323f",
                                                                                     Collections.emptyList());
    then(rev).isEqualTo("b265fd1608fe17f912a031312e1efc758c4e8a35");
  }

  public void test_merges() throws IOException, VcsException {
    GitVcsSupport support = git();
    VcsRoot root = vcsRoot().withFetchUrl(myRepo).build();

    ensureFetchPerformed(support, root, "refs/heads/master", "b304522994197be5f336d58cc34edc11cbda095e");
    String rev = support.getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:src"),
                                                                                            "b304522994197be5f336d58cc34edc11cbda095e",
                                                                                            Collections.emptyList());
    then(rev).isEqualTo("3dada99f39b112fe1de4da19a6ed5113f0035f21");

    rev = support.getCollectChangesPolicy().getLatestRevisionAcceptedByCheckoutRules(root, new CheckoutRules("+:test"),
                                                                                     "b304522994197be5f336d58cc34edc11cbda095e",
                                                                                     Collections.emptyList());
    then(rev).isEqualTo("3dada99f39b112fe1de4da19a6ed5113f0035f21");
  }



  @NotNull
  private GitVcsSupport git() {
    return gitSupport().withPluginConfig(myConfig).build();
  }
}
