/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.buildTriggers.vcs.git.VcsHostingRepo;
import jetbrains.buildServer.buildTriggers.vcs.git.WellKnownHostingsUtil;
import org.eclipse.jgit.transport.URIish;
import org.testng.annotations.Test;

import java.net.URISyntaxException;

@Test
public class WellKnownHostingsTest extends BaseTestCase {
  public void test_github_https() throws URISyntaxException {
    String url = "https://github.com/JetBrains/teamcity-commit-hooks.git";
    VcsHostingRepo repo = WellKnownHostingsUtil.getGitHubRepo(new URIish(url));
    assertNotNull(repo);
    assertEquals("https://github.com/JetBrains/teamcity-commit-hooks", repo.repositoryUrl());
    assertEquals("JetBrains", repo.owner());
    assertEquals("teamcity-commit-hooks", repo.repoName());
  }

  public void test_github_ssh() throws URISyntaxException {
    String url = "git@github.com:JetBrains/teamcity-commit-hooks.git";
    VcsHostingRepo repo = WellKnownHostingsUtil.getGitHubRepo(new URIish(url));
    assertNotNull(repo);
    assertEquals("https://github.com/JetBrains/teamcity-commit-hooks", repo.repositoryUrl());
    assertEquals("JetBrains", repo.owner());
    assertEquals("teamcity-commit-hooks", repo.repoName());
  }

  public void test_bitbucket_https() throws URISyntaxException {
    String url = "https://owner@bitbucket.org/owner/testgitrepo.git";
    VcsHostingRepo repo = WellKnownHostingsUtil.getBitbucketRepo(new URIish(url));
    assertNotNull(repo);
    assertEquals("https://bitbucket.org/owner/testgitrepo", repo.repositoryUrl());
    assertEquals("owner", repo.owner());
    assertEquals("testgitrepo", repo.repoName());
  }

  public void test_bitbucket_ssh() throws URISyntaxException {
    String url = "git@bitbucket.org:owner/testgitrepo.git";
    VcsHostingRepo repo = WellKnownHostingsUtil.getBitbucketRepo(new URIish(url));
    assertNotNull(repo);
    assertEquals("https://bitbucket.org/owner/testgitrepo", repo.repositoryUrl());
    assertEquals("owner", repo.owner());
    assertEquals("testgitrepo", repo.repoName());
  }

  public void test_vsts_https() throws URISyntaxException {
    String url = "https://spav5.visualstudio.com/_git/MyFirstProject";
    VcsHostingRepo repo = WellKnownHostingsUtil.getVSTSRepo(new URIish(url));
    assertNotNull(repo);
    assertEquals("https://spav5.visualstudio.com/_git/MyFirstProject", repo.repositoryUrl());
    assertEquals("spav5", repo.owner());
    assertEquals("MyFirstProject", repo.repoName());
  }

  public void test_vsts_ssh() throws URISyntaxException {
    String url = "ssh://spav5@spav5.visualstudio.com:22/_git/MyFirstProject";
    VcsHostingRepo repo = WellKnownHostingsUtil.getVSTSRepo(new URIish(url));
    assertNotNull(repo);
    assertEquals("https://spav5.visualstudio.com/_git/MyFirstProject", repo.repositoryUrl());
    assertEquals("spav5", repo.owner());
    assertEquals("MyFirstProject", repo.repoName());
  }
}
