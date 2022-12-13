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

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitAgentVcsSupport;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.PluginConfigImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.tests.builders.AgentRunningBuildBuilder;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.Predicate;
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitVersionProvider.getGitPath;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static org.assertj.core.api.BDDAssertions.then;

@Test
public class AgentSideSparseCheckoutTest extends BaseRemoteRepositoryTest {

  private GitAgentVcsSupport myVcsSupport;
  private File myCheckoutDir;
  private VcsRoot myRoot;

  private Pattern quotesPattern = Pattern.compile("\\'[a-zA-Z0-9\\/\\s\\+=>:\\._]*\\'");

  public AgentSideSparseCheckoutTest() {
    super("repo.git");
  }


  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();

    myCheckoutDir = myTempFiles.createTempDir();
    myVcsSupport = new AgentSupportBuilder(myTempFiles).build();
    String pathToGit = getGitPath();
    myRoot = vcsRoot()
      .withAgentGitPath(pathToGit)
      .withFetchUrl(getRemoteRepositoryUrl("repo.git"))
      .withBranch("master")
      .build();
  }


  public void update_files_after_checkout_rules_change() throws Exception {
    String version = "465ad9f630e451b9f2b782ffb09804c6a98c4bb9";
    AgentRunningBuild build = runningBuild().sharedConfigParams(PluginConfigImpl.USE_SPARSE_CHECKOUT, "true")
      .withAgentConfiguration(myAgentConfiguration).build();
    CheckoutRules rules = new CheckoutRules("+:dir");
    myVcsSupport.updateSources(myRoot, rules, version, myCheckoutDir, build, false);
    then(myCheckoutDir.list()).contains("dir").doesNotContain("readme.txt");

    rules = new CheckoutRules("-:dir");
    myVcsSupport.updateSources(myRoot, rules, version, myCheckoutDir, build, false);
    then(myCheckoutDir.list()).doesNotContain("dir").contains("readme.txt");
  }


  public void update_files_after_switching_to_default_rules() throws Exception {
    String version = "465ad9f630e451b9f2b782ffb09804c6a98c4bb9";
    AgentRunningBuild build = runningBuild().sharedConfigParams(PluginConfigImpl.USE_SPARSE_CHECKOUT, "true")
      .withAgentConfiguration(myAgentConfiguration).build();
    CheckoutRules rules = new CheckoutRules("+:dir");
    myVcsSupport.updateSources(myRoot, rules, version, myCheckoutDir, build, false);
    then(myCheckoutDir.list()).contains("dir").doesNotContain("readme.txt");

    rules = CheckoutRules.DEFAULT;
    myVcsSupport.updateSources(myRoot, rules, version, myCheckoutDir, build, false);
    then(myCheckoutDir.list()).contains("dir", "readme.txt");
  }


  public void map_some_files_exclude_others() throws Exception {
    //Files at revision 465ad9f630e451b9f2b782ffb09804c6a98c4bb9:
    // dir/b.txt
    // dir/d.txt
    // dir/not_ignored_by_checkout_rules.txt
    // dir/q.txt
    // readme.txt

    checkRules("465ad9f630e451b9f2b782ffb09804c6a98c4bb9",
               rules("-:dir"),
               "readme.txt");

    checkRules("465ad9f630e451b9f2b782ffb09804c6a98c4bb9",
               rules("+:dir"),
               "dir/b.txt",
               "dir/d.txt",
               "dir/not_ignored_by_checkout_rules.txt",
               "dir/q.txt");

    checkRules("465ad9f630e451b9f2b782ffb09804c6a98c4bb9",
               rules("+:dir", "-:dir/q.txt"),
               "dir/b.txt",
               "dir/d.txt",
               "dir/not_ignored_by_checkout_rules.txt");

    checkRules("465ad9f630e451b9f2b782ffb09804c6a98c4bb9",
               rules("-:readme.txt", "-:dir/q.txt"),
               "dir/b.txt",
               "dir/d.txt",
               "dir/not_ignored_by_checkout_rules.txt");

    checkRules("465ad9f630e451b9f2b782ffb09804c6a98c4bb9",
               rules("+:.=>newDir", "-:dir"),
               "newDir/readme.txt");

    checkRules("465ad9f630e451b9f2b782ffb09804c6a98c4bb9",
               rules("+:dir=>newDir/dir",
                     "+:readme.txt=>newDir/readme.txt",
                     "-:dir/b.txt",
                     "-:dir/q.txt"),
               "newDir/dir/d.txt",
               "newDir/dir/not_ignored_by_checkout_rules.txt",
               "newDir/readme.txt");
  }


  @TestFor(issues = "TW-43433")
  public void include_all_rule() throws Exception {
    //content on revision 7574b5358ac09d61ec5cb792d4462230de1d00c2:
    // Folder1/f1
    // Folder1/f2
    // Folder2/SubFolder1/f3
    // Folder2/SubFolder1/f4
    // Folder2/SubFolder2/f5
    // Folder2/SubFolder2/f6

    myRoot = vcsRoot()
      .withAgentGitPath(getGitPath())
      .withFetchUrl(getRemoteRepositoryUrl("repo.git"))
      .withBranch("TW-43433")
      .build();

    checkRules("7574b5358ac09d61ec5cb792d4462230de1d00c2",
               rules("+:.",
                     "-:Folder2",
                     "+:Folder2/SubFolder1=>Folder2/SubFolder1"),
               "Folder1/f1",
               "Folder1/f2",
               "Folder2/SubFolder1/f3",
               "Folder2/SubFolder1/f4");
  }

  @TestFor(issues = "TW-53732")
  public void remap_directory_error() throws Exception {
    String version = "465ad9f630e451b9f2b782ffb09804c6a98c4bb9";
    AgentRunningBuild build = runningBuild().sharedConfigParams(PluginConfigImpl.USE_SPARSE_CHECKOUT, "true")
                                            .withAgentConfiguration(myAgentConfiguration).build();
    CheckoutRules rules = rules("dir1 => dir2", "+:dirA => dirB", "dirC/dirD => dirC/dirD");
    try {
      myVcsSupport.updateSources(myRoot, rules, version, myCheckoutDir, build, false);
      Assert.fail("Checking rules should be failed");
    } catch (VcsException ve) {
      Assert.assertEquals(extractRules(ve.getMessage()).get(0), "'dir1=>dir2'");
    }
  }

  @TestFor(issues = "TW-53732")
  public void remap_directory_error2() throws Exception {
    String version = "465ad9f630e451b9f2b782ffb09804c6a98c4bb9";
    AgentRunningBuild build = runningBuild().sharedConfigParams(PluginConfigImpl.USE_SPARSE_CHECKOUT, "true")
                                            .withAgentConfiguration(myAgentConfiguration).build();
    CheckoutRules rules = rules("dir1", "+:dirA => dirB", "dirC/dirD => dirC/dirD");
    try {
      myVcsSupport.updateSources(myRoot, rules, version, myCheckoutDir, build, false);
      Assert.fail("Checking rules should be failed");
    } catch (VcsException ve) {
      Assert.assertEquals(extractRules(ve.getMessage()).get(0), "'dirA=>dirB'");
    }
  }

  @TestFor(issues = "TW-53732")
  public void different_prefix_error() throws Exception {
    String version = "465ad9f630e451b9f2b782ffb09804c6a98c4bb9";
    AgentRunningBuild build = runningBuild().sharedConfigParams(PluginConfigImpl.USE_SPARSE_CHECKOUT, "true")
                                            .withAgentConfiguration(myAgentConfiguration).build();
    CheckoutRules rules = rules("dir1", "+:dirA => dirB/dirA", "dirC/dirD => dirC/dirD");
    try {
      myVcsSupport.updateSources(myRoot, rules, version, myCheckoutDir, build, false);
      Assert.fail("Checking rules should be failed");
    } catch (VcsException ve) {
      Assert.assertEquals(extractRules(ve.getMessage()).get(0), "'dirA=>dirB/dirA'");
    }
  }

  @TestFor(issues = "TW-77887")
  public void postfix_error() throws Exception {
    String version = "465ad9f630e451b9f2b782ffb09804c6a98c4bb9";
    AgentRunningBuild build = runningBuild().sharedConfigParams(PluginConfigImpl.USE_SPARSE_CHECKOUT, "true")
                                            .withAgentConfiguration(myAgentConfiguration).build();
    CheckoutRules rules = rules("dir1 => dirB/dir1", "+:dirA => dirB/dirA", "dirC/dirD => dirB/dirC/dirD/dirE");
    try {
      myVcsSupport.updateSources(myRoot, rules, version, myCheckoutDir, build, false);
      Assert.fail("Checking rules should be failed");
    } catch (VcsException ve) {
      Assert.assertEquals(extractRules(ve.getMessage()).get(0), "'dirC/dirD=>dirB/dirC/dirD/dirE'");
    }
  }

  @TestFor(issues = "TW-53732")
  public void prefix_substring_error() throws Exception {
    String version = "465ad9f630e451b9f2b782ffb09804c6a98c4bb9";
    AgentRunningBuild build = runningBuild().sharedConfigParams(PluginConfigImpl.USE_SPARSE_CHECKOUT, "true")
                                            .withAgentConfiguration(myAgentConfiguration).build();
    CheckoutRules rules = rules("dir1 => dirB/dir1", "+:dirA => dirB/dirA", "dirC/dirD => dirBx/dirC/dirD");
    try {
      myVcsSupport.updateSources(myRoot, rules, version, myCheckoutDir, build, false);
      Assert.fail("Checking rules should be failed");
    } catch (VcsException ve) {
      Assert.assertEquals(extractRules(ve.getMessage()).get(0), "'dirC/dirD=>dirBx/dirC/dirD'");
    }
  }

  @TestFor(issues = "TW-77887")
  public void postfix_substring_error() throws Exception {
    String version = "465ad9f630e451b9f2b782ffb09804c6a98c4bb9";
    AgentRunningBuild build = runningBuild().sharedConfigParams(PluginConfigImpl.USE_SPARSE_CHECKOUT, "true")
                                            .withAgentConfiguration(myAgentConfiguration).build();
    CheckoutRules rules = rules("dir1 => dirB/dir1", "+:dirA => dirB/dirA", "dirC/dirD => dirB/dirC/dirDx");
    try {
      myVcsSupport.updateSources(myRoot, rules, version, myCheckoutDir, build, false);
      Assert.fail("Checking rules should be failed");
    } catch (VcsException ve) {
      Assert.assertEquals(extractRules(ve.getMessage()).get(0), "'dirC/dirD=>dirB/dirC/dirDx'");
    }
  }

  @TestFor(issues = "TW-77887")
  public void postfix_ignore_substring_error() throws Exception {
    String version = "465ad9f630e451b9f2b782ffb09804c6a98c4bb9";
    AgentRunningBuild build = runningBuild().sharedConfigParams(PluginConfigImpl.USE_SPARSE_CHECKOUT, "true",
                                                                PluginConfigImpl.IGNORE_CHECKOUT_RULES_POSIFIX_CHECK_PARAMETER, "true")
                                            .withAgentConfiguration(myAgentConfiguration).build();
    CheckoutRules rules = rules("dir1 => dirB/dir1", "+:dirA => dirB/dirA", "dirC/dirD => dirB/dirC/dirD/dirE");
    try {
      myVcsSupport.updateSources(myRoot, rules, version, myCheckoutDir, build, false);
      Assert.assertTrue(true);
    } catch (VcsException ve) {
      Assert.fail("Should not be failed");
    }
  }

  private void checkRules(@NotNull String version, @NotNull CheckoutRules rules, String... files) throws VcsException {
    FileUtil.delete(myCheckoutDir);
    myCheckoutDir.mkdirs();
    AgentRunningBuild build = runningBuild().sharedConfigParams(PluginConfigImpl.USE_SPARSE_CHECKOUT, "true")
      .withAgentConfiguration(myAgentConfiguration).build();
    myVcsSupport.updateSources(myRoot, rules, version, myCheckoutDir, build, true);
    then(listFiles(myCheckoutDir)).containsOnly(files);
  }


  private CheckoutRules rules(String... rules) {
    return new CheckoutRules(asList(rules));
  }


  @NotNull
  private List<String> listFiles(@NotNull File dir) {
    List<String> result = new ArrayList<String>();
    Predicate<File> excludeDotGit = new Predicate<File>() {
      public boolean apply(final File item) {
        return !item.getAbsolutePath().contains(".git");
      }
    };
    FileUtil.listFilesRecursively(dir, "", false, Integer.MAX_VALUE, excludeDotGit, result);
    return result;
  }

  @NotNull
  public AgentRunningBuildBuilder runningBuild() {
    return AgentRunningBuildBuilder.runningBuild().addRoot(myRoot).withCheckoutDir(myCheckoutDir);
  }

  @NotNull
  public List<String> extractRules(String errorMessaage) {
    List<String> checkoutRules = new ArrayList<>();
    Matcher m = quotesPattern.matcher(errorMessaage);
    while (m.find()) {
      checkoutRules.add(m.group());
    }
    return  checkoutRules;
  }
}
