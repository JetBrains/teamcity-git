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

import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitSupportBuilder.gitSupport;

/**
 * @author dmitry.neverov
 */
public class GitUrlSupportTest extends BaseTestCase {

  private TempFiles myTempFiles = new TempFiles();
  private GitUrlSupport myUrlSupport;
  private MirrorManager myMirrorManager;

  @BeforeMethod
  public void setUp() throws IOException {
    ServerPaths paths = new ServerPaths(myTempFiles.createTempDir().getAbsolutePath());
    PluginConfig config = new PluginConfigBuilder(paths).build();
    myMirrorManager = new MirrorManagerImpl(config, new HashCalculatorImpl());

    GitVcsSupport vcsSupport = gitSupport().withServerPaths(paths).build();
    myUrlSupport = new GitUrlSupport(vcsSupport);
  }

  @AfterMethod
  public void tearDown() {
    myTempFiles.cleanup();
  }

  @Test
  public void test_convert() throws MalformedURLException, VcsException, URISyntaxException {
    List<String> urls = Arrays.asList("scm:git:git://github.com/path_to_repository",
                                      "scm:git:http://github.com/path_to_repository",
                                      "scm:git:https://github.com/path_to_repository",
                                      "scm:git:ssh://github.com/path_to_repository",
                                      "scm:git:file://localhost/path_to_repository",
                                      "scm:git:ssh://github.com/nd/regex.git/pom.xml");

    for (String url : urls) {
      MavenVcsUrl vcsUrl = new MavenVcsUrl(url);
      GitVcsRoot root = toGitRoot(vcsUrl);
      assertEquals(new URIish(vcsUrl.getProviderSpecificPart()), root.getRepositoryFetchURL());
      checkAuthMethod(vcsUrl, root);
    }

    String user = "user";
    String pass = "pass";
    for (String url : urls) {
      MavenVcsUrl vcsUrl = new MavenVcsUrl(url, new Credentials(user, pass));
      GitVcsRoot s = toGitRoot(vcsUrl);
      checkAuthMethod(vcsUrl, s);
    }
  }


  @Test
  public void should_not_throw_exception_when_url_is_from_other_provider() throws MalformedURLException, VcsException {
    VcsUrl url = new VcsUrl("scm:svn:ssh://svn.repo.com/path_to_repository");
    assertNull(myUrlSupport.convertToVcsRootProperties(url));

    url = new VcsUrl("svn://svn.repo.com/path_to_repository");
    assertNull(myUrlSupport.convertToVcsRootProperties(url));
  }


  @Test
  public void convert_scp_like_syntax() throws Exception {
    MavenVcsUrl url = new MavenVcsUrl("scm:git:git@github.com:user/repo.git");
    GitVcsRoot root = toGitRoot(url);
    assertEquals(new URIish(url.getProviderSpecificPart()), root.getRepositoryFetchURL());
    assertEquals(AuthenticationMethod.PRIVATE_KEY_DEFAULT, root.getAuthSettings().getAuthMethod());
    assertEquals("git", root.getAuthSettings().toMap().get(Constants.USERNAME));
  }


  @Test
  public void convert_scp_like_syntax_with_credentials() throws Exception {
    VcsUrl url = new VcsUrl("scm:git:git@github.com:user/repo.git", new Credentials("user", "pass"));
    GitVcsRoot root = toGitRoot(url);
    assertEquals(new URIish("user@github.com:user/repo.git"), root.getRepositoryFetchURL());
    assertEquals(AuthenticationMethod.PRIVATE_KEY_DEFAULT, root.getAuthSettings().getAuthMethod());
    assertEquals("user", root.getAuthSettings().toMap().get(Constants.USERNAME));
    assertNull(root.getAuthSettings().toMap().get(Constants.PASSWORD));

    assertEquals(root.getProperties(),
                 myUrlSupport.convertToVcsRootProperties(new VcsUrl("git@github.com:user/repo.git", new Credentials("user", "pass"))));
  }

  @Test
  public void http_protocol() throws Exception {
    VcsUrl url = new VcsUrl("http://git.jetbrains.org/teamcity/git-plugin.git");
    GitVcsRoot root = toGitRoot(url);

    assertEquals("http://git.jetbrains.org/teamcity/git-plugin.git", root.getProperty(Constants.FETCH_URL));
    assertEquals("refs/heads/master", root.getProperty(Constants.BRANCH_NAME));
    assertEquals(AuthenticationMethod.ANONYMOUS.name(), root.getProperty(Constants.AUTH_METHOD));
  }

  @Test
  public void http_protocol_with_credentials() throws Exception {
    VcsUrl url = new VcsUrl("http://git.jetbrains.org/teamcity/git-plugin.git", new Credentials("user1", "pwd1"));
    GitVcsRoot root = toGitRoot(url);

    assertEquals("http://git.jetbrains.org/teamcity/git-plugin.git", root.getProperty(Constants.FETCH_URL));
    assertEquals("refs/heads/master", root.getProperty(Constants.BRANCH_NAME));
    assertEquals(AuthenticationMethod.PASSWORD.name(), root.getProperty(Constants.AUTH_METHOD));
    assertEquals("user1", root.getProperty(Constants.USERNAME));
    assertEquals("pwd1", root.getProperty(Constants.PASSWORD));
  }

  @Test
  public void http_protocol_svn_repo() throws Exception {
    VcsUrl url = new VcsUrl("http://svn.jetbrains.org/teamcity/plugins/xml-tests-reporting/trunk/");
    assertNull(myUrlSupport.convertToVcsRootProperties(url));
  }

  @Test
  public void should_not_use_private_key_for_local_repository() throws VcsException {
    VcsUrl url = new VcsUrl("/home/user/repository.git");
    GitVcsRoot root = toGitRoot(url);
    assertEquals(AuthenticationMethod.ANONYMOUS.name(), root.getProperty(Constants.AUTH_METHOD));
  }


  @Test
  public void should_use_username_from_url() throws Exception {
    VcsUrl url = new VcsUrl("scm:git:http://teamcity@acme.com/repository.git");
    GitVcsRoot root = toGitRoot(url);
    assertEquals("teamcity", root.getProperty(Constants.USERNAME));
  }


  private void checkAuthMethod(MavenVcsUrl url, GitVcsRoot root) {
    if (url.getProviderSpecificPart().startsWith("ssh")) {
      assertEquals(AuthenticationMethod.PRIVATE_KEY_DEFAULT, root.getAuthSettings().getAuthMethod());
      assertTrue(root.getAuthSettings().isIgnoreKnownHosts());
      if (url.getCredentials() != null) {
        assertEquals(url.getCredentials().getUsername(), root.getAuthSettings().toMap().get(Constants.USERNAME));
      }
      assertNull(root.getAuthSettings().toMap().get(Constants.PASSWORD));
    } else {
      if (url.getCredentials() != null &&
          !StringUtil.isEmptyOrSpaces(url.getCredentials().getUsername()) &&
          !StringUtil.isEmptyOrSpaces(url.getCredentials().getPassword())) {
        assertEquals(AuthenticationMethod.PASSWORD, root.getAuthSettings().getAuthMethod());
        assertEquals(url.getCredentials().getUsername(), root.getAuthSettings().toMap().get(Constants.USERNAME));
        assertEquals(url.getCredentials().getPassword(), root.getAuthSettings().toMap().get(Constants.PASSWORD));
      } else {
        assertEquals(AuthenticationMethod.ANONYMOUS, root.getAuthSettings().getAuthMethod());
        assertNull(root.getAuthSettings().toMap().get(Constants.USERNAME));
        assertNull(root.getAuthSettings().toMap().get(Constants.PASSWORD));
      }
    }
  }

  private GitVcsRoot toGitRoot(@NotNull VcsUrl url) throws VcsException {
    Map<String, String> properties = myUrlSupport.convertToVcsRootProperties(url);
    VcsRootImpl myRoot = new VcsRootImpl(1, properties);
    return new GitVcsRoot(myMirrorManager, myRoot);
  }
}
