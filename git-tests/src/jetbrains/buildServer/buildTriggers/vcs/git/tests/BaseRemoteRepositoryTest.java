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
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.tests.builders.BuildAgentConfigurationBuilder;
import jetbrains.buildServer.log.LogInitializer;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.copyRepository;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.dataFile;

public abstract class BaseRemoteRepositoryTest {

  private Set<String> myPropertiesToClean;
  protected TempFiles myTempFiles;
  protected BuildAgentConfiguration myAgentConfiguration;
  private String[] myRepositories;
  private Map<String, File> myRemoteRepositories;

  protected BaseRemoteRepositoryTest(String... repositories) {
    myRepositories = repositories;
  }

  @BeforeClass
  public void setUpClass() {
    LogInitializer.setUnitTest(true);
  }

  @BeforeMethod
  public void setUp() throws Exception {
    TestInternalProperties.init();
    myPropertiesToClean = new HashSet<>();
    myTempFiles = new TempFiles();
    File tmp = myTempFiles.createTempDir();
    myRemoteRepositories = new HashMap<String, File>();
    for (String r : myRepositories) {
      File remoteRepository = new File(tmp, r);
      copyRepository(dataFile(r), remoteRepository);
      myRemoteRepositories.put(r, remoteRepository);
    }
    myAgentConfiguration = BuildAgentConfigurationBuilder.agentConfiguration(myTempFiles.createTempDir(), myTempFiles.createTempDir())
      .build();
  }

  @AfterMethod
  public void tearDown() {
    cleanInternalProperties();
    myTempFiles.cleanup();
  }

  protected String getRemoteRepositoryUrl(@NotNull String remoteRepository) {
    File remote = myRemoteRepositories.get(remoteRepository);
    if (remote == null)
      throw new IllegalArgumentException("No remote repository found: " + remoteRepository);
    return GitUtils.toURL(remote);
  }

  protected File getRemoteRepositoryDir(@NotNull String remoteRepository) {
    return myRemoteRepositories.get(remoteRepository);
  }

  @DataProvider(name = "true,false")
  public static Object[][] createData() {
    return new Object[][] {
      new Object[] { Boolean.TRUE },
      new Object[] { Boolean.FALSE }
    };
  }

  protected void setInternalProperty(@NotNull String propKey, @NotNull String value) {
    System.setProperty(propKey, value);
    myPropertiesToClean.add(propKey);
  }

  private void cleanInternalProperties() {
    for (String prop : myPropertiesToClean) {
      System.getProperties().remove(prop);
    }
    myPropertiesToClean.clear();
  }
}
