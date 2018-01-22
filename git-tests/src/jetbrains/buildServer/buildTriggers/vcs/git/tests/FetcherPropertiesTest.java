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
import jetbrains.buildServer.buildTriggers.vcs.git.FetcherProperties;
import jetbrains.buildServer.serverSide.FileWatchingPropertiesModel;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.PluginConfigBuilder.pluginConfig;
import static org.testng.AssertJUnit.assertEquals;

@Test
public class FetcherPropertiesTest {

  private TempFiles myTempFiles;
  private PluginConfigBuilder myPluginConfig;

  @BeforeMethod
  public void setUp() throws Exception {
    myTempFiles = new TempFiles();
    myPluginConfig = pluginConfig().withDotBuildServerDir(myTempFiles.createTempDir());
  }

  @AfterMethod
  public void tearDown() throws Exception {
    myTempFiles.cleanup();
  }

  public void fetcher_properties_file_should_contain_properties_for_fetcher() throws Exception {
    myPluginConfig.withFetcherProperties("fetcher.property.1", "val.1", "fetcher.property.2", "val.2");
    FetcherProperties props = new FetcherProperties(myPluginConfig.build());
    File fetcherProps = props.getPropertiesFile();
    initTeamCityProperties(fetcherProps);
    assertEquals("val.1", TeamCityProperties.getProperty("fetcher.property.1"));
    assertEquals("val.2", TeamCityProperties.getProperty("fetcher.property.2"));
  }


  private void initTeamCityProperties(final File fetcherProps) {
    new TeamCityProperties() {{
      setModel(new FileWatchingPropertiesModel(fetcherProps));
    }};
  }
}
