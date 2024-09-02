

package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author dmitry.neverov
 */
public interface RepositoryManager extends MirrorManager {

  @NotNull
  List<File> getExpiredDirs();

  @NotNull
  Repository openRepository(@NotNull URIish fetchUrl) throws VcsException;

  @NotNull
  Repository openRepository(@NotNull File dir, @NotNull URIish fetchUrl) throws VcsException;

  void closeRepository(@NotNull Repository repository);

  @NotNull
  ReentrantLock getWriteLock(@NotNull File dir);

  @NotNull
  ReadWriteLock getRmLock(@NotNull File dir);

  <T> T runWithDisabledRemove(@NotNull File dir, @NotNull VcsOperation<T> operation) throws VcsException;

  void runWithDisabledRemove(@NotNull File dir, @NotNull VcsAction action) throws VcsException;

  void cleanLocksFor(@NotNull File dir);
}