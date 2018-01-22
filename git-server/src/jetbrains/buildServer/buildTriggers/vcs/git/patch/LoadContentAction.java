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

package jetbrains.buildServer.buildTriggers.vcs.git.patch;

import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsRoot;
import jetbrains.buildServer.vcs.patches.PatchBuilder;
import jetbrains.buildServer.vcs.patches.PatchBuilderContentInputStream;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.io.AutoCRLFInputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;

/**
* @author dmitry.neverov
*/
public class LoadContentAction implements Callable<Void> {
  @NotNull private final ContentLoaderFactory myContentFactory;
  private final GitVcsRoot myRoot;
  private final PatchBuilder myBuilder;
  private final BuildPatchLogger myLogger;
  private final PatchFileAction myFileAction;
  private final Repository myRepository;
  private final ObjectId myObjectId;
  private final String myPath;
  private final String myMappedPath;
  private final String myMode;

  public LoadContentAction(@NotNull final ContentLoaderFactory contentFactory,
                           final GitVcsRoot root,
                           final PatchBuilder builder,
                           final BuildPatchLogger logger,
                           final PatchFileAction fileAction,
                           final Repository repository,
                           final ObjectId objectId,
                           final String path,
                           final String mappedPath,
                           final String mode) {
    myContentFactory = contentFactory;
    myRoot = root;
    myBuilder = builder;
    myLogger = logger;
    myFileAction = fileAction;
    myRepository = repository;
    myObjectId = objectId;
    myPath = path;
    myMappedPath = mappedPath;
    myMode = mode;
  }

  public Void call() throws Exception {
    myFileAction.call("CREATE", myMappedPath);
    InputStream objectStream = null;
    final long size;
    try {
      final ObjectLoader loader = getObjectLoader();
      if (myRoot.isIncludeContentHashes()) {
        size = loader.getSize();
        objectStream = new LazyInputStream() {
          @NotNull
          @Override
          protected InputStream openStream() throws IOException {
            return LoadContentAction.this.openContentStream(loader);
          }

          @Nullable
          @Override
          public String getContentHash() {
            return myObjectId.toObjectId().name();
          }
        };
      } else {
        size = getStreamSize(myRoot, loader);
        objectStream = getObjectStream(myRoot, loader);
      }
      myBuilder.changeOrCreateBinaryFile(GitUtils.toFile(myMappedPath), myMode, objectStream, size);
      myLogger.logAddFile(myMappedPath, size);
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

  @NotNull
  protected ObjectLoader getObjectLoader() throws IOException {
    ObjectLoader loader = myContentFactory.open(myRepository, myObjectId);
    if (loader == null)
      throw new IOException("Unable to find blob " + myObjectId.name() + (myPath == null ? "" : "(" + myPath + ")") + " in repository " + myRepository);
    return loader;
  }

  private long getStreamSize(@NotNull GitVcsRoot root, @NotNull ObjectLoader loader) throws IOException {
    if (!root.isAutoCrlf())
      return loader.getSize();

    InputStream objectStream = null;
    try {
      objectStream = openContentStream(loader);
      objectStream = new AutoCRLFInputStream(objectStream, true);
      int count;
      int size = 0;
      byte[] buf = new byte[8096];
      while ((count = objectStream.read(buf)) != -1) {
        size += count;
      }
      return size;
    } finally {
      if (objectStream != null)
        objectStream.close();
    }
  }

  @NotNull
  private InputStream getObjectStream(@NotNull final GitVcsRoot root, @NotNull final ObjectLoader loader) throws IOException {
    final InputStream stream = openContentStream(loader);
    if (!root.isAutoCrlf())
      return stream;
    return new AutoCRLFInputStream(stream, true);
  }

  @NotNull
  private InputStream openContentStream(@NotNull final ObjectLoader loader) throws IOException {
    return loader.isLarge() ? loader.openStream() : new ByteArrayInputStream(loader.getCachedBytes());
  }

  private static abstract class LazyInputStream extends InputStream implements PatchBuilderContentInputStream {
    private volatile InputStream myLazyStream;

    @NotNull
    private InputStream getHost() throws IOException {
      if (myLazyStream != null) return myLazyStream;
      synchronized (this) {
        if (myLazyStream != null) return myLazyStream;
        myLazyStream = openStream();
      }
      return myLazyStream;
    }

    @NotNull
    protected abstract InputStream openStream() throws IOException;

    @Override
    public int read() throws IOException {
      return getHost().read();
    }

    @Override
    public int read(@NotNull final byte[] b) throws IOException {
      return getHost().read(b);
    }

    @Override
    public int read(@NotNull final byte[] b, final int off, final int len) throws IOException {
      return getHost().read(b, off, len);
    }

    @Override
    public int available() throws IOException {
      return getHost().available();
    }

    @Override
    public synchronized void close() throws IOException {
      if (myLazyStream != null) {
        myLazyStream.close();
      }
    }
  }
}
