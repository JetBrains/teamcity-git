/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import org.eclipse.jgit.lib.Ref;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Collection of tags in a repository
 */
public class Tags {

  private final Map<String, Ref> myTags = new HashMap<String, Ref>();

  public Tags(@NotNull final List<Ref> tags) {
    for (Ref r : tags)
      myTags.put(r.getName(), r);
  }

  public Tags(@NotNull Map<String, Ref> tagMap) {
    myTags.putAll(tagMap);
  }

  public boolean isOutdated(@NotNull Ref tag) {
    Ref myTag = myTags.get(tag.getName());
    if (myTag == null)
      return true;
    return !myTag.getObjectId().equals(tag.getObjectId());
  }

  public Collection<Ref> list() {
    return myTags.values();
  }
}
