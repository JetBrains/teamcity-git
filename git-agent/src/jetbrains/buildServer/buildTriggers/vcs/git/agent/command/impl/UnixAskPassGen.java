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

import com.intellij.openapi.util.io.FileUtil;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthSettings;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.AskPassGenerator;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class UnixAskPassGen implements AskPassGenerator {

  private final File myTempDir;
  private final EscapeEchoArgument myEscaper;

  public UnixAskPassGen(@NotNull File tempDir,
                        @NotNull EscapeEchoArgument escaper) {
    myTempDir = tempDir;
    myEscaper = escaper;
  }

  @NotNull
  public File generate(@NotNull AuthSettings authSettings) throws IOException {
    File script = FileUtil.createTempFile(myTempDir, "pass", "", true);
    PrintWriter out = null;
    try {
      out = new PrintWriter(new FileWriter(script));
      out.println("#!/bin/sh");
      out.println("printf " + myEscaper.escape(authSettings.getPassword()));
      if (!script.setExecutable(true))
        throw new IOException("Cannot make askpass script executable");
    } finally {
      if (out != null)
        out.close();
    }
    return script;
  }
}
