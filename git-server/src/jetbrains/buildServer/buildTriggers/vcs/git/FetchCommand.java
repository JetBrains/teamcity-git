

package jetbrains.buildServer.buildTriggers.vcs.git;

import java.io.IOException;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;

/**
 * @author dmitry.neverov
 */
public interface FetchCommand extends GitCommand {

  /**
   * Makes a fetch into local repository (db.getDirectory() should be not null)
   */
  void fetch(@NotNull Repository db,
             @NotNull URIish fetchURI,
             @NotNull FetchSettings settings) throws IOException, VcsException;

}