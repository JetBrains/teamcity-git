

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import jetbrains.buildServer.agent.plugins.beans.PluginDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class MockGitPluginDescriptor implements PluginDescriptor {
  @NotNull
  public File getPluginRoot() {
    return new File("jetbrains.git");
  }
}