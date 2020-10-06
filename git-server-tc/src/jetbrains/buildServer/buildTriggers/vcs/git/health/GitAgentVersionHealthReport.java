package jetbrains.buildServer.buildTriggers.vcs.git.health;

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVersion;
import jetbrains.buildServer.serverSide.BuildAgentEx;
import jetbrains.buildServer.serverSide.BuildAgentManagerEx;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.serverSide.WebLinks;
import jetbrains.buildServer.serverSide.agentPools.AgentPool;
import jetbrains.buildServer.serverSide.agentPools.AgentPoolManager;
import jetbrains.buildServer.serverSide.agentPools.AgentPoolUtil;
import jetbrains.buildServer.serverSide.agentTypes.SAgentType;
import jetbrains.buildServer.serverSide.healthStatus.*;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;

public class GitAgentVersionHealthReport extends HealthStatusReport {
  private static final Logger LOG = Logger.getInstance(GitAgentVersionHealthReport.class.getName());

  static final String TYPE = "GitAgentVersionHealthReport";
  static final String UNSUPPORTED_CATEGORY = TYPE + ".unsupported";
  static final String DEPRECATED_CATEGORY = TYPE + ".deprecated";

  private static final String VERSION_THRESHOLD_INTERNAL_PROPERTY = "teamcity.git.deprecated.version";

  @NotNull private final BuildAgentManagerEx myAgentManager;
  @NotNull private final AgentPoolManager myAgentPoolManager;

  @NotNull private final ItemCategory myUnsupportedCategory;
  @NotNull private final ItemCategory myDeprecatedCategory;
  @NotNull private final GitVersion myDeprecatedVersion;

  public GitAgentVersionHealthReport(@NotNull final BuildAgentManagerEx agentManager,
                                     @NotNull final AgentPoolManager agentPoolManager,
                                     @NotNull final WebLinks webLinks) {
    myAgentManager = agentManager;
    myAgentPoolManager = agentPoolManager;
    myDeprecatedVersion = getDeprecatedVersion();

    final String helpUrl = webLinks.getHelp("Git", "agentGitPath");
    myUnsupportedCategory = new ItemCategory(UNSUPPORTED_CATEGORY,  "Unsupported git executable version on agent", ItemSeverity.WARN,
                                             "Some agents are running unsupported git versions prior to " + GitVersion.MIN + ", agent-side checkout can't be performed",
                                             helpUrl);
    myDeprecatedCategory = new ItemCategory(DEPRECATED_CATEGORY, "Deprecated git executable version on agent", ItemSeverity.WARN,
                                            "Some agents are running git versions prior to " + myDeprecatedVersion + ", which will be no longer supported starting from the next major release",
                                            helpUrl);

  }

  @NotNull
  @Override
  public String getType() {
    return TYPE;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Report agents running older git versions, which are either unsupported or will no longer be supported from the next major release";
  }

  @NotNull
  private static GitVersion getDeprecatedVersion() {
    final String property = TeamCityProperties.getProperty(VERSION_THRESHOLD_INTERNAL_PROPERTY);
    if (StringUtil.isNotEmpty(property)) {
      try {
        return GitVersion.parse(property);
      } catch (Exception e) {
        LOG.warn("Failed to parse git version provided by \"" + VERSION_THRESHOLD_INTERNAL_PROPERTY + "\" internal property: " + property);
      }
    }
    return GitVersion.DEPRECATED;
  }

  @NotNull
  @Override
  public Collection<ItemCategory> getCategories() {
    return Arrays.asList(myUnsupportedCategory, myDeprecatedCategory);
  }

  @Override
  public boolean canReportItemsFor(@NotNull final HealthStatusScope scope) {
    return scope.globalItems() && scope.isItemWithSeverityAccepted(ItemSeverity.WARN);
  }

  @Override
  public void report(@NotNull final HealthStatusScope scope, @NotNull final HealthStatusItemConsumer consumer) {
    final List<BuildAgentEx> unsupported = new ArrayList<>();
    final List<BuildAgentEx> deprecated = new ArrayList<>();

    for (BuildAgentEx a : myAgentManager.getRegisteredAgents()) {
      final GitVersion version = getGitVersion(a);
      if (version == null) {
        LOG.debug("No git executable version reported for \"" + a.getName() + "\" agent via \"" + Constants.TEAMCITY_AGENT_GIT_VERSION + "\" environment property");
        continue;
      }
      if (version.isLessThan(GitVersion.MIN)) {
        unsupported.add(a);
      } else if (version.isLessThan(myDeprecatedVersion)) {
        deprecated.add(a);
      }
    }
    if (!unsupported.isEmpty()) {
      final Map<String, Object> model = new HashMap<>();
      model.put("gitVersionUnsupported", true);
      model.put("gitVersionAgentCount", unsupported.size());
      model.put("gitVersionAgents", groupAgents(unsupported));
      model.put("hasSeveralPools", myAgentPoolManager.hasSeveralPools());
      consumer.consumeGlobal(new HealthStatusItem(UNSUPPORTED_CATEGORY, myUnsupportedCategory, model));
    }
    if (!deprecated.isEmpty()) {
      final Map<String, Object> model = new HashMap<>();
      model.put("gitVersionUnsupported", false);
      model.put("gitVersionAgentCount", deprecated.size());
      model.put("gitVersionAgents", groupAgents(deprecated));
      model.put("hasSeveralPools", myAgentPoolManager.hasSeveralPools());
      consumer.consumeGlobal(new HealthStatusItem(DEPRECATED_CATEGORY, myDeprecatedCategory, model));
    }
  }

  // grouping code borrowed from jetbrains.buildserver.jvmUpdate.server.AgentsJVMGeneralExtension
  @NotNull
  private static Map<AgentPool, Set<SAgentType>> groupAgents(@NotNull Collection<BuildAgentEx> agents) {
    final Comparator<SAgentType> byNameComparator = Comparator.comparing(agentType -> agentType.getDetails().getName());
    final Collector<SAgentType, ?, Set<SAgentType>> distinctAgentTypesCollector =
      Collectors.toCollection(() -> new TreeSet<>(byNameComparator.thenComparingInt(SAgentType::getAgentTypeId)));
    final Supplier<TreeMap<AgentPool, Set<SAgentType>>> sortedPoolsMap = () -> new TreeMap<>(AgentPoolUtil.POOL_COMPARATOR);

    return agents.stream()
                 .map(BuildAgentEx::getAgentType)
                 .collect(groupingBy(SAgentType::getAgentPool, sortedPoolsMap, distinctAgentTypesCollector));
  }

  @Nullable
  private static GitVersion getGitVersion(@NotNull BuildAgentEx agent) {
    final String versionStr = agent.getBuildParameters().get("env." + Constants.TEAMCITY_AGENT_GIT_VERSION);
    if (StringUtil.isNotEmpty(versionStr)) {
      try {
        return GitVersion.fromString(versionStr);
      } catch (Exception e) {
        // not expected
        LOG.warn("Failed to parse git executable version reported for \"" + agent.getName() + "\" agent via \"" + Constants.TEAMCITY_AGENT_GIT_VERSION + "\" environment property: " + versionStr);
      }
    }
    return null;
  }
}
