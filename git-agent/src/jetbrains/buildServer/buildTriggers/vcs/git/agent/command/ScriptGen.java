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

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command;

import com.intellij.openapi.util.io.StreamUtil;
import jetbrains.buildServer.agent.ClasspathUtil;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthSettings;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.CredentialsHelper;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;

public abstract class ScriptGen {

  protected final File myTempDir;

  public ScriptGen(@NotNull File tempDir) {
    myTempDir = tempDir;
  }

  @NotNull
  public abstract File generateAskPass(@NotNull AuthSettings authSettings) throws IOException;

  @NotNull
  public abstract File generateAskPass(@Nullable String password) throws IOException;


  @NotNull
  public File generateCredentialsHelper() throws IOException {
    String template = getCredHelperTemplate();
    String extension = "";
    int idx = template.lastIndexOf(".");
    if (idx != -1)
      extension = template.substring(idx, template.length());

    File result = FileUtil.createTempFile(myTempDir, "credHelper", extension, true);
    PrintWriter out = null;
    try {
      out = new PrintWriter(new FileWriter(result));

      InputStream templateStream = ScriptGen.class.getResourceAsStream(template);
      if (templateStream == null)
        throw new IOException("Cannot read script template " + template);
      String script = StreamUtil.readText(templateStream);
      String javaPath = "java";
      String javaHome = System.getProperty("java.home");
      if (StringUtil.isNotEmpty(javaHome)) {
        javaPath = "\"" + javaHome + File.separatorChar + "bin" + File.separatorChar + "java\"";
      }
      script = script.replace("{JAVA}", javaPath);
      script = script.replace("{CREDENTIALS_SCRIPT}", result.getCanonicalPath());
      script = script.replace("{CREDENTIALS_CLASSPATH}", ClasspathUtil.composeClasspath(new Class[]{CredentialsHelper.class}, null, null));
      script = script.replace("{CREDENTIALS_CLASS}", CredentialsHelper.class.getName());
      String[] lines = script.split("(\r\n|\r|\n)");
      for (String line : lines) {
        out.println(line);
      }
      if (!result.setExecutable(true))
        throw new IOException("Cannot make credentialsHelper script executable");
    } catch (IOException e) {
      FileUtil.delete(result);
      throw e;
    } finally {
      if (out != null)
        out.close();
    }
    return result;
  }


  @NotNull
  protected abstract String getCredHelperTemplate();
}
