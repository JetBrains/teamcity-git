

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class FSImpl implements FS {

  @Override
  public boolean delete(@NotNull File f) {
    return FileUtil.delete(f);
  }

  @Override
  public boolean mkdirs(@NotNull File dir) {
    return dir.mkdirs();
  }
}