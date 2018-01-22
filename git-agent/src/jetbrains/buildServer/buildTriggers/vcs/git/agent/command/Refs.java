/*
 * Copyright 2000-2018 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command;

import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
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
    return GitUtils.isTag(ref) && !myRef.getObjectId().equals(ref.getObjectId());
  }

  public Collection<Ref> list() {
    return myRefs.values();
  }

  public boolean isEmpty() {
    return myRefs.isEmpty();
  }
}
