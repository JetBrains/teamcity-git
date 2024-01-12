

package jetbrains.buildServer.buildTriggers.vcs.git;

import java.io.File;
import java.io.IOException;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class FetcherProperties {

  private final ServerPluginConfig myConfig;

  public FetcherProperties(@NotNull ServerPluginConfig config) {
    myConfig = config;
  }


  @NotNull
  public File getPropertiesFile() throws VcsException {
    try {
      File props = FileUtil.createTempFile("git", "props");
      GitServerUtil.writeAsProperties(props, myConfig.getFetcherProperties());
      return props;
    } catch (IOException e) {
      throw new VcsException("Cannot create properties file for git fetch process: " + e.getMessage(), e);
    }
  }
}