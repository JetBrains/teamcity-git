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

import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author dmitry.neverov
 */
public abstract class AskPassGenerator {

  private final TempFiles myTempFiles = new TempFiles();

  public final String generateScriptFor(@NotNull String password) throws VcsException {
    try {
      File passFile = myTempFiles.createTempFile(password);
      File askPass = new File(myTempFiles.createTempDir(), getScriptName());
      FileUtil.writeFile(askPass, getScriptContent(passFile.getCanonicalPath()));
      FileUtil.setExectuableAttribute(askPass.getAbsolutePath(), true);
      return askPass.getCanonicalPath();
    } catch (Exception e) {
      throw new VcsException("Error while generating askpass script", e);
    }
  }

  public void cleanup() {
    myTempFiles.cleanup();
  }

  @NotNull abstract String getScriptName();

  @NotNull abstract String getScriptContent(@NotNull String passwordPath);
}
