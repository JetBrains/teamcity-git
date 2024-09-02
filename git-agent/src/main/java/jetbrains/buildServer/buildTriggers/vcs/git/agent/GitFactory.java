

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import java.io.File;
import org.jetbrains.annotations.NotNull;

/**
 * @author dmitry.neverov
 */
public interface GitFactory {

  @NotNull
  AgentGitFacade create(@NotNull File repositoryDir);

}