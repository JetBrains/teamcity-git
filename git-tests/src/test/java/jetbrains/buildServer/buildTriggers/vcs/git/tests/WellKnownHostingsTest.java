

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

  public void test_gitlab_https() throws URISyntaxException {
    String url = "https://pavelsher@gitlab.com/pavelsher/testgitrepo.git";
    VcsHostingRepo repo = WellKnownHostingsUtil.getGitlabRepo(new URIish(url));
    assertNotNull(repo);
    assertEquals("https://gitlab.com/pavelsher/testgitrepo", repo.repositoryUrl());
    assertEquals("pavelsher", repo.owner());
    assertEquals("testgitrepo", repo.repoName());
  }

  public void test_gitlab_ssh() throws URISyntaxException {
    String url = "git@gitlab.com:pavelsher/testgitrepo.git";
    VcsHostingRepo repo = WellKnownHostingsUtil.getGitlabRepo(new URIish(url));
    assertNotNull(repo);
    assertEquals("https://gitlab.com/pavelsher/testgitrepo", repo.repositoryUrl());
    assertEquals("pavelsher", repo.owner());
    assertEquals("testgitrepo", repo.repoName());
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

  public void test_bitbucket_server_http_personal_repo() throws URISyntaxException {
    String url = "http://admin@localhost:7990/scm/~admin/personalrepo.git";
    VcsHostingRepo repo = WellKnownHostingsUtil.getBitbucketServerRepo(new URIish(url));
    assertNotNull(repo);
    assertEquals("http://localhost:7990/users/admin/repos/personalrepo", repo.repositoryUrl());
    assertEquals("admin", repo.owner());
    assertEquals("personalrepo", repo.repoName());
  }

  public void test_bitbucket_server_http_project_repo() throws URISyntaxException {
    String url = "https://admin@localhost/scm/TP/projectrepo.git";
    VcsHostingRepo repo = WellKnownHostingsUtil.getBitbucketServerRepo(new URIish(url));
    assertNotNull(repo);
    assertEquals("https://localhost/projects/TP/repos/projectrepo", repo.repositoryUrl());
    assertEquals("TP", repo.owner());
    assertEquals("projectrepo", repo.repoName());
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

  public void test_vsts_nested_project() throws URISyntaxException {
    String url = "https://spav5.visualstudio.com/MyProject/_git/MyFirstProject";
    VcsHostingRepo repo = WellKnownHostingsUtil.getVSTSRepo(new URIish(url));
    assertNotNull(repo);
    assertEquals("https://spav5.visualstudio.com/MyProject/_git/MyFirstProject", repo.repositoryUrl());
    assertEquals("spav5", repo.owner());
    assertEquals("MyFirstProject", repo.repoName());
  }
}