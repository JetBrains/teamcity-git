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

import jetbrains.buildServer.buildTriggers.vcs.git.AuthSettings;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthenticationMethod;
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.vcs.VcsRoot;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;
import org.testng.annotations.Test;

import java.net.URISyntaxException;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static org.testng.AssertJUnit.*;

@Test
public class AuthSettingsTest {

  @TestFor(issues = {"TW-24499", "TW-30655"})
  public void should_not_fail_when_password_is_empty() throws Exception {
    VcsRoot root = vcsRoot().withFetchUrl("http://some.org/repository")
      .withAuthMethod(AuthenticationMethod.PASSWORD)
      .withUsername("user")
      .build();
    CredentialsProvider c = new AuthSettings(root).toCredentialsProvider();
    assertFalse(c.supports(new CredentialItem.Username(), new CredentialItem.Password()));
    c.get(new URIish("http://some.org/repository"), new CredentialItem.Username(), new CredentialItem.Password());
  }


  public void credential_provider_should_return_true_when_all_items_provided() throws Exception {
    VcsRoot root = vcsRoot().withFetchUrl("http://some.org/repository")
      .withAuthMethod(AuthenticationMethod.PASSWORD)
      .withUsername("user")
      .build();
    CredentialsProvider c = new AuthSettings(root).toCredentialsProvider();
    assertFalse(c.supports(new CredentialItem.Username(), new CredentialItem.Password()));

    final URIish uri = new URIish("http://some.org/repository");
    assertTrue(c.get(uri, new CredentialItem.Username()));
    assertFalse(c.get(uri, new CredentialItem.Password()));
    assertFalse(c.get(uri, new CredentialItem.Username(), new CredentialItem.Password()));
  }


  @TestFor(issues = "TW-27506")
  public void should_take_username_from_URL_into_account() throws URISyntaxException {
    String user = "usr";
    String url = "http://" + user + "@acme.org/repository.git";
    VcsRoot root = vcsRoot().withFetchUrl(url)
      .withAuthMethod(AuthenticationMethod.PASSWORD)
      .withPassword("pwd")
      .build();
    CredentialsProvider c = new AuthSettings(root).toCredentialsProvider();
    CredentialItem.Username username = new CredentialItem.Username();
    c.get(new URIish(url), username, new CredentialItem.Password());
    assertEquals(user, username.getValue());
  }


  @TestFor(issues = "TW-27226")
  public void should_take_username_in_URL_into_account2() {
    VcsRoot root = vcsRoot().withFetchUrl("git@github.com:name/repo.git")
      .withAuthMethod(AuthenticationMethod.PRIVATE_KEY_DEFAULT)
      .build();
    assertEquals("git", new AuthSettings(root).getUserName());
  }


  @TestFor(issues = "TW-25087")
  public void auth_uri_for_anonymous_protocol_should_not_have_user_and_password() throws Exception {
    VcsRoot root = vcsRoot().withFetchUrl("git://some.org/repo.git")
      .withAuthMethod(AuthenticationMethod.PASSWORD)
      .withUsername("user")
      .withPassword("pwd")
      .build();
    AuthSettings authSettings = new AuthSettings(root);
    URIish authURI = authSettings.createAuthURI("git://some.org/repo.git");
    assertNull(authURI.getUser());
    assertNull(authURI.getPass());
  }
}
