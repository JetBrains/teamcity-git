

package jetbrains.buildServer.buildTriggers.vcs.git.command.credentials;

import com.intellij.openapi.util.io.StreamUtil;
import java.io.*;
import jetbrains.buildServer.agent.ClasspathUtil;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthSettings;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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