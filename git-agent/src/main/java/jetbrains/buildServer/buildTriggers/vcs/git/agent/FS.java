

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import org.jetbrains.annotations.NotNull;

import java.io.File;

public interface FS {

  boolean delete(@NotNull File f);

  boolean mkdirs(@NotNull File dir);

  boolean deleteDirContent(@NotNull File dir);

}