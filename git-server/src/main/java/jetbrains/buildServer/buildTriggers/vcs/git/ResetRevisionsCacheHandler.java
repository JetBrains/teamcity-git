

package jetbrains.buildServer.buildTriggers.vcs.git;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.util.cache.ResetCacheHandler;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static java.util.Collections.singletonList;

public class ResetRevisionsCacheHandler implements ResetCacheHandler {

  private final static Logger LOG = Logger.getInstance(ResetRevisionsCacheHandler.class.getName());
  private final static String CACHE_NAME = "git revisions cache";
  private final RevisionsCache myCache;

  public ResetRevisionsCacheHandler(@NotNull RevisionsCache cache) {
    myCache = cache;
  }

  @NotNull
  @Override
  public List<String> listCaches() {
    return singletonList(CACHE_NAME);
  }

  @Override
  public boolean isEmpty(@NotNull String name) {
    return false;
  }

  @Override
  public void resetCache(@NotNull String name) {
    synchronized (this) {
      LOG.info("Reset git revisions cache");
      myCache.reset();
    }
  }
}