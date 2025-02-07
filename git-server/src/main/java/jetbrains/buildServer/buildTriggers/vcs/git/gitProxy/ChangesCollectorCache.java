package jetbrains.buildServer.buildTriggers.vcs.git.gitProxy;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.vcs.ModificationData;
import jetbrains.buildServer.vcs.RepositoryStateData;
import jetbrains.buildServer.vcs.VcsChange;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ChangesCollectorCache {

  @NotNull
  private final Cache<String, CacheEntry> myCache;
  @NotNull
  private final HashFunction myHashFunction;
  @NotNull
  private final Map<String, CompletableFuture<List<ModificationData>>> myRunningOperations;

  private static final String TTL_ACCESS_PROPERTY = "teamcity.git.changesCollection.cache.accessTtlSeconds";
  private static final int TTL_ACCESS_DEFAULT = 60;
  private static final String MAX_SIZE_MB_PROPERTY = "teamcity.git.changesCollection.cache.maxSizeMb";
  private static final int MAX_SIZE_MB_DEFAULT = 1024;

  private static final long GIT_COMMIT_ID_SIZE_BYTES = 120; // 40 chars(80 bytes) + 40 bytes approximate string overhead


  public ChangesCollectorCache() {
    myCache = CacheBuilder.newBuilder()
                          .expireAfterAccess(getAccessTtl(), TimeUnit.SECONDS)
                          .softValues()
                          .<String, CacheEntry>weigher((a, b) -> b.sizeKb)
                          .maximumWeight(getMaxSizeKb())
                          .build();
    myRunningOperations = new HashMap<>();
    myHashFunction = Hashing.murmur3_128();
  }

  /**
   * @param key should be calculated with {@link #getKey(RepositoryStateData, RepositoryStateData, String)}
   * @return future that should contain result or for which result should be set
   * <br>
   *  if the type is NEW,then the result should be calculated and set for the future
   *  <br>
   *  if the type is RUNNING, the calculation for the key is still in progress, future should be awaited
   *  <br>
   *  if the type is COMPLETED, the result is present in cache and can be retrieved immidiately
   */
  @NotNull
  public synchronized Result getOrCreateNew(@NotNull Key key, @NotNull VcsRoot vcsRoot) {
    //check if we have result in cache
    CacheEntry entry = myCache.getIfPresent(key.get());
    if (entry != null) {
      return new Result(entry.data, vcsRoot);
    }

    //check if some operation is running for this key
    if (myRunningOperations.containsKey(key.get())) {
      return new Result(ResultType.RUNNING, myRunningOperations.get(key.get()), vcsRoot);
    }

    // create new future, update cache when it is completed
    CompletableFuture<List<ModificationData>> future = new CompletableFuture<>();
    future.whenComplete((res, ex) -> {
      myRunningOperations.remove(key.get());
      if (res != null) {
        add(key, res, calculateSizeKb(res));
      }
    });
    myRunningOperations.put(key.get(), future);
    return new Result(ResultType.NEW, future, vcsRoot);
  }

  private synchronized void add(@NotNull Key key, @NotNull List<ModificationData> result, int sizeKb) {
    myCache.put(key.get(), new CacheEntry(result, sizeKb));
  }

  @NotNull
  public Key getKey(@NotNull RepositoryStateData fromState, @NotNull RepositoryStateData toState, @NotNull String repoUrl) {
    Hasher h = myHashFunction.newHasher();
    fromState.getBranchRevisions().entrySet().stream().sorted((a, b) -> a.getKey().compareTo(b.getKey())).forEach(entry -> {
      h.putUnencodedChars(entry.getKey());
      h.putUnencodedChars(entry.getValue());
    });

    toState.getBranchRevisions().entrySet().stream().sorted((a, b) -> a.getKey().compareTo(b.getKey()))
           .filter(entry -> !Objects.equals(entry.getValue(), fromState.getBranchRevisions().get(entry.getKey())))
           .forEach(entry -> {
             h.putUnencodedChars(entry.getKey());
             h.putUnencodedChars(entry.getValue());
           });

    return new Key(repoUrl + h.hash());
  }

  public static int calculateSizeKb(@NotNull List<ModificationData> data) {
    long size = 0;
    for (ModificationData md : data) {
      size += (md.getParentRevisions().size() + 2) * GIT_COMMIT_ID_SIZE_BYTES; // parents + verstion + display version
      for (Map.Entry<String, String> entry : md.getAttributes().entrySet()) {
        size += getStringSizeBytes(entry.getKey()) + getStringSizeBytes(entry.getValue());
      }
      size += getStringSizeBytes(md.getDescription());
      size += getStringSizeBytes(md.getUserName());
      for (VcsChange change : md.getChanges()) {
        size += getStringSizeBytes(change.getBeforeChangeRevisionNumber()) + getStringSizeBytes(change.getAfterChangeRevisionNumber());
        size += getStringSizeBytes(change.getFileName()) * 2; // file name + relative file name
      }
    }
    return (int)(size / 1024) + 1;
  }

  private static long getStringSizeBytes(@Nullable String s) {
    if (s == null) {
      return 0;
    }
    return s.length() * 2L + 40;
  }

  public static class Key {
    @NotNull
    private final String myKey;

    private Key(@NotNull String key) {
      myKey = key;
    }

    @NotNull
    private String get() {
      return myKey;
    }
  }

  public static class Result {
    @NotNull
    private final ResultType myType;
    @NotNull
    private final CompletableFuture<List<ModificationData>> myFuture;
    @NotNull
    private final VcsRoot myVcsRoot;

    private Result(@NotNull List<ModificationData> data, @NotNull VcsRoot vcsRoot) {
      myType = ResultType.COMPLETED;
      myFuture = CompletableFuture.completedFuture(data);
      myVcsRoot = vcsRoot;
    }

    private Result(@NotNull ResultType type, @NotNull CompletableFuture<List<ModificationData>> future, @NotNull VcsRoot vcsRoot) {
      myType = type;
      myFuture = future;
      myVcsRoot = vcsRoot;
    }

    @NotNull
    public ResultType getType() {
      return myType;
    }

    @NotNull
    public List<ModificationData> getResult(long timeoutSeconds) throws ExecutionException, InterruptedException, TimeoutException {
      return copyResult(myFuture.get(timeoutSeconds, TimeUnit.SECONDS), myVcsRoot);
    }

    public void complete(@NotNull List<ModificationData> result) {
      List<ModificationData> copy = copyResult(result, myVcsRoot);
      myFuture.complete(copy);
    }

    public void completeExceptionally(@NotNull Throwable ex) {
      myFuture.completeExceptionally(ex);
    }
  }

  public enum ResultType {
    COMPLETED,
    RUNNING,
    NEW
  }

  @NotNull
  private static List<ModificationData> copyResult(@NotNull List<ModificationData> result, @NotNull VcsRoot root) {
    List<ModificationData> copy = new ArrayList<>(result.size());
    for (ModificationData md : result) {
      copy.add(copyModificationData(md, root));
    }
    return copy;
  }

  @NotNull
  private static ModificationData copyModificationData(@NotNull ModificationData md, @NotNull VcsRoot vcsRoot) {
    ModificationData copy = new ModificationData(md.getVcsDate(), copyVcsChanges(md.getChanges()), md.getDescription(), md.getUserName(), vcsRoot, md.getVersion(), md.getDisplayVersion());
    copy.setAttributes(new HashMap<>(md.getAttributes()));
    return copy;
  }

  @NotNull
  private static List<VcsChange> copyVcsChanges(@NotNull List<VcsChange> changes) {
    List<VcsChange> copy = new ArrayList<>(changes.size());
    for (VcsChange change : changes) {
      copy.add(new VcsChange(change.getType(), change.getFileName(), change.getRelativeFileName(), change.getBeforeChangeRevisionNumber(), change.getAfterChangeRevisionNumber()));
    }
    return copy;
  }

  private static int getAccessTtl() {
    return TeamCityProperties.getInteger(TTL_ACCESS_PROPERTY, TTL_ACCESS_DEFAULT);
  }

  private static int getMaxSizeKb() {
    return TeamCityProperties.getInteger(MAX_SIZE_MB_PROPERTY, MAX_SIZE_MB_DEFAULT) * 1024;
  }

  private static class CacheEntry {
    @NotNull
    public final List<ModificationData> data;
    public final int sizeKb;

    CacheEntry(@NotNull List<ModificationData> data, int sizeKb) {
      this.data = data;
      this.sizeKb = sizeKb;
    }
  }
}
