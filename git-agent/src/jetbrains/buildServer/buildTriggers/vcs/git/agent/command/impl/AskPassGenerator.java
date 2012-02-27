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

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl;

import jetbrains.buildServer.buildTriggers.vcs.git.agent.AgentPluginConfig;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author dmitry.neverov
 */
public abstract class AskPassGenerator {

  private final AgentPluginConfig myConfig;
  private final List<File> myFiles = new ArrayList<File>();

  protected AskPassGenerator(@NotNull AgentPluginConfig config) {
    myConfig = config;
  }

  public final String generateScriptFor(@NotNull String password) throws VcsException {
    try {
      File passFile = createPassFile(password);
      File askPass = createAskPass(passFile);
      myFiles.add(passFile);
      myFiles.add(askPass);
      return askPass.getCanonicalPath();
    } catch (Exception e) {
      throw new VcsException("Error while generating askpass script", e);
    }
  }

  public void cleanup() {
    Iterator<File> iter = myFiles.iterator();
    while (iter.hasNext()) {
      File f = iter.next();
      FileUtil.delete(f);
      iter.remove();
    }
  }

  @NotNull abstract String getScriptName();

  @NotNull abstract String getScriptContent(@NotNull String passwordPath);

  private File createPassFile(@NotNull String password) throws IOException {
    File passFile = FileUtil.createTempFile(myConfig.getTempDir(), "tc", "pass", true);
    FileUtil.writeFile(passFile, password);
    return passFile;
  }

  private File createAskPass(@NotNull File passFile) throws IOException {
    File askPass = FileUtil.createTempFile(myConfig.getTempDir(), "", getScriptName(), true);
    FileUtil.writeFile(askPass, getScriptContent(passFile.getCanonicalPath()));
    FileUtil.setExectuableAttribute(askPass.getAbsolutePath(), true);
    return askPass;
  }
}
