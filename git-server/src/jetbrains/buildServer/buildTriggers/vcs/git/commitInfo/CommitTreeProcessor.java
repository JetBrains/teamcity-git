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

package jetbrains.buildServer.buildTriggers.vcs.git.commitInfo;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.buildTriggers.vcs.git.submodules.SubmodulesConfig;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.eclipse.jgit.lib.Constants.DOT_GIT_MODULES;

/**
 * Created 20.02.14 12:37
 *
 * @author Eugene Petrenko (eugene.petrenko@jetbrains.com)
 */
public class CommitTreeProcessor {
  private static final Logger LOG = Logger.getInstance(CommitTreeProcessor.class.getName());

  private final DotGitModulesResolver myModules;
  private final ObjectReader myReader;

  public CommitTreeProcessor(@NotNull final DotGitModulesResolver modules,
                             @NotNull final Repository db) {
    myModules = modules;
    myReader = db.getObjectDatabase().newCachedDatabase().newReader();
  }

  @Nullable
  public SubInfo processCommitTree(@NotNull final RevCommit commit) {
    final TreeResult tree;
    try {
      tree = processTree(commit.getTree(), "", null);
    } catch (IOException e) {
      LOG.info("Failed to process commit " + commit + ". " + e.getMessage(), e);
      return null;
    }

    final SubmodulesConfig config = tree.getConfig();
    final Map<String, AnyObjectId> subStates = tree.getSubmoduleToPath();
    if (config == null || subStates.isEmpty()) return null;

    return new SubInfo() {
      @NotNull
      public Map<String, AnyObjectId> getSubmoduleToPath() {
        return subStates;
      }

      @NotNull
      public SubmodulesConfig getConfig() {
        return config;
      }
    };
  }

  @NotNull
  private TreeResult processTree(@NotNull final AnyObjectId tree,
                                 @NotNull final String basePathPrefix,
                                 @Nullable final SubmodulesConfig baseConfig) throws IOException {
    final Map<String, AnyObjectId> pathToSubmoduleHash = new HashMap<String, AnyObjectId>();
    final Map<String, AnyObjectId> childTrees = new HashMap<String, AnyObjectId>();
    SubmodulesConfig submodules = baseConfig;

    final CanonicalTreeParser ps = new CanonicalTreeParser();
    ps.reset(myReader, tree);
    if (ps.eof()) return EMPTY;

    for (; !ps.eof(); ps.next()) {
      final FileMode mode = ps.getEntryFileMode();
      if (mode == FileMode.GITLINK) {
        pathToSubmoduleHash.put(basePathPrefix + ps.getEntryPathString(), ps.getEntryObjectId());
      }

      if (mode == FileMode.TREE) {
        childTrees.put(basePathPrefix + ps.getEntryPathString(), ps.getEntryObjectId());
      }

      if (submodules == null && mode == FileMode.REGULAR_FILE && DOT_GIT_MODULES.equals(ps.getEntryPathString())) {
        submodules = myModules.forBlob(ps.getEntryObjectId());
      }
    }

    if (submodules == null) return EMPTY;
    for (Map.Entry<String, AnyObjectId> e : childTrees.entrySet()) {
      final String path = e.getKey();
      if (!submodules.containsSubmodule(path)) continue;

      //TODO: may also cache parsed sub-strees as well
      final TreeResult sub = processTree(e.getValue(), path + "/", submodules);
      pathToSubmoduleHash.putAll(sub.getSubmoduleToPath());
    }

    return new TreeResult(pathToSubmoduleHash, submodules);
  }

  public static final TreeResult EMPTY = new TreeResult(Collections.<String, AnyObjectId>emptyMap(), null);

  public static class TreeResult {
    private final Map<String, AnyObjectId> mySubmoduleToPath;
    private final SubmodulesConfig myConfig;

    public TreeResult(@NotNull final Map<String, AnyObjectId> submoduleToPath,
                      @Nullable final SubmodulesConfig config) {
      mySubmoduleToPath = submoduleToPath;
      myConfig = config;
    }

    @NotNull
    public Map<String, AnyObjectId> getSubmoduleToPath() {
      return mySubmoduleToPath;
    }

    @Nullable
    public SubmodulesConfig getConfig() {
      return myConfig;
    }
  }
}
