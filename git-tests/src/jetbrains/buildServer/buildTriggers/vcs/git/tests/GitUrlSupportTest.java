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

import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.*;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.ExtensionsProvider;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.oauth.TokenRefresher;
import jetbrains.buildServer.ssh.ServerSshKeyManager;
import jetbrains.buildServer.ssh.TeamCitySshKey;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jmock.Mock;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitSupportBuilder.gitSupport;

/**
 * @author dmitry.neverov
 */
public class GitUrlSupportTest extends BaseTestCase {

  private TempFiles myTempFiles = new TempFiles();
  private GitUrlSupport myUrlSupport;
  private MirrorManager myMirrorManager;
  private List<TeamCitySshKey> myTestKeys = new ArrayList<>();
  private Mock myProjectMock;
  private GitVcsSupport myGitVcsSupport;
  private Boolean myTestConnectionMocked;

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    ServerPaths paths = new ServerPaths(myTempFiles.createTempDir().getAbsolutePath());
    PluginConfig config = new PluginConfigBuilder(paths).build();
    myMirrorManager = new MirrorManagerImpl(config, new HashCalculatorImpl(), new RemoteRepositoryUrlInvestigatorImpl());

    myProjectMock = mock(SProject.class);
    Mock vrMock = mock(SVcsRoot.class);
    SVcsRoot svcsRoot = (SVcsRoot)vrMock.proxy();
    myProjectMock.stubs().method("createDummyVcsRoot").will(returnValue(svcsRoot));
    final SProject project = (SProject)myProjectMock.proxy();

    final Mock pmMock = mock(ProjectManager.class);
    pmMock.stubs().method("findProjectById").will(returnValue(project));
    ProjectManager pm = (ProjectManager)pmMock.proxy();
    myGitVcsSupport = gitSupport().withServerPaths(paths).withTestConnectionSupport(vcsRoot -> {
      if (myTestConnectionMocked != null && myTestConnectionMocked) return null;
      return myGitVcsSupport.testConnection(vcsRoot);
    }).build();

    final TokenRefresher tokenRefresher = (TokenRefresher)mock(TokenRefresher.class).proxy();
    myUrlSupport = new GitUrlSupport(myGitVcsSupport, tokenRefresher) {
      @NotNull
      @Override
      protected VcsRoot createDummyRoot(@NotNull final Map<String, String> props, @Nullable final SProject curProject) {
        return new VcsRootImpl(-1, Constants.VCS_NAME, props);
      }
    };
    myUrlSupport.setProjectManager(pm);

    final Mock sshMock = mock(ServerSshKeyManager.class);
    sshMock.stubs().method("getKeys").with(eq(project)).will(returnValue(myTestKeys));
    ServerSshKeyManager ssh = (ServerSshKeyManager)sshMock.proxy();
    Mock epMock = mock(ExtensionsProvider.class);
    epMock.stubs().method("getExtensions").with(eq(ServerSshKeyManager.class)).will(returnValue(Collections.singleton(ssh)));
    myUrlSupport.setExtensionsProvider((ExtensionsProvider)epMock.proxy());
  }

  @AfterMethod
  public void tearDown() throws Exception {
    myTestKeys.clear();
    myTempFiles.cleanup();
    myTestConnectionMocked = null;
    super.tearDown();
  }

  @Test
  public void test_convert() throws MalformedURLException, VcsException, URISyntaxException {
    List<String> urls = Arrays.asList("scm:git:git://github.com/path_to_repository",
                                      "scm:git:http://github.com/path_to_repository",
                                      "scm:git:https://github.com/path_to_repository",
                                      "scm:git:ssh://github.com/path_to_repository",
                                      "scm:git:file://localhost/path_to_repository",
                                      "scm:git:ssh://github.com/nd/regex.git/pom.xml");
    myTestConnectionMocked = true;

    for (String url : urls) {
      MavenVcsUrl vcsUrl = new MavenVcsUrl(url);
      GitVcsRoot root = toGitRoot(vcsUrl);
      assertEquals(new URIish(vcsUrl.getProviderSpecificPart()), root.getRepositoryFetchURL().get());
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
    assertNull(myUrlSupport.convertToVcsRootProperties(url, createRootContext()));

    url = new VcsUrl("svn://svn.repo.com/path_to_repository");
    assertNull(myUrlSupport.convertToVcsRootProperties(url, createRootContext()));
  }

  @NotNull
  private VcsOperationContext createRootContext() {
    return () -> SProject.ROOT_PROJECT_ID;
  }

  @Test
  public void convert_scp_like_syntax() throws Exception {
    MavenVcsUrl url = new MavenVcsUrl("scm:git:git@github.com:user/repo.git");
    myTestConnectionMocked = true;

    GitVcsRoot root = toGitRoot(url);
    assertEquals(new URIish(url.getProviderSpecificPart()), root.getRepositoryFetchURL().get());
    assertEquals(AuthenticationMethod.PRIVATE_KEY_DEFAULT, root.getAuthSettings().getAuthMethod());
    assertEquals("git", root.getAuthSettings().toMap().get(Constants.USERNAME));
  }

  @Test
  public void convert_scp_like_syntax_with_credentials() throws Exception {
    VcsUrl url = new VcsUrl("scm:git:git@github.com:user/repo.git", new Credentials("user", "pass"));
    myTestConnectionMocked = true;

    GitVcsRoot root = toGitRoot(url);
    assertEquals(new URIish("user@github.com:user/repo.git"), root.getRepositoryFetchURL().get());
    assertEquals(AuthenticationMethod.PRIVATE_KEY_DEFAULT, root.getAuthSettings().getAuthMethod());
    assertEquals("user", root.getAuthSettings().toMap().get(Constants.USERNAME));
    assertNull(root.getAuthSettings().toMap().get(Constants.PASSWORD));

    assertEquals(root.getProperties(),
                 myUrlSupport.convertToVcsRootProperties(new VcsUrl("git@github.com:user/repo.git", new Credentials("user", "pass")), createRootContext()));
  }

  @Test
  public void http_protocol() throws Exception {
    VcsUrl url = new VcsUrl("https://github.com/JetBrains/kotlin.git");
    GitVcsRoot root = toGitRoot(url);

    assertEquals("https://github.com/JetBrains/kotlin.git", root.getProperty(Constants.FETCH_URL));
    assertEquals("refs/heads/master", root.getProperty(Constants.BRANCH_NAME));
    assertEquals(AuthenticationMethod.ANONYMOUS.name(), root.getProperty(Constants.AUTH_METHOD));
  }

  @Test
  public void ssh_protocol() throws Exception {
    VcsUrl url = new VcsUrl("ssh://git@github.com/JetBrains/kotlin.git");
    myTestConnectionMocked = true;
    GitVcsRoot root = toGitRoot(url);

    assertEquals("ssh://git@github.com/JetBrains/kotlin.git", root.getProperty(Constants.FETCH_URL));
    assertEquals("refs/heads/master", root.getProperty(Constants.BRANCH_NAME));
    assertEquals(AuthenticationMethod.PRIVATE_KEY_DEFAULT.name(), root.getProperty(Constants.AUTH_METHOD));
  }

  @Test
  public void scp_syntax_use_uploaded_key() throws Exception {
    VcsUrl url = new VcsUrl("git@github.com:user/repo.git");
    myTestKeys.add(new TeamCitySshKey("key1", new byte[0], true));
    myTestKeys.add(new TeamCitySshKey("key2", new byte[0], false));

//    Mock vcsRoot = mock(SVcsRoot.class);
//    myProjectMock.expects(once()).method("createDummyVcsRoot").with(eq(Constants.VCS_NAME), mapContaining(VcsRootSshKeyManager.VCS_ROOT_TEAMCITY_SSH_KEY_NAME, "key2")).will(returnValue(vcsRoot.proxy()));
    myTestConnectionMocked = true;

    GitVcsRoot root = toGitRoot(url);

    assertEquals(AuthenticationMethod.TEAMCITY_SSH_KEY, root.getAuthSettings().getAuthMethod());
    assertEquals("key2", root.getAuthSettings().getTeamCitySshKeyId());
    assertEquals("git", root.getAuthSettings().toMap().get(Constants.USERNAME));
  }

  @Test(enabled = false) // the test is disabled because GitHub now allows accessing a public repository even if provided credentials are invalid
  public void http_protocol_with_invalid_credentials() throws Exception {
    VcsUrl url = new VcsUrl("https://github.com/JetBrains/kotlin.git", new Credentials("user1", "pwd1"));
    try {
      toGitRoot(url);
      fail("VcsException expected");
    } catch (VcsException e) {
      //
    }
  }

  @Test
  public void http_protocol_svn_repo() throws Exception {
    VcsUrl url = new VcsUrl("http://svn.jetbrains.org/teamcity/plugins/xml-tests-reporting/trunk/");
    assertNull(myUrlSupport.convertToVcsRootProperties(url, createRootContext()));
  }

  @Test
  public void should_not_use_private_key_for_local_repository() throws VcsException {
    final File localRepo = GitTestUtil.dataFile("repo.git");
    assertTrue(localRepo.exists());

    GitVcsRoot root = toGitRoot(new VcsUrl(GitUtils.toURL(localRepo)));
    assertEquals(AuthenticationMethod.ANONYMOUS.name(), root.getProperty(Constants.AUTH_METHOD));
  }

  @Test
  public void repo_no_master_branch() throws VcsException {
    final File localRepo = GitTestUtil.dataFile("repo_without_master.git");
    assertTrue(localRepo.exists());

    GitVcsRoot root = toGitRoot(new VcsUrl(GitUtils.toURL(localRepo)));
    assertEquals("refs/heads/default", root.getProperty(Constants.BRANCH_NAME));
  }

  @Test
  public void should_use_username_from_url() throws Exception {
    VcsUrl url = new VcsUrl("scm:git:http://teamcity@acme.com/repository.git");
    myTestConnectionMocked = true;
    GitVcsRoot root = toGitRoot(url);
    assertEquals("teamcity", root.getProperty(Constants.USERNAME));
  }

  @Test
  public void vault_url() throws Exception {
    VcsUrl url = new VcsUrl("http://some.host.com/VaultService/VaultWeb/Default.aspx?repid=1709&path=$/");
    assertNull(myUrlSupport.convertToVcsRootProperties(url, createRootContext()));
  }


  @Test
  @TestFor(issues = "TW-43247")
  public void should_throw_exception_on_authentication_failures() {
    Credentials credentials = new Credentials("username", "password");
    VcsUrl url = new VcsUrl("https://jb-vso-tests.visualstudio.com/DefaultCollection/_git/TeamCityGitTests", credentials);
    try {
      toGitRoot(url);
      fail();
    } catch (VcsException e) {
      assertContainsAny(e.getMessage().toLowerCase(), "not authorized", "authentication failed");
    }
  }

  @Test
  public void should_adjust_gitlab_fetch_url() throws VcsException {
    VcsUrl url = new VcsUrl("https://gitlab.com/fdroid/repomaker");
    GitVcsRoot root = toGitRoot(url);
    assertEquals("https://gitlab.com/fdroid/repomaker.git", root.getProperty(Constants.FETCH_URL));
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
    Map<String, String> properties = myUrlSupport.convertToVcsRootProperties(url, createRootContext());
    assertNotNull(properties);
    VcsRootImpl myRoot = new VcsRootImpl(1, properties);
    return new GitVcsRoot(myMirrorManager, myRoot, new URIishHelperImpl());
  }
}
