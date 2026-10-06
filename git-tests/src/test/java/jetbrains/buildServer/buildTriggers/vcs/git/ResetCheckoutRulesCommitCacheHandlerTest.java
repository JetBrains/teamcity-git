package jetbrains.buildServer.buildTriggers.vcs.git;

import java.io.File;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.vcs.CheckoutRules;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;

public class ResetCheckoutRulesCommitCacheHandlerTest extends BaseTestCase {
  @Test
  public void reset_clears_checkout_rules_commit_cache() {
    CheckoutRulesCommitCache cache = new CheckoutRulesCommitCache();
    ResetCheckoutRulesCommitCacheHandler handler = new ResetCheckoutRulesCommitCacheHandler(cache);
    GitVcsRoot gitRoot = Mockito.mock(GitVcsRoot.class);
    Mockito.doReturn(new File("git-test")).when(gitRoot).getRepositoryDir();

    cache.put(gitRoot, CheckoutRules.DEFAULT, "revision", new CheckoutRulesCommitCache.Value(new String[0], 0));

    then(handler.listCaches()).containsExactly("git checkout rules commit cache");
    then(handler.isEmpty("git checkout rules commit cache")).isFalse();

    handler.resetCache("git checkout rules commit cache");

    then(handler.isEmpty("git checkout rules commit cache")).isTrue();
  }
}
