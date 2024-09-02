

package jetbrains.buildServer.buildTriggers.vcs.git.commitInfo;

import jetbrains.buildServer.vcs.CommitDataBean;
import org.eclipse.jgit.revwalk.RevCommit;
import org.jetbrains.annotations.NotNull;

/**
* Created 20.02.14 11:51
*
* @author Eugene Petrenko (eugene.petrenko@jetbrains.com)
*/
public interface SubmoduleResolver {
  void includeSubmodules(@NotNull final RevCommit commit,
                         @NotNull final CommitDataBean bean);

}