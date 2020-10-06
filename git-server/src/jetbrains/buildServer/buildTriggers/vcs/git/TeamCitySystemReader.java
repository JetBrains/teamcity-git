/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

package jetbrains.buildServer.buildTriggers.vcs.git;

import com.intellij.openapi.diagnostic.Logger;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class TeamCitySystemReader extends SystemReader {

  private static final Logger LOG = Logger.getInstance(TeamCitySystemReader.class.getName());

  @NotNull
  private final SystemReader myDelegate;
  @NotNull
  private final Config myConfig;

  public TeamCitySystemReader(@NotNull final SystemReader delegate, @NotNull final Config config) {
    myDelegate = delegate;
    myConfig = config;
//    myConfig.clear();
//    try {
//      myConfig.fromText(config);
//    } catch (ConfigInvalidException e) {
//      LOG.warn("Failed to create config from string: " + config);
//      LOG.warnAndDebugDetails("Failed to create config from string", e);
//    }
  }

  @Override
  public String getHostname() {
    return myDelegate.getHostname();
  }

  @Override
  public String getenv(final String variable) {
    return myDelegate.getenv(variable);
  }

  @Override
  public String getProperty(final String key) {
    return myDelegate.getProperty(key);
  }

  @Override
  public FileBasedConfig openUserConfig(final Config parent, final FS fs) {
    return new FakeFileBasedConfig(parent, fs);
  }

  @Override
  public FileBasedConfig openSystemConfig(final Config parent, final FS fs) {
    return new FakeFileBasedConfig(parent, fs);
  }

  @Override
  public StoredConfig getUserConfig() throws IOException, ConfigInvalidException {
    return openSystemConfig(myConfig, FS.DETECTED);
  }


  @Override
  public StoredConfig getSystemConfig() throws IOException, ConfigInvalidException {
    return openSystemConfig(null, FS.DETECTED);
  }

  @Override
  public long getCurrentTime() {
    return myDelegate.getCurrentTime();
  }

  @Override
  public int getTimezone(final long when) {
    return myDelegate.getTimezone(when);
  }

  // Return an empty configuration, based on example in SystemReader.Default#openSystemConfig
  private static final class FakeFileBasedConfig extends FileBasedConfig {
    public FakeFileBasedConfig(final Config parent, final FS fs) {
      super(parent, null, fs);
    }

    @Override
    public void load() {
    }

    @Override
    public boolean isOutdated() {
      return false;
    }
  }
}
