/*
 * Copyright 2000-2019 JetBrains s.r.o.
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
import jetbrains.buildServer.buildTriggers.vcs.git.ServerPluginConfig;
import jetbrains.buildServer.buildTriggers.vcs.git.submodules.SubmoduleUrlResolver;
import jetbrains.buildServer.serverSide.ServerPaths;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.lib.StoredConfig;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;

@Test
public class SubmoduleUrlResolverTest {

  private TempFiles myTempFiles;
  private ServerPluginConfig myServerPluginConfig;

  @BeforeMethod
  public void setUp() throws Exception {
    myTempFiles = new TempFiles();
    myServerPluginConfig = new PluginConfigBuilder(new ServerPaths(myTempFiles.createTempDir().getAbsolutePath())).build();
  }

  @AfterMethod
  public void tearDown() {
    myTempFiles.cleanup();
  }

  @DataProvider
  public static Object[][] arguments() {
    return new Object[][]{
      new Object[]{new Arguments()
        .setMainUrl("https://git@someurl.com/path1/path1.git")
        .setSubUrl("someurl.com:path2/path2.git")
        .setExpectUrl("git@someurl.com:path2/path2.git")
        .setShouldSetSubmoduleUserInAbsoluteUrls(true)
      },
      new Object[]{new Arguments()
        .setMainUrl("https://git@someurl.com/path1/path1.git")
        .setSubUrl("user@someurl.com:path2/path2.git")
        .setExpectUrl("user@someurl.com:path2/path2.git")
        .setShouldSetSubmoduleUserInAbsoluteUrls(true)
      },
      new Object[]{new Arguments()
        .setMainUrl("https://git@someurl.com/path1/path1.git")
        .setSubUrl("someurl.com:path2/path2.git")
        .setExpectUrl("someurl.com:path2/path2.git")
        .setShouldSetSubmoduleUserInAbsoluteUrls(false)
      },
      new Object[]{new Arguments()
        .setMainUrl("https://git@someurl.com/path1/path1.git")
        .setSubUrl("user@someurl.com:path2/path2.git")
        .setExpectUrl("user@someurl.com:path2/path2.git")
        .setShouldSetSubmoduleUserInAbsoluteUrls(false)
      },
      new Object[]{new Arguments()
        .setMainUrl("https://someurl.com/path1/path1.git")
        .setSubUrl("ssh://someurl.com:path2/path2.git")
        .setExpectUrl("ssh://someurl.com:path2/path2.git")
        .setShouldSetSubmoduleUserInAbsoluteUrls(true)
      },
      new Object[]{new Arguments()
        .setMainUrl("https://someurl.com/path1/path1.git")
        .setSubUrl("ssh://user@someurl.com:path2/path2.git")
        .setExpectUrl("ssh://user@someurl.com:path2/path2.git")
        .setShouldSetSubmoduleUserInAbsoluteUrls(true)
      },
      new Object[]{new Arguments()
        .setMainUrl("https://git@someurl.com/path1/path1.git")
        .setSubUrl("git://someurl.com:path2/path2.git")
        .setExpectUrl("git://someurl.com:path2/path2.git")
        .setShouldSetSubmoduleUserInAbsoluteUrls(true)
      },
      new Object[]{new Arguments()
        .setMainUrl("https://someurl.com/path1/path1.git")
        .setSubUrl("git://someurl.com:path2/path2.git")
        .setExpectUrl("git://someurl.com:path2/path2.git")
        .setShouldSetSubmoduleUserInAbsoluteUrls(true)
      },
      new Object[]{new Arguments()
        .setMainUrl("https://git@someurl.com/path1/path1.git")
        .setSubUrl("../path2/path2.git")
        .setExpectUrl("https://git@someurl.com/path1/path2/path2.git")
        .setShouldSetSubmoduleUserInAbsoluteUrls(true)
      },
      new Object[]{new Arguments()
        .setMainUrl("someurl.com")
        .setSubUrl("./path2/path2.git")
        .setExpectUrl("someurl.com/path2/path2.git")
        .setShouldSetSubmoduleUserInAbsoluteUrls(true)
      },
    };
  }

  @Test(dataProvider = "arguments")
  public void resolveTest(@NotNull final Arguments arguments) throws Exception {
    Repository r = createBareRepository();
    StoredConfig config = r.getConfig();
    config.setString("teamcity", null, "remote", arguments.getMainUrl());
    System.setProperty("teamcity.git.setSubmoduleUserInAbsoluteUrls", arguments.getShouldSetSubmoduleUserInAbsoluteUrls().toString());

    final String actual = SubmoduleUrlResolver.resolveSubmoduleUrl(myServerPluginConfig, config, arguments.getSubUrl());
    Assert.assertEquals(arguments.getExpectUrl(), actual);
  }

  @DataProvider
  public static Object[][] urls() throws Exception {
    return new Object[][]{
      new Object[]{"someurl.com:path2/path2.git", true},
      new Object[]{"user@someurl.com:path2/path2.git", true},
      new Object[]{"../path2/path2.git", false},
      new Object[]{"./path2/path2.git", false},
    };
  }

  @Test(dataProvider = "urls")
  public void absoluteTest(@NotNull final String url, @NotNull final Boolean expect) throws Exception {
    Assert.assertEquals(expect, SubmoduleUrlResolver.isAbsolute(url));
  }

  @NotNull
  private Repository createBareRepository() throws IOException {
    File mirrorDir = myTempFiles.createTempDir();
    mirrorDir.mkdirs();
    Repository result = new RepositoryBuilder().setBare().setGitDir(mirrorDir).build();
    result.create(true);
    return result;
  }

  private static class Arguments {
    private String mainUrl;
    private String subUrl;
    private String expectUrl;
    private Boolean shouldSetSubmoduleUserInAbsoluteUrls;

    Arguments setMainUrl(final String mainUrl) {
      this.mainUrl = mainUrl;
      return this;
    }

    Arguments setSubUrl(final String subUrl) {
      this.subUrl = subUrl;
      return this;
    }

    Arguments setExpectUrl(final String expectUrl) {
      this.expectUrl = expectUrl;
      return this;
    }

    Arguments setShouldSetSubmoduleUserInAbsoluteUrls(final Boolean shouldSetSubmoduleUserInAbsoluteUrls) {
      this.shouldSetSubmoduleUserInAbsoluteUrls = shouldSetSubmoduleUserInAbsoluteUrls;
      return this;
    }

    String getMainUrl() {
      return mainUrl;
    }

    String getSubUrl() {
      return subUrl;
    }

    String getExpectUrl() {
      return expectUrl;
    }

    Boolean getShouldSetSubmoduleUserInAbsoluteUrls() {
      return shouldSetSubmoduleUserInAbsoluteUrls;
    }
  }
}
