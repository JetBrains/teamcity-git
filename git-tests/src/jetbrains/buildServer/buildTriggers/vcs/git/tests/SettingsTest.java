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

import jetbrains.buildServer.buildTriggers.vcs.git.AuthenticationMethod;
import jetbrains.buildServer.buildTriggers.vcs.git.MirrorManager;
import jetbrains.buildServer.buildTriggers.vcs.git.Settings;
import jetbrains.buildServer.vcs.VcsRoot;
import org.eclipse.jgit.transport.URIish;
import org.jmock.Mockery;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;

/**
 * @author dmitry.neverov
 */
@Test
public class SettingsTest {

  private MirrorManager myMirrorManager;

  @BeforeMethod
  public void setUp() {
    Mockery context = new Mockery();
    myMirrorManager = context.mock(MirrorManager.class);
  }

  public void fetch_url_for_repository_in_local_filesystem_should_not_contain_password() throws Exception {
    String pathInLocalFS = "/path/in/local/fs";
    VcsRoot root = vcsRoot().withFetchUrl(pathInLocalFS)
      .withAuthMethod(AuthenticationMethod.PASSWORD)
      .withUsername("user")
      .withPassword("pass")
      .build();
    Settings s = new Settings(myMirrorManager, root);
    assertEquals(new URIish(pathInLocalFS), s.getRepositoryFetchURL());
  }

  public void cred_prod() throws Exception {
    VcsRoot root = vcsRoot().withFetchUrl("git://git@some.org/repository.git").build();
    Settings s = new Settings(myMirrorManager, root);
    assertNull("User is not stripped from the url with anonymous protocol", s.getRepositoryFetchURL().getUser());
    assertEquals("git", s.getRepositoryFetchURL().getUser());
  }

}
