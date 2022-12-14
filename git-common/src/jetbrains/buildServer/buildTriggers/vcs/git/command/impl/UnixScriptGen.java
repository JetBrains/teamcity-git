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

package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthSettings;
import jetbrains.buildServer.buildTriggers.vcs.git.command.credentials.ScriptGen;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UnixScriptGen extends ScriptGen {

  private final EscapeEchoArgument myEscaper;

  public UnixScriptGen(@NotNull File tempDir,
                       @NotNull EscapeEchoArgument escaper) {
    super(tempDir);
    myEscaper = escaper;
  }

  @NotNull
  public File generateAskPass(@NotNull AuthSettings authSettings) throws IOException {
    return generateAskPass(authSettings.getPassword());
  }


  @NotNull
  @Override
  public File generateAskPass(@Nullable String password) throws IOException {
    File script = FileUtil.createTempFile(myTempDir, "pass", "", true);
    PrintWriter out = null;
    try {
      out = new PrintWriter(new FileWriter(script));
      out.println("#!/bin/sh");
      out.println("printf '%s' " + myEscaper.escape(password));
      if (!script.setExecutable(true))
        throw new IOException("Cannot make askpass script executable");
    } finally {
      if (out != null)
        out.close();
    }
    return script;
  }


  @NotNull
  @Override
  protected String getCredHelperTemplate() {
    return "/META-INF/credentials-helper.sh";
  }
}
