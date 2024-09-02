

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import jetbrains.buildServer.agent.SmartDirectoryCleaner;
import jetbrains.buildServer.agent.SmartDirectoryCleanerCallback;
import jetbrains.buildServer.util.FileUtil;

import java.io.File;

public class MockDirectoryCleaner implements SmartDirectoryCleaner {
  public void cleanFolder(final File file, final SmartDirectoryCleanerCallback callback) {
    FileUtil.delete(file);
  }
}