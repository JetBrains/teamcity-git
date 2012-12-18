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

package jetbrains.buildServer.buildTriggers.vcs.git.patch;

import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsRoot;
import jetbrains.buildServer.vcs.patches.PatchBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.io.AutoCRLFOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.concurrent.Callable;

/**
* @author dmitry.neverov
*/
class LoadContentAction implements Callable<Void> {
  private final GitVcsRoot myRoot;
  private final PatchBuilder myBuilder;
  private final BuildPatchLogger myLogger;
  private Repository myRepository;
  private ObjectId myObjectId;
  private String myPath;
  private String myMappedPath;
  private String myMode;

  LoadContentAction(@NotNull GitVcsRoot root,
                    @NotNull PatchBuilder builder,
                    @NotNull BuildPatchLogger logger) {
    myRoot = root;
    myBuilder = builder;
    myLogger = logger;
  }

  LoadContentAction fromRepository(@NotNull Repository repository) {
    myRepository = repository;
    return this;
  }

  LoadContentAction withPath(@Nullable String path) {
    myPath = path;
    return this;
  }

  LoadContentAction withMappedPath(@NotNull String mappedPath) {
    myMappedPath = mappedPath;
    return this;
  }

  LoadContentAction withMode(@Nullable String mode) {
    myMode = mode;
    return this;
  }

  LoadContentAction withObjectId(@NotNull ObjectId objectId) {
    myObjectId = objectId;
    return this;
  }

  public Void call() throws Exception {
    InputStream objectStream = null;
    try {
      ObjectLoader loader = getObjectLoader();
      ByteArrayOutputStream out = new ByteArrayOutputStream((int) loader.getSize());
      OutputStream output = myRoot.isAutoCrlf() ? new AutoCRLFOutputStream(out) : out;
      loader.copyTo(output);
      output.flush();
      myBuilder.changeOrCreateBinaryFile(GitUtils.toFile(myMappedPath), myMode, new ByteArrayInputStream(out.toByteArray()), out.size());
      myLogger.logAddFile(myMappedPath, out.size());
    } catch (Error e) {
      myLogger.cannotLoadFile(myPath, myObjectId);
      throw e;
    } catch (Exception e) {
      myLogger.cannotLoadFile(myPath, myObjectId);
      throw e;
    } finally {
      if (objectStream != null)
        objectStream.close();
    }
    return null;
  }

  private ObjectLoader getObjectLoader() throws IOException {
    ObjectLoader loader = myRepository.open(myObjectId);
    if (loader == null)
      throw new IOException("Unable to find blob " + myObjectId.name() + (myPath == null ? "" : "(" + myPath + ")") + " in repository " + myRepository);
    return loader;
  }
}
