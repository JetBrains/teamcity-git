

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command;

import org.eclipse.jgit.lib.Ref;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class ShowRefResult {

  private final Map<String, Ref> myValidRefs;
  private final Set<String> myInvalidRefs;
  private final Integer myExitCode;

  public ShowRefResult(@NotNull Map<String, Ref> validRefs,
                       @NotNull Set<String> invalidRefs,
                       int exitCode) {
    myValidRefs = validRefs;
    myInvalidRefs = invalidRefs;
    myExitCode = exitCode;
  }

  public ShowRefResult() {
    myValidRefs = Collections.emptyMap();
    myInvalidRefs = Collections.emptySet();
    myExitCode = null;
  }

  @NotNull
  public Map<String, Ref> getValidRefs() {
    return myValidRefs;
  }

  @NotNull
  public Set<String> getInvalidRefs() {
    return myInvalidRefs;
  }

  public boolean isFailed() {
    return myExitCode == null || myExitCode != 0;
  }
}