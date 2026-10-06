package jetbrains.buildServer.buildTriggers.vcs.git;

import com.intellij.openapi.diagnostic.Logger;
import java.util.List;
import jetbrains.buildServer.util.cache.ResetCacheHandler;
import org.jetbrains.annotations.NotNull;

import static java.util.Collections.singletonList;

public class ResetCheckoutRulesCommitCacheHandler implements ResetCacheHandler {
  private static final Logger LOG = Logger.getInstance(ResetCheckoutRulesCommitCacheHandler.class.getName());
  private static final String CACHE_NAME = "git checkout rules commit cache";

  private final CheckoutRulesCommitCache myCache;

  public ResetCheckoutRulesCommitCacheHandler(@NotNull CheckoutRulesCommitCache cache) {
    myCache = cache;
  }

  @NotNull
  @Override
  public List<String> listCaches() {
    return singletonList(CACHE_NAME);
  }

  @Override
  public boolean isEmpty(@NotNull String name) {
    return myCache.isEmpty();
  }

  @Override
  public void resetCache(@NotNull String name) {
    LOG.info("Reset git checkout rules commit cache");
    myCache.reset();
  }
}
