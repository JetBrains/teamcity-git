package jetbrains.buildServer.buildTriggers.vcs.git;

import java.util.EventListener;
import org.jetbrains.annotations.NotNull;

/**
 * Receives the events of a fetch from the fetch command, see {@link FetchSettings#addListener}.
 * The methods do nothing by default, so that a listener implements only the events it needs and new events can be added.
 */
public interface FetchListener extends EventListener {

  /**
   * @param fetchMode the kind of fetch the fetch command uses, it can differ from {@link FetchSettings#getFetchMode()}:
   *                  e.g. JGit fetches the refspecs whatever the mode is
   */
  default void fetchModeUsed(@NotNull FetchSettings.FetchMode fetchMode) {
  }
}
