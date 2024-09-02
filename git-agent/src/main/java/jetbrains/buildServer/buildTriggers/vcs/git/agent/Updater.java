

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import jetbrains.buildServer.vcs.VcsException;

/**
 * @author dmitry.neverov
 */
public interface Updater {

  void update() throws VcsException;

}