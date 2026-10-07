package jetbrains.buildServer.buildTriggers.vcs.git;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.TimeIntervalAction;
import jetbrains.buildServer.util.impl.Lazy;
import jetbrains.buildServer.vcs.CheckoutRules;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CheckoutRulesCommitCache {
  private static final String ENABLED_PROPERTY = "teamcity.git.checkoutRulesRevision.commitCache.enabled";
  private static final String EXPIRATION_PROPERTY = "teamcity.git.checkoutRulesRevision.commitCache.expiration";
  private static final String RESET_INTERVAL_PROPERTY = "teamcity.git.checkoutRulesRevision.commitCache.resetInterval";
  private static final String SIZE_PROPERTY = "teamcity.git.checkoutRulesRevision.commitCache.size";
  private static final long DEFAULT_EXPIRATION_MILLIS = TimeUnit.HOURS.toMillis(8);
  private static final long DEFAULT_RESET_INTERVAL_MILLIS = TimeUnit.SECONDS.toMillis(30);
  private static final int DEFAULT_SIZE = 100_000;

  static final CheckoutRulesCommitCache EMPTY = new CheckoutRulesCommitCache() {
    @Nullable
    @Override
    public Value get(@NotNull GitVcsRoot gitRoot, @NotNull CheckoutRules checkoutRules, @NotNull String revision) {
      return null;
    }

    @Override
    public void put(@NotNull GitVcsRoot gitRoot, @NotNull CheckoutRules checkoutRules, @NotNull String revision, @NotNull Value value) {
    }
  };

  private final Lazy<CacheState> myState;
  private final TimeIntervalAction myResetStateAction;

  public CheckoutRulesCommitCache() {
    myState = Lazy.create(() -> createState(readConfiguration()));
    myResetStateAction = new TimeIntervalAction(() -> TeamCityProperties.getIntervalMilliseconds(RESET_INTERVAL_PROPERTY, DEFAULT_RESET_INTERVAL_MILLIS),
                                                myState::resetValue);
  }

  @Nullable
  public Value get(@NotNull GitVcsRoot gitRoot, @NotNull CheckoutRules checkoutRules, @NotNull String revision) {
    CacheState state = getCurrentState();
    if (!isEnabled(state)) return null;
    return state.myCache.getIfPresent(new Key(gitRoot.getRepositoryDir().getName(),
                                              gitRoot.getSubmodulesCheckoutPolicy(),
                                              checkoutRules,
                                              revision));
  }

  public void put(@NotNull GitVcsRoot gitRoot, @NotNull CheckoutRules checkoutRules, @NotNull String revision, @NotNull Value value) {
    CacheState state = getCurrentState();
    if (!isEnabled(state)) return;
    state.myCache.put(new Key(gitRoot.getRepositoryDir().getName(),
                              gitRoot.getSubmodulesCheckoutPolicy(),
                              checkoutRules,
                              revision), value);
  }

  public long getHitCount() {
    return getCurrentState().myCache.stats().hitCount();
  }

  public boolean isEmpty() {
    return getCurrentState().myCache.estimatedSize() == 0;
  }

  public void reset() {
    myState.resetValue();
  }

  private boolean isEnabled(@NotNull CacheState state) {
    boolean enabled = state.myConfiguration.myEnabled;
    if (!enabled) {
      state.myCache.invalidateAll();
    }
    return enabled;
  }

  @NotNull
  private CacheState getCurrentState() {
    myResetStateAction.execute();
    return myState.getValue();
  }

  @NotNull
  private Configuration readConfiguration() {
    return new Configuration(TeamCityProperties.getBooleanOrTrue(ENABLED_PROPERTY),
                             TeamCityProperties.getIntervalMilliseconds(EXPIRATION_PROPERTY, DEFAULT_EXPIRATION_MILLIS),
                             TeamCityProperties.getInteger(SIZE_PROPERTY, DEFAULT_SIZE));
  }

  @NotNull
  private static CacheState createState(@NotNull Configuration configuration) {
    Cache<Key, Value> cache = Caffeine.newBuilder()
                                      .executor(Runnable::run)
                                      .expireAfterAccess(configuration.myExpirationMillis, TimeUnit.MILLISECONDS)
                                      .maximumSize(configuration.myMaximumSize)
                                      .recordStats()
                                      .build();
    return new CacheState(configuration, cache);
  }

  private static class Configuration {
    private final boolean myEnabled;
    private final long myExpirationMillis;
    private final int myMaximumSize;

    private Configuration(boolean enabled, long expirationMillis, int maximumSize) {
      myEnabled = enabled;
      myExpirationMillis = expirationMillis;
      myMaximumSize = maximumSize;
    }

  }

  private static class CacheState {
    private final Configuration myConfiguration;
    private final Cache<Key, Value> myCache;

    private CacheState(@NotNull Configuration configuration, @NotNull Cache<Key, Value> cache) {
      myConfiguration = configuration;
      myCache = cache;
    }
  }

  public static class Value {
    private static final String[] NO_AFFECTED_PARENTS = new String[0];

    private final String[] myAffectedParents;

    public Value(@NotNull String[] affectedParents, int affectedParentsCount) {
      myAffectedParents = affectedParentsCount == 0 ? NO_AFFECTED_PARENTS :
                          affectedParentsCount == affectedParents.length ? affectedParents :
                          Arrays.copyOf(affectedParents, affectedParentsCount);
    }

    boolean isParentAffected(@NotNull String revision) {
      for (String affectedParent : myAffectedParents) {
        if (affectedParent.equals(revision)) return true;
      }
      return false;
    }
  }

  private static class Key {
    private final String myRepositoryDirName;
    private final SubmodulesCheckoutPolicy mySubmodulesCheckoutPolicy;
    private final CheckoutRules myCheckoutRules;
    private final String myRevision;

    private Key(@NotNull String repositoryDirName,
                @NotNull SubmodulesCheckoutPolicy submodulesCheckoutPolicy,
                @NotNull CheckoutRules checkoutRules,
                @NotNull String revision) {
      myRepositoryDirName = repositoryDirName;
      mySubmodulesCheckoutPolicy = submodulesCheckoutPolicy;
      myCheckoutRules = checkoutRules;
      myRevision = revision;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Key)) return false;
      Key key = (Key)o;
      return Objects.equals(myRepositoryDirName, key.myRepositoryDirName) &&
             mySubmodulesCheckoutPolicy == key.mySubmodulesCheckoutPolicy &&
             Objects.equals(myCheckoutRules, key.myCheckoutRules) &&
             Objects.equals(myRevision, key.myRevision);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myRepositoryDirName, mySubmodulesCheckoutPolicy, myCheckoutRules, myRevision);
    }
  }
}
