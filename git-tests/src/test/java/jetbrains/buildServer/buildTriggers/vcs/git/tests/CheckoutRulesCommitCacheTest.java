package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import java.io.File;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.buildTriggers.vcs.git.CheckoutRulesCommitCache;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsRoot;
import jetbrains.buildServer.buildTriggers.vcs.git.SubmodulesCheckoutPolicy;
import jetbrains.buildServer.vcs.CheckoutRules;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import static org.assertj.core.api.BDDAssertions.then;

public class CheckoutRulesCommitCacheTest extends BaseTestCase {
  @Test
  public void cache_is_available_for_submodule_checkout() {
    CheckoutRulesCommitCache cache = new CheckoutRulesCommitCache();
    GitVcsRoot gitRoot = createGitRoot(SubmodulesCheckoutPolicy.CHECKOUT);
    CheckoutRulesCommitCache.Value value = new CheckoutRulesCommitCache.Value(new String[]{"parent"}, 1);

    cache.put(gitRoot, CheckoutRules.DEFAULT, "revision", value);

    then(cache.get(gitRoot, CheckoutRules.DEFAULT, "revision")).isSameAs(value);
  }

  @Test
  public void cache_entries_are_separated_by_submodule_checkout_policy() {
    CheckoutRulesCommitCache cache = new CheckoutRulesCommitCache();
    GitVcsRoot checkoutSubmodulesRoot = createGitRoot(SubmodulesCheckoutPolicy.CHECKOUT);
    GitVcsRoot ignoreSubmodulesRoot = createGitRoot(SubmodulesCheckoutPolicy.IGNORE);
    CheckoutRulesCommitCache.Value value = new CheckoutRulesCommitCache.Value(new String[]{"parent"}, 1);

    cache.put(checkoutSubmodulesRoot, CheckoutRules.DEFAULT, "revision", value);

    then(cache.get(ignoreSubmodulesRoot, CheckoutRules.DEFAULT, "revision")).isNull();
  }

  @NotNull
  private GitVcsRoot createGitRoot(@NotNull SubmodulesCheckoutPolicy submodulesCheckoutPolicy) {
    GitVcsRoot gitRoot = Mockito.mock(GitVcsRoot.class);
    Mockito.doReturn(new File("git-test")).when(gitRoot).getRepositoryDir();
    Mockito.doReturn(submodulesCheckoutPolicy).when(gitRoot).getSubmodulesCheckoutPolicy();
    Mockito.doReturn(submodulesCheckoutPolicy != SubmodulesCheckoutPolicy.IGNORE).when(gitRoot).isCheckoutSubmodules();
    return gitRoot;
  }
}
