package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.jetbrains.annotations.NotNull;

public interface GitHubPasswordAuthRootRegistryFactory {
  static final String REPORT_TYPE = "gitHubPasswordAuthHealthReport";

  static boolean isEnabled() {
    return TeamCityProperties.getBoolean("teamcity.git." + REPORT_TYPE + ".enabled");
  }

  @NotNull GitHubPasswordAuthRootRegistry createRegistry();
}
