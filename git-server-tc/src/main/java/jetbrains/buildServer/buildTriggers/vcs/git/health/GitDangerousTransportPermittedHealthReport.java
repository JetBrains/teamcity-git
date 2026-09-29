package jetbrains.buildServer.buildTriggers.vcs.git.health;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import jetbrains.buildServer.buildTriggers.vcs.git.GitRemoteUrlInspector;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusItem;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusItemConsumer;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusReport;
import jetbrains.buildServer.serverSide.healthStatus.HealthStatusScope;
import jetbrains.buildServer.serverSide.healthStatus.ItemCategory;
import jetbrains.buildServer.serverSide.healthStatus.ItemSeverity;
import org.jetbrains.annotations.NotNull;

/**
 * Reports, once globally, when {@code teamcity.git.additionalAllowedUrlTransports} currently permits a
 * known-dangerous transport ({@code ext}/{@code fd}).
 */
public class GitDangerousTransportPermittedHealthReport extends HealthStatusReport {

  public static final String TYPE = "GitDangerousTransportPermittedHealthReport";

  private static final String DATA_TRANSPORTS = "transports";

  private static final Map<String, String> KNOWN_DANGEROUS_TRANSPORT_RISKS = buildKnownDangerousTransportRisks();

  private static Map<String, String> buildKnownDangerousTransportRisks() {
    Map<String, String> risks = new LinkedHashMap<>();
    risks.put("ext", "runs an arbitrary shell command on the server host");
    risks.put("fd", "connects using arbitrary already-open file descriptor numbers in the git process");
    return Collections.unmodifiableMap(risks);
  }

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

    Collection<String> allowed = GitRemoteUrlInspector.getEffectiveAllowedTransports();
    Map<String, String> permitted = new LinkedHashMap<>();
    for (Map.Entry<String, String> risk : KNOWN_DANGEROUS_TRANSPORT_RISKS.entrySet()) {
      if (allowed.contains(risk.getKey())) permitted.put(risk.getKey(), risk.getValue());
    }
    if (permitted.isEmpty()) return;

    consumer.consumeGlobal(new HealthStatusItem(
      TYPE + ".item",
      CATEGORY,
      Collections.singletonMap(DATA_TRANSPORTS, (Object) permitted)
    ));
  }
}
