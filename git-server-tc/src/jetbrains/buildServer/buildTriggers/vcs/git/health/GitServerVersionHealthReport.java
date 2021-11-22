package jetbrains.buildServer.buildTriggers.vcs.git.health;

import com.intellij.openapi.util.Ref;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.buildTriggers.vcs.git.GitRepoOperations;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitExec;
import jetbrains.buildServer.serverSide.IOGuard;
import jetbrains.buildServer.serverSide.healthStatus.*;
import org.jetbrains.annotations.NotNull;

public class GitServerVersionHealthReport extends HealthStatusReport {

  public static final String TYPE = "GitServerVersionHealthReport";
  private static final String DESCRIPTION = "Disabled running native commands on TeamCity server-side";
  private static final ItemCategory CATEGORY = new ItemCategory( "GitServerVersionHealthCategory", DESCRIPTION, ItemSeverity.WARN);

  private final GitRepoOperations myGitOperations;

  public GitServerVersionHealthReport(@NotNull GitRepoOperations gitOperations) {
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

    final Ref<GitExec> gitExec = new Ref<>(null);
    String reason = null;
    try {
      IOGuard.allowCommandLine(() -> {gitExec.set(myGitOperations.detectGit());});
      if (myGitOperations.isNativeGitOperationsSupported(gitExec.get())) return;
    } catch (Exception e) {
      reason = e.getMessage();
    }
    final Map<String, Object> data = new HashMap<>();
    data.put("gitExec", gitExec.get());
    data.put("reason", reason);
    consumer.consumeGlobal(new HealthStatusItem("GitServerVersionId", CATEGORY, data));
  }
}
