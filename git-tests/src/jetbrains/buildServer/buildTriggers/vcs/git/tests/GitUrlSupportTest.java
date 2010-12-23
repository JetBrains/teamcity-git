/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import jetbrains.buildServer.buildTriggers.vcs.git.AuthenticationMethod;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUrlSupport;
import jetbrains.buildServer.buildTriggers.vcs.git.Settings;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsUrl;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import org.eclipse.jgit.transport.URIish;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author dmitry.neverov
 */
public class GitUrlSupportTest extends BaseTestCase {

  GitUrlSupport mySupport;

  @BeforeMethod
  public void setUp() throws IOException {
    mySupport = new GitUrlSupport();
  }

  @Test
  public void test_get_provider_schema() {
    assertEquals("git:ssh", mySupport.getProviderSchema());
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
      VcsUrl vcsUrl = new VcsUrl(url);
      Settings s = toSettings(vcsUrl);
      assertEquals(new URIish(vcsUrl.getProviderSpecificPart()), s.getRepositoryFetchURL());
      checkAuthMethod(vcsUrl, s);
    }

    String user = "user";
    String pass = "pass";
    for (String url : urls) {
      VcsUrl vcsUrl = new VcsUrl(url, user, pass);
      Settings s = toSettings(vcsUrl);
      checkAuthMethod(vcsUrl, s);
    }
  }


  @Test
  public void should_throw_exception_when_url_incorrect() throws MalformedURLException, VcsException {
    VcsUrl url = new VcsUrl("scm:svn:ssh://svn.repo.com/path_to_repository");
    try {
      toSettings(url);
      fail("Should fail here");
    } catch (VcsException e) {
      assertTrue(true);
      assertTrue(e.getMessage().contains("Unknown provider schema"));
    }
  }


  private void checkAuthMethod(VcsUrl url, Settings s) {
    if (url.getProviderSpecificPart().startsWith("ssh")) {
      assertEquals(AuthenticationMethod.PRIVATE_KEY_DEFAULT, s.getAuthSettings().getAuthMethod());
      assertTrue(s.getAuthSettings().isIgnoreKnownHosts());
      assertEquals(url.getUsername(), s.getAuthSettings().toMap().get(Constants.USERNAME));
      assertNull(s.getAuthSettings().toMap().get(Constants.PASSWORD));
    } else {
      if (url.getUsername() != null && url.getPassword() != null &&
          !StringUtil.isEmptyOrSpaces(url.getUsername()) &&
          !StringUtil.isEmptyOrSpaces(url.getPassword())) {
        assertEquals(AuthenticationMethod.PASSWORD, s.getAuthSettings().getAuthMethod());
        assertEquals(url.getUsername(), s.getAuthSettings().toMap().get(Constants.USERNAME));
        assertEquals(url.getPassword(), s.getAuthSettings().toMap().get(Constants.PASSWORD));
      } else {
        assertEquals(AuthenticationMethod.ANONYMOUS, s.getAuthSettings().getAuthMethod());
        assertNull(s.getAuthSettings().toMap().get(Constants.USERNAME));
        assertNull(s.getAuthSettings().toMap().get(Constants.PASSWORD));
      }
    }
  }

  private Settings toSettings(VcsUrl url) throws VcsException {
    Map<String, String> properties = mySupport.convertToVcsRootProperties(url);
    VcsRootImpl myRoot = new VcsRootImpl(1, properties);
    return new Settings(myRoot);
  }
}
