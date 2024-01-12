

package jetbrains.buildServer.buildTriggers.vcs.git.command;

import java.util.Map;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthSettings;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;

public interface AuthCommand<T extends BaseCommand> {

  @NotNull
  T setAuthSettings(@NotNull AuthSettings authSettings);

  @NotNull
  T setUseNativeSsh(boolean useNativeSsh);

  @NotNull
  T setTimeout(int timeout);

  T addPreAction(@NotNull Runnable action);

  T setRetryAttempts(int num);

  T trace(@NotNull Map<String, String> gitTraceEnv);

  T setRepoUrl(@NotNull URIish repoUrl);
}