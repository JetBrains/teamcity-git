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

import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.TestInternalProperties;
import jetbrains.buildServer.buildTriggers.vcs.git.URIishHelperImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthenticationMethod;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsRoot;
import jetbrains.buildServer.buildTriggers.vcs.git.MirrorManagerImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitAgentVcsSupport;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.RemoteRepositoryConfigurator;
import jetbrains.buildServer.log.LogInitializer;
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.lib.StoredConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.*;

import java.io.File;
import java.io.IOException;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static org.assertj.core.api.BDDAssertions.then;

@SuppressWarnings("ALL")
@Test
@TestFor(issues = "TW-48103")
public class RemoteRepositoryConfiguratorTest {

  private TempFiles myTempFiles;
  private MirrorManagerImpl myMirrorManager;

  @BeforeClass
  public void setUpClass() {
    LogInitializer.setUnitTest(true);
  }

  @BeforeMethod
  public void setUp() throws Exception {
    TestInternalProperties.init();
    myTempFiles = new TempFiles();
    AgentSupportBuilder builder = new AgentSupportBuilder(myTempFiles);
    GitAgentVcsSupport vcs = builder.build();
    myMirrorManager = builder.getMirrorManager();
  }


  @AfterMethod
  public void tearDown() throws Exception {
    myTempFiles.cleanup();
  }


  @DataProvider
  public static Object[][] setups() throws Exception {
    return new Object[][] {
      //new repository
      new Object[] {new Setup()
        .setRoot(vcsRoot().withFetchUrl("git://some.org/repo.git"))
        .setExpectedUrl("git://some.org/repo.git")
        .setExpectedCredentialUser(null)
      },
      new Object[] {new Setup()
        .setRoot(vcsRoot().withFetchUrl("git@some.org:repo.git"))
        .setExpectedUrl("git@some.org:repo.git")
        .setExpectedCredentialUser(null)
      },
      new Object[] {new Setup()
        .setRoot(vcsRoot().withFetchUrl("ssh://git@some.org/repo.git"))
        .setExpectedUrl("ssh://git@some.org/repo.git")
        .setExpectedCredentialUser(null)
      },
      new Object[] {new Setup()
        .setRoot(vcsRoot().withFetchUrl("http://some.org/repo.git"))
        .setExpectedUrl("http://some.org/repo.git")
        .setExpectedCredentialUser(null)
      },
      new Object[] {new Setup()
        .setRoot(vcsRoot().withFetchUrl("https://some.org/repo.git"))
        .setExpectedUrl("https://some.org/repo.git")
        .setExpectedCredentialUser(null)
      },
      new Object[] {new Setup()
        .setRoot(vcsRoot().withFetchUrl("http://some.org/repo.git").withAuthMethod(AuthenticationMethod.PASSWORD).withUsername("user").withPassword("pwd"))
        .setExpectedUrl("http://some.org/repo.git")
        .setExpectedCredentialUser("user")
      },
      new Object[] {new Setup()
        .setRoot(vcsRoot().withFetchUrl("https://user@some.org/repo.git").withAuthMethod(AuthenticationMethod.PASSWORD).withPassword("pwd"))
        .setExpectedUrl("https://some.org/repo.git")
        .setExpectedCredentialUser("user")
      },

      //existing repository
      new Object[] {new Setup()
        .setRoot(vcsRoot().withFetchUrl("git://some.org/repo.git"))
        .setExpectedUrl("git://some.org/repo.git")
        .setExpectedCredentialUser(null)
        .setConfigAction("prepare existing repository", config -> {
          config.setString("remote", "origin", "url", "someUrl");
          config.setString("credential", null, "username", "someUser");
        })
      },
      new Object[] {new Setup()
        .setRoot(vcsRoot().withFetchUrl("git@some.org:repo.git"))
        .setExpectedUrl("git@some.org:repo.git")
        .setExpectedCredentialUser(null)
        .setConfigAction("prepare existing repository", config -> {
          config.setString("remote", "origin", "url", "someUrl");
          config.setString("credential", null, "username", "someUser");
        })
      },
      new Object[] {new Setup()
        .setRoot(vcsRoot().withFetchUrl("ssh://git@some.org/repo.git"))
        .setExpectedUrl("ssh://git@some.org/repo.git")
        .setExpectedCredentialUser(null)
        .setConfigAction("prepare existing repository", config -> {
          config.setString("remote", "origin", "url", "someUrl");
          config.setString("credential", null, "username", "someUser");
        })
      },
      new Object[] {new Setup()
        .setRoot(vcsRoot().withFetchUrl("http://some.org/repo.git"))
        .setExpectedUrl("http://some.org/repo.git")
        .setExpectedCredentialUser(null)
        .setConfigAction("prepare existing repository", config -> {
          config.setString("remote", "origin", "url", "someUrl");
          config.setString("credential", null, "username", "someUser");
        })
      },
      new Object[] {new Setup()
        .setRoot(vcsRoot().withFetchUrl("http://some.org/repo.git").withAuthMethod(AuthenticationMethod.PASSWORD).withUsername("user").withPassword("pwd"))
        .setExpectedUrl("http://some.org/repo.git")
        .setExpectedCredentialUser("user")
        .setConfigAction("prepare existing repository", config -> {
          config.setString("remote", "origin", "url", "someUrl");
          config.setString("credential", null, "username", "someUser");
        })
      },
      new Object[] {new Setup()
        .setRoot(vcsRoot().withFetchUrl("https://user@some.org/repo.git").withAuthMethod(AuthenticationMethod.PASSWORD).withPassword("pwd"))
        .setExpectedUrl("https://some.org/repo.git")
        .setExpectedCredentialUser("user")
        .setConfigAction("prepare existing repository", config -> {
          config.setString("remote", "origin", "url", "someUrl");
          config.setString("credential", null, "username", "someUser");
        })
      },
      new Object[] {new Setup()
        .setRoot(vcsRoot().withFetchUrl("https://some.org/repo.git").withAuthMethod(AuthenticationMethod.PASSWORD).withUsername("user").withPassword("pwd"))
        .setExpectedUrl("https://some.org/repo.git")
        .setExpectedCredentialUser("user")
        .setConfigAction("prepare existing repository", config -> {
          config.setString("remote", "origin", "url", "someUrl");
          config.setString("credential", null, "username", "someUser");
        })
      },

      //disable exclude
      new Object[] {new Setup()
        .setRoot(vcsRoot().withFetchUrl("https://some.org/repo.git").withAuthMethod(AuthenticationMethod.PASSWORD).withUsername("user").withPassword("pwd"))
        .disableUsernameExclude()
        .setExpectedUrl("https://user@some.org/repo.git")
        .setExpectedCredentialUser(null)
      },
      new Object[] {new Setup()
        .setRoot(vcsRoot().withFetchUrl("http://some.org/repo.git").withAuthMethod(AuthenticationMethod.PASSWORD).withUsername("user").withPassword("pwd"))
        .disableUsernameExclude()
        .setExpectedUrl("http://user@some.org/repo.git")
        .setExpectedCredentialUser(null)
        .setConfigAction("prepare existing repository", config -> {
          config.setString("remote", "origin", "url", "http://some.org/repo.git");
          config.setString("credential", null, "username", "user");
        })
      },
    };
  }


  @Test(dataProvider = "setups")
  public void test(@NotNull Setup setup) throws Exception {
    Repository r = createBareRepository();

    ConfigAction action = setup.getConfigAction();
    if (action != null) {
      StoredConfig cfg = r.getConfig();
      try {
        action.run(cfg);
      } finally {
        cfg.save();
      }
    }

    RemoteRepositoryConfigurator configurator = new RemoteRepositoryConfigurator();
    configurator.setGitDir(r.getDirectory());
    configurator.setExcludeUsernameFromHttpUrls(setup.isExcludeUsernameFromHttpUrl());
    configurator.configure(createRoot(setup.getRoot()));

    StoredConfig config = r.getConfig();
    then(config.getString("remote", "origin", "url")).isEqualTo(setup.getExpectedUrl());
    then(config.getString("credential", null, "username")).isEqualTo(setup.getExpectedCredentialUser());
  }


  @NotNull
  private Repository createBareRepository() throws IOException {
    File mirrorDir = myTempFiles.createTempDir();
    mirrorDir.mkdirs();
    Repository result = new RepositoryBuilder().setBare().setGitDir(mirrorDir).build();
    result.create(true);
    return result;
  }


  @NotNull
  private Repository createRepository() throws IOException {
    File workingDir = myTempFiles.createTempDir();
    workingDir.mkdirs();
    Repository result = new RepositoryBuilder().setWorkTree(workingDir).build();
    result.create();
    return result;
  }


  @NotNull
  private GitVcsRoot createRoot(@NotNull VcsRootBuilder root) throws VcsException {
    return new GitVcsRoot(myMirrorManager, root.build(), new URIishHelperImpl());
  }


  private static class Setup {
    private VcsRootBuilder myRoot;
    private String myExpectedUrl;
    private String myExpectedCredentialUser;
    private String myConfigActionDescription;
    private ConfigAction myConfigAction;
    private boolean myExcludeUsernameFromHttpUrl = true;

    public VcsRootBuilder getRoot() {
      return myRoot;
    }

    @NotNull
    public Setup setRoot(@NotNull VcsRootBuilder root) {
      myRoot = root;
      return this;
    }

    public String getExpectedUrl() {
      return myExpectedUrl;
    }

    @NotNull
    public Setup setExpectedUrl(final String expectedUrl) {
      myExpectedUrl = expectedUrl;
      return this;
    }

    public String getExpectedCredentialUser() {
      return myExpectedCredentialUser;
    }

    @NotNull
    public Setup setExpectedCredentialUser(@Nullable String expectedCredentialUser) {
      myExpectedCredentialUser = expectedCredentialUser;
      return this;
    }

    @NotNull
    public Setup setConfigAction(@NotNull String description, @NotNull ConfigAction action) {
      myConfigActionDescription = description;
      myConfigAction = action;
      return this;
    }

    public ConfigAction getConfigAction() {
      return myConfigAction;
    }

    public boolean isExcludeUsernameFromHttpUrl() {
      return myExcludeUsernameFromHttpUrl;
    }

    @NotNull
    public Setup disableUsernameExclude() {
      myExcludeUsernameFromHttpUrl = false;
      return this;
    }

    @Override
    public String toString() {
      return "root: " + describeRoot() +
             ", expectedUrl: '" + myExpectedUrl + '\'' +
             ", expectedCredentialUser: " + (myExpectedCredentialUser != null ? "'" + myExpectedCredentialUser  + "'" : "null") +
             (myConfigActionDescription != null ? ", configAction: " + myConfigActionDescription : "") +
             (myExcludeUsernameFromHttpUrl ? "" : ", excludeUsernameFromUrl: false");
    }

    @NotNull
    private String describeRoot() {
      VcsRootImpl root = myRoot.build();
      StringBuilder result = new StringBuilder();
      result.append("{url=").append(root.getProperty(Constants.FETCH_URL));
      result.append(", auth=").append(root.getProperty(Constants.AUTH_METHOD));
      result.append(", user=").append(root.getProperty(Constants.USERNAME));
      result.append("}");
      return result.toString();
    }
  }

  interface ConfigAction {
    void run(@NotNull Config config) throws Exception;
  }
}
