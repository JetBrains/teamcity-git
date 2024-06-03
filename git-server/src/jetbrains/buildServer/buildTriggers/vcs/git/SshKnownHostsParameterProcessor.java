package jetbrains.buildServer.buildTriggers.vcs.git;

import java.util.Collection;
import jetbrains.buildServer.serverSide.BuildStartContext;
import jetbrains.buildServer.serverSide.BuildStartContextProcessor;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.jetbrains.annotations.NotNull;

public class SshKnownHostsParameterProcessor implements BuildStartContextProcessor {

  @Override
  public void updateParameters(@NotNull BuildStartContext context) {
    Collection<String> props = TeamCityProperties.getPropertiesWithPrefix(Constants.SSH_KNOWN_HOSTS_PARAM_NAME).values();
    if (!props.isEmpty()) {
      String knownHosts = String.join("\n", props);
      context.addSharedParameter(Constants.SSH_KNOWN_HOSTS_PARAM_NAME, knownHosts);
    }
  }
}
