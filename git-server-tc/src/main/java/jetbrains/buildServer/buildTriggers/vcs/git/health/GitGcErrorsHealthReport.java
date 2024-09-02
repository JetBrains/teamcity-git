

package jetbrains.buildServer.buildTriggers.vcs.git.health;

import jetbrains.buildServer.buildTriggers.vcs.git.GcErrors;
import jetbrains.buildServer.serverSide.healthStatus.*;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class GitGcErrorsHealthReport extends HealthStatusReport {

  private static final String PREFIX = "gitGcError";
  static final String REPORT_TYPE = PREFIX + "HealthReport";
  private static final ItemCategory CATEGORY = new ItemCategory(PREFIX + "HealthCategory", "Git garbage collection error", ItemSeverity.WARN);
  static final String ERRORS_KEY = "errors";

  private final GcErrors myGcErrors;

  public GitGcErrorsHealthReport(@NotNull GcErrors gcErrors) {
    myGcErrors = gcErrors;
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
    return scope.globalItems() && scope.isItemWithSeverityAccepted(ItemSeverity.WARN);
  }

  @Override
  public void report(@NotNull HealthStatusScope scope, @NotNull HealthStatusItemConsumer resultConsumer) {
    Map<File, String> errors = myGcErrors.getErrors();
    if (!errors.isEmpty()) {
      Map<String, Object> data = new HashMap<>();
      data.put(ERRORS_KEY, errors);
      resultConsumer.consumeGlobal(new HealthStatusItem(PREFIX + "HealthItemId", CATEGORY, ItemSeverity.WARN, data));
    }
  }
}