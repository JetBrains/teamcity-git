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

package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;

public class FetcherProperties {

  private final ServerPluginConfig myConfig;

  public FetcherProperties(@NotNull ServerPluginConfig config) {
    myConfig = config;
  }


  @NotNull
  public File getPropertiesFile() throws VcsException {
    try {
      File props = FileUtil.createTempFile("git", "props");
      StringBuilder sb = new StringBuilder();
      for (Map.Entry<String, String> e : myConfig.getFetcherProperties().entrySet()) {
        if (!isEmpty(e.getValue()))
          sb.append(e.getKey()).append("=").append(e.getValue()).append("\n");
      }
      FileUtil.writeFileAndReportErrors(props, sb.toString());
      return props;
    } catch (IOException e) {
      throw new VcsException("Cannot create properties file for git fetch process", e);
    }
  }
}
