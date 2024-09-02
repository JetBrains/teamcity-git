

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import java.io.IOException;
import jetbrains.buildServer.buildTriggers.vcs.git.FetchCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.FetchSettings;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;

class FetchCommandCountDecorator implements FetchCommand {

  private final FetchCommand myDelegate;
  private int myFetchCount = 0;

  FetchCommandCountDecorator(FetchCommand delegate) {
    myDelegate = delegate;
  }

  public void fetch(@NotNull Repository db,
                    @NotNull URIish fetchURI,
                    @NotNull FetchSettings settings) throws IOException,
                                                            VcsException {
    myDelegate.fetch(db, fetchURI, settings);
    inc();
  }

  private synchronized void inc() {
    myFetchCount++;
  }

  public synchronized int getFetchCount() {
    return myFetchCount;
  }

  public synchronized void resetFetchCounter() {
    myFetchCount = 0;
  }
}