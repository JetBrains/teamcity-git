

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import java.util.Objects;
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

  @Override
  public boolean deleteDirContent(@NotNull File dir) {
    if (!dir.isDirectory()) {
      return false;
    }


    File[] files = dir.listFiles();
    if (files == null) {
      return false;
    }
    boolean successfull = true;
    for (File file : files) {
      successfull &= FileUtil.delete(file);
    }
    return successfull;
  }
}