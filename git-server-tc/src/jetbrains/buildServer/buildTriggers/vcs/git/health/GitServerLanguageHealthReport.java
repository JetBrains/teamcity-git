package jetbrains.buildServer.buildTriggers.vcs.git.health;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import jetbrains.buildServer.buildTriggers.vcs.git.GitRepoOperations;
import jetbrains.buildServer.serverSide.healthStatus.*;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

public class GitServerLanguageHealthReport extends HealthStatusReport {
  public static final String TYPE = "GitServerLanguageHealthReport";

  private static final String DESCRIPTION = "Native Git uses a language other than English.";

  private static final ItemCategory CATEGORY = new ItemCategory("GitServerLanguageHealthCategory", DESCRIPTION, ItemSeverity.WARN);

  private final GitRepoOperations myGitOperations;

  public GitServerLanguageHealthReport(GitRepoOperations gitOperations) {
    myGitOperations = gitOperations;
  }

  @NotNull
  @Override
  public String getType() {
    return TYPE;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return DESCRIPTION;
  }

  @NotNull
  @Override
  public Collection<ItemCategory> getCategories() {
    return Collections.singletonList(CATEGORY);
  }

  @Override
  public boolean canReportItemsFor(@NotNull HealthStatusScope scope) {
    return scope.globalItems() && scope.isItemWithSeverityAccepted(ItemSeverity.WARN);
  }

  @Override
  public void report(@NotNull HealthStatusScope scope, @NotNull HealthStatusItemConsumer consumer) {
    if (!myGitOperations.isNativeGitOperationsEnabled()) return;

    boolean isEnglishGit = false;
    try {
      isEnglishGit = myGitOperations.isEnglishGit();
    } catch (VcsException ignored) { }

    if (!isEnglishGit) {
      consumer.consumeGlobal(new HealthStatusItem("GitServerLanguageId", CATEGORY, new HashMap<>()));
    }
  }
}
