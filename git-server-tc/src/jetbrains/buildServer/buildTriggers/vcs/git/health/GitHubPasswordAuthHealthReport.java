package jetbrains.buildServer.buildTriggers.vcs.git.health;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.buildTriggers.vcs.git.GitHubPasswordAuthRootRegistry;
import jetbrains.buildServer.buildTriggers.vcs.git.GitHubPasswordAuthRootRegistryFactory;
import jetbrains.buildServer.serverSide.healthStatus.*;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.jetbrains.annotations.NotNull;

import static jetbrains.buildServer.buildTriggers.vcs.git.GitHubPasswordAuthRootRegistry.isGitRoot;
import static jetbrains.buildServer.buildTriggers.vcs.git.GitHubPasswordAuthRootRegistryFactory.REPORT_TYPE;
import static jetbrains.buildServer.buildTriggers.vcs.git.GitHubPasswordAuthRootRegistryFactory.isEnabled;

public class GitHubPasswordAuthHealthReport extends HealthStatusReport {

  public static final ItemSeverity SEVERITY = ItemSeverity.WARN;
  public static final String VCS_ROOT_KEY = "vcsRoot";
  private static final ItemCategory CATEGORY = new ItemCategory(REPORT_TYPE + "HealthCategory",
                                                                "Vcs root uses password authentication with github.com", SEVERITY,
                                                                "VCS roots using deprecated password authentication with github.com will soon stop working",
                                                                "https://github.blog/2020-12-15-token-authentication-requirements-for-git-operations/#timeline");

  @NotNull private final GitHubPasswordAuthRootRegistry myRegistry;

  public GitHubPasswordAuthHealthReport(@NotNull GitHubPasswordAuthRootRegistryFactory registryFactory) {
    myRegistry = registryFactory.createRegistry();
  }

  @NotNull
  @Override
  public String getType() {
    return REPORT_TYPE;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return CATEGORY.getName();
  }

  @NotNull
  @Override
  public Collection<ItemCategory> getCategories() {
    return Collections.singleton(CATEGORY);
  }

  @Override
  public boolean canReportItemsFor(@NotNull HealthStatusScope scope) {
    return isEnabled() && scope.isItemWithSeverityAccepted(ItemSeverity.WARN) && scope.getVcsRoots().stream().anyMatch(GitHubPasswordAuthRootRegistry::isGitRoot);
  }

  @Override
  public void report(@NotNull HealthStatusScope scope, @NotNull HealthStatusItemConsumer resultConsumer) {
    if (!isEnabled()) return;

    for (SVcsRoot root : scope.getVcsRoots()) {
      if (!isGitRoot(root) || !myRegistry.containsVcsRoot(root.getId())) continue;

      final Map<String, Object> additionalData = new HashMap<String, Object>();
      additionalData.put(VCS_ROOT_KEY, root);
      resultConsumer.consumeForVcsRoot(root, new HealthStatusItem(REPORT_TYPE + root.getId(), CATEGORY, SEVERITY, additionalData));
    }
  }
}
