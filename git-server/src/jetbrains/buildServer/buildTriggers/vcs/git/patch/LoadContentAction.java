

package jetbrains.buildServer.buildTriggers.vcs.git.patch;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.patches.PatchBuilder;
import jetbrains.buildServer.vcs.patches.PatchBuilderContentInputStream;
import org.eclipse.jgit.lfs.Lfs;
import org.eclipse.jgit.lfs.LfsBlobLoader;
import org.eclipse.jgit.lfs.LfsPointer;
import org.eclipse.jgit.lfs.SmudgeFilter;
import org.eclipse.jgit.lfs.lib.AnyLongObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.io.AutoCRLFInputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  private final ServerPluginConfig myConfig;
  private final SshSessionMetaFactory mySshMetaFactory;

  public LoadContentAction(@NotNull final ContentLoaderFactory contentFactory,
                           final GitVcsRoot root,
                           final PatchBuilder builder,
                           final BuildPatchLogger logger,
                           final PatchFileAction fileAction,
                           final Repository repository,
                           final ObjectId objectId,
                           final String path,
                           final String mappedPath,
                           final String mode,
                           final ServerPluginConfig config,
                           final SshSessionMetaFactory sshMetaFactory) {
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
    myConfig = config;
    mySshMetaFactory = sshMetaFactory;
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
            return openContentStream(loader);
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
  protected ObjectLoader getObjectLoader() throws IOException, VcsException {
    ObjectLoader loader = myContentFactory.open(myRepository, myObjectId);
    if (loader == null)
      throw new IOException("Unable to find blob " + myObjectId.name() + (myPath == null ? "" : "(" + myPath + ")") + " in repository " + myRepository);

    return myConfig.downloadLfsObjectsForPatch() ? smudgeLfsBlob(loader) : loader;
  }

  @NotNull
  public ObjectLoader smudgeLfsBlob(@NotNull ObjectLoader loader)
    throws IOException, VcsException {
    if (loader.getSize() > LfsPointer.SIZE_THRESHOLD) {
      return loader;
    }

    try (final InputStream is = loader.openStream()) {
      LfsPointer ptr = LfsPointer.parseLfsPointer(is);
      if (ptr != null) {
        Lfs lfs = new Lfs(myRepository);
        AnyLongObjectId oid = ptr.getOid();
        Path mediaFile = lfs.getMediaFile(oid);
        if (!Files.exists(mediaFile)) {
          final SshSessionFactory oldFactory = SshSessionFactory.getInstance();
          final URIish url = myRoot.getRepositoryFetchURL().get();
          if (myRoot.isHttp() || !URIishHelperImpl.requiresCredentials(url)) {
            SmudgeFilter.downloadLfsResource(lfs, myRepository, ptr);
          } else {
            try {
              SshSessionFactory.setInstance(mySshMetaFactory.getSshSessionFactory(url, myRoot.getAuthSettings()));
              SmudgeFilter.downloadLfsResource(lfs, myRepository, ptr);
            } finally {
              SshSessionFactory.setInstance(oldFactory);
            }

          }
        }

        return new LfsBlobLoader(mediaFile);
      }
    }

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