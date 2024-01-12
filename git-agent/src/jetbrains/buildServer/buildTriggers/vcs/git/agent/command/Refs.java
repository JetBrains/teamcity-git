

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command;

import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitUtilsAgent;
import org.eclipse.jgit.lib.Ref;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Collection of refs in a repository
 */
public class Refs {

  private final Map<String, Ref> myRefs = new HashMap<String, Ref>();

  public Refs(@NotNull final List<Ref> refs) {
    for (Ref r : refs)
      myRefs.put(r.getName(), r);
  }

  public Refs(@NotNull Map<String, Ref> refMap) {
    myRefs.putAll(refMap);
  }

  public boolean isOutdated(@NotNull Ref ref) {
    Ref myRef = myRefs.get(ref.getName());
    if (myRef == null) //every ref is outdated if it is not among refs in repository
      return true;
    //tag also is outdated if its revision changed (because git doesn't update tags):
    return GitUtilsAgent.isTag(ref) && !myRef.getObjectId().equals(ref.getObjectId());
  }

  public Collection<Ref> list() {
    return myRefs.values();
  }

  public boolean isEmpty() {
    return myRefs.isEmpty();
  }
}