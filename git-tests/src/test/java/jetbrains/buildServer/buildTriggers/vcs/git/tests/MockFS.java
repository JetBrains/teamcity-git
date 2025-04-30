

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import java.util.Objects;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.FS;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class MockFS implements FS {

  private final Set<File> myDeleteToFail = new HashSet<File>();

  @Override
  public boolean delete(@NotNull final File f) {
    if (myDeleteToFail.contains(f))
      return false;
    return FileUtil.delete(f);
  }

  @Override
  public boolean mkdirs(@NotNull File dir) {
    return dir.mkdirs();
  }

  @Override
  public boolean deleteDirContent(@NotNull File dir) {
    if (myDeleteToFail.contains(dir)) {
      return false;
    }
    if (!dir.isDirectory()) {
      return false;
    }
    boolean success = true;
    for (File f : Objects.requireNonNull(dir.listFiles())) {
      success &= FileUtil.delete(f);
    }
    return success;
  }

  public void makeDeleteFail(@NotNull File f) {
    myDeleteToFail.add(f);
  }
}