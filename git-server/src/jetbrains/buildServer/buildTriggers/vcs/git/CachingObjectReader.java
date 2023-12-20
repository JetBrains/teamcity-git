package jetbrains.buildServer.buildTriggers.vcs.git;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.jetbrains.annotations.NotNull;

public class CachingObjectReader extends ObjectReader {
  private final ObjectReader myDelegate;
  private final Map<String, ObjectLoader> myCache;
  private final int myMaxCachedObjectSize;
  private final int myMaxCachedObjects;
  private boolean myCachingEnabled;

  public CachingObjectReader(@NotNull ObjectReader delegate, int maxCachedObjects, int maxCachedObjectSize) {
    myDelegate = delegate;

    myMaxCachedObjects = maxCachedObjects;
    myMaxCachedObjectSize = maxCachedObjectSize;
    myCache = new HashMap<>();
  }

  @Override
  public ObjectReader newReader() {
    return myDelegate.newReader();
  }

  @Override
  public Collection<ObjectId> resolve(AbbreviatedObjectId id) throws IOException {
    return myDelegate.resolve(id);
  }

  @Override
  public ObjectLoader open(AnyObjectId objectId, int typeHint) throws MissingObjectException, IncorrectObjectTypeException, IOException {
    if (!(objectId instanceof RevCommit)) {
      return myDelegate.open(objectId, typeHint);
    }

    ObjectLoader cached = myCache.get(objectId.name());
    if (cached != null) {
      return cached;
    }

    ObjectLoader objectLoader = myDelegate.open(objectId, typeHint);
    if (myCachingEnabled && objectLoader.getSize() < myMaxCachedObjectSize && myCache.size() < myMaxCachedObjects) {
      myCache.put(objectId.name(), objectLoader);
    }
    return objectLoader;
  }

  @Override
  public Set<ObjectId> getShallowCommits() throws IOException {
    return myDelegate.getShallowCommits();
  }

  @Override
  public void close() {
    myDelegate.close();
  }

  public void removeCached(@NotNull AnyObjectId objectId) {
    removeCached(objectId.name());
  }

  public void removeCached(@NotNull String revision) {
    myCache.remove(revision);
  }

  public void setCachingEnabled(boolean enabled) {
    myCachingEnabled = enabled;
  }

  public void clearCache() {
    printStatistics();
    myCache.clear();
  }

  public void printStatistics() {
    System.out.println("Total objects cache size: " + myCache.size() + ", estimated occupied memory: " + myCache.size() * myMaxCachedObjectSize);
  }
}
