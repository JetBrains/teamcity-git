

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import jetbrains.buildServer.buildTriggers.vcs.git.Fetcher;
import org.eclipse.jgit.internal.storage.file.LockFile;
import org.eclipse.jgit.util.FS;

import java.io.File;

/**
 * @author dmitry.neverov
 */
public class MockFetcher {

  public static void main(String... args) throws Exception {
    String repositoryPath = new File(".").getAbsolutePath();
    LockFile lock = new LockFile(new File(repositoryPath, "mock"));
    lock.lock();
    try {
      Thread.sleep(10000);
      Fetcher.main(args);
    } finally {
      lock.unlock();
    }
  }
}