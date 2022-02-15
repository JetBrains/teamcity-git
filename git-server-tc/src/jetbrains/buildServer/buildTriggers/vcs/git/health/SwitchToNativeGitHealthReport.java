package jetbrains.buildServer.buildTriggers.vcs.git.health;

import java.util.Collection;
import java.util.Collections;
import jetbrains.buildServer.buildTriggers.vcs.git.GitDiagnosticsTab;
import jetbrains.buildServer.buildTriggers.vcs.git.GitMainConfigProcessor;
import jetbrains.buildServer.serverSide.healthStatus.*;
import org.jetbrains.annotations.NotNull;

public class SwitchToNativeGitHealthReport extends HealthStatusReport {
  public static final String TYPE = "SwitchToNativeGitHealthReport";
  private static final String DESCRIPTION = "Switch to native git";
  private static final ItemCategory CATEGORY = new ItemCategory( "SwitchToNativeGitHealthCategory", DESCRIPTION, ItemSeverity.WARN);

  private final GitMainConfigProcessor myMainConfigProcessor;

  public SwitchToNativeGitHealthReport(@NotNull GitMainConfigProcessor mainConfigProcessor) {
    myMainConfigProcessor = mainConfigProcessor;
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
  public void report(@NotNull HealthStatusScope scope, @NotNull HealthStatusItemConsumer resultConsumer) {
    if (myMainConfigProcessor.isNativeGitOperationsEnabled() || !GitDiagnosticsTab.isEnabled()) return;
    resultConsumer.consumeGlobal(new HealthStatusItem("SwitchToNativeGitId", CATEGORY, Collections.emptyMap()));
  }
}
