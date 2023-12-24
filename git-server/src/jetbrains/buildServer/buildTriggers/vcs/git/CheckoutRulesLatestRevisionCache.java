package jetbrains.buildServer.buildTriggers.vcs.git;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.RevisionMatchedByCheckoutRulesCalculator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CheckoutRulesLatestRevisionCache {
  public static final String CHECKOUT_RULES_REVISION_CACHE_ENABLED = "teamcity.git.checkoutRulesRevisionCache.enabled";
  private final Cache<Key, Value> myCache;

  public CheckoutRulesLatestRevisionCache() {
    myCache = Caffeine.newBuilder()
                      .expireAfterAccess(TeamCityProperties.getInteger("teamcity.git.checkoutRulesRevisionCache.expirationTime", 8*3600), TimeUnit.SECONDS)
                      .maximumSize(TeamCityProperties.getInteger("teamcity.git.checkoutRulesRevisionCache.size", 10_000))
                      .executor(Runnable::run)
                      .build();
  }

  @Nullable
  public Value getCachedValue(@NotNull GitVcsRoot vcsRoot, @NotNull String branchName, @NotNull CheckoutRules checkoutRules) {
    if (!TeamCityProperties.getBoolean(CHECKOUT_RULES_REVISION_CACHE_ENABLED)) {
      myCache.invalidateAll();
      return null;
    }

    return myCache.getIfPresent(makeKey(vcsRoot, branchName, checkoutRules));
  }

  public void storeInCache(@NotNull GitVcsRoot vcsRoot, @NotNull CheckoutRules checkoutRules,
                           @NotNull String startRevision,
                           @NotNull String branchName,
                           @NotNull Collection<String> stopRevisions,
                           @NotNull RevisionMatchedByCheckoutRulesCalculator.Result result) {
    Key key = makeKey(vcsRoot, branchName, checkoutRules);
    Value value = new Value(startRevision, stopRevisions, result.getRevision(), result.getReachableStopRevisions());
    myCache.put(key, value);
  }

  @NotNull
  private Key makeKey(@NotNull GitVcsRoot vcsRoot, @NotNull String branchName, @NotNull CheckoutRules checkoutRules) {
    return new Key(vcsRoot.getRepositoryDir(), branchName, checkoutRules);
  }

  private static class Key {
    private final String myBranchName;
    private final CheckoutRules myCheckoutRules;
    private final File myMirror;

    public Key(@NotNull File mirror, @NotNull String branchName, @NotNull CheckoutRules checkoutRules) {
      myBranchName = branchName;
      myCheckoutRules = checkoutRules;
      myMirror = mirror;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Key)) return false;
      Key key = (Key)o;
      return Objects.equals(myBranchName, key.myBranchName) && Objects.equals(myCheckoutRules, key.myCheckoutRules) &&
             Objects.equals(myMirror, key.myMirror);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myBranchName, myCheckoutRules, myMirror);
    }
  }

  public static class Value {
    public final String myStartRevision;
    public final String myComputedRevision;
    public final List<String> myReachedStopRevisions;
    public final Collection<String> myStopRevisions;

    public Value(@NotNull String startRevision, @NotNull Collection<String> stopRevisions, @Nullable String computedRevision, @NotNull List<String> reachedStopRevisions) {
      myStartRevision = startRevision;
      myStopRevisions = stopRevisions;
      myComputedRevision = computedRevision;
      myReachedStopRevisions = reachedStopRevisions;
    }
  }
}
