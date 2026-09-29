package jetbrains.buildServer.buildTriggers.vcs.git.health;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import jetbrains.buildServer.buildTriggers.vcs.git.GitRemoteUrlInspector;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusItem;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusItemConsumer;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusReport;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusScope;
import jetbrains.buildServer.serverSide.healthStatus.ItemCategory;
import jetbrains.buildServer.serverSide.healthStatus.ItemSeverity;
import org.jetbrains.annotations.NotNull;

/**
 * Reports, once globally (no VCS root involved), when the {@code teamcity.git.additionalAllowedUrlTransports}
 * override currently permits a known-dangerous transport ({@code ext}/{@code fd}). Unlike
 * {@link GitDisallowedTransportUrlHealthReport}, this is not a per-root problem: which roots happen to use
 * the transport today doesn't change how dangerous permitting it is, and any root broken by the allowlist
 * already surfaces there.
 */
public class GitDangerousTransportPermittedHealthReport extends HealthStatusReport {

  public static final String TYPE = "GitDangerousTransportPermittedHealthReport";

  private static final String DATA_TRANSPORTS = "transports";

  private static final Set<String> KNOWN_DANGEROUS_TRANSPORTS = new LinkedHashSet<>(Arrays.asList("ext", "fd"));

  private static final ItemCategory CATEGORY = new ItemCategory(
    TYPE + ".category",
    "Git URL transport allowlist permits a dangerous transport",
    ItemSeverity.WARN
  );

  @NotNull
  @Override
  public String getType() {
    return TYPE;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Detect a dangerous transport permitted by the Git URL transport allowlist";
  }

  @NotNull
  @Override
  public Collection<ItemCategory> getCategories() {
    return Collections.singletonList(CATEGORY);
  }

  @Override
  public boolean canReportItemsFor(@NotNull HealthStatusScope scope) {
    return scope.isItemWithSeverityAccepted(CATEGORY.getSeverity());
  }

  @Override
  public void report(@NotNull HealthStatusScope scope, @NotNull HealthStatusItemConsumer consumer) {
    if (!scope.globalItems()) return;

    Set<String> permitted = new LinkedHashSet<>(KNOWN_DANGEROUS_TRANSPORTS);
    permitted.retainAll(GitRemoteUrlInspector.getEffectiveAllowedTransports());
    if (permitted.isEmpty()) return;

    consumer.consumeGlobal(new HealthStatusItem(
      TYPE + ".item",
      CATEGORY,
      Collections.singletonMap(DATA_TRANSPORTS, (Object) permitted)
    ));
  }
}
