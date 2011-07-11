/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.dataFile;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.getVcsRoot;
import static org.testng.AssertJUnit.assertEquals;

/**
 * @author dmitry.neverov
 */
@Test
public class TransportFactoryTest {

  private TempFiles myTempFiles;
  private PluginConfigBuilder myConfigBuilder;
  private ServerPaths myServerPaths;


  @BeforeMethod
  public void setUp() throws Exception {
    myTempFiles = new TempFiles();
    File dotBuildServer = myTempFiles.createTempDir();
    myServerPaths = new ServerPaths(dotBuildServer.getAbsolutePath());
    myConfigBuilder = new PluginConfigBuilder(myServerPaths);
  }


  @AfterMethod
  public void tearDown() {
    myTempFiles.cleanup();
  }


  public void transport_should_have_timeout_specified_in_config() throws Exception {
    myConfigBuilder.setIdleTimeoutSeconds(20);
    ServerPluginConfig config = myConfigBuilder.build();
    TransportFactory transportFactory = new TransportFactoryImpl(config);
    Transport transport = createTransport(transportFactory);
    assertEquals(20, transport.getTimeout());
  }


  private Transport createTransport(TransportFactory factory) throws Exception {
    File original = dataFile("repo.git");
    File copy = myTempFiles.createTempDir();
    FileUtil.copyDir(original, copy);

    VcsRootImpl root = getVcsRoot(copy);
    Settings settings = new Settings(root, new File(myServerPaths.getCachesDir()));
    Repository repository = new RepositoryBuilder().setGitDir(copy).setBare().build();
    return factory.createTransport(repository, new URIish(GitUtils.toURL(original)), settings.getAuthSettings());
  }
}
