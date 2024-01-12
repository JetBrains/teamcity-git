

package jetbrains.buildServer.buildTriggers.vcs.git.command.impl;

import com.intellij.openapi.util.Pair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jetbrains.buildServer.buildTriggers.vcs.git.command.BaseCommand;
import jetbrains.buildServer.buildTriggers.vcs.git.command.GitCommandLine;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.jetbrains.annotations.NotNull;

public class BaseCommandImpl implements BaseCommand {

  private final GitCommandLine myCmd;
  private final List<Pair<String, String>> myConfigs = new ArrayList<>();
  private final Map<String, String> myEnv = new HashMap<>();
  private final List<Runnable> myPostActions = new ArrayList<>();
  private boolean myThrowExceptionOnNonZeroExitCode = true;

  public BaseCommandImpl(@NotNull GitCommandLine cmd) {
    myCmd = cmd;
  }

  @NotNull
  protected GitCommandLine getCmd() {
    for (Map.Entry<String, String> e : myEnv.entrySet()) {
      myCmd.addEnvParam(e.getKey(), e.getValue());
    }
    for (Pair<String, String> config : myConfigs) {
      myCmd.addParameters("-c", config.first + "=" + config.second);
    }
    for (Runnable action : myPostActions) {
      myCmd.addPostAction(action);
    }
    if (TeamCityProperties.getBooleanOrTrue("teamcity.git.native.forceEnglishLocale")) {
      myCmd.addEnvParam("LANGUAGE", "en_US");
    }
    return myCmd.abnormalExitExpected(!myThrowExceptionOnNonZeroExitCode);
  }


  public void addConfig(@NotNull String name, @NotNull String value) {
    myConfigs.add(Pair.create(name, value));
  }


  public void setEnv(@NotNull String name, @NotNull String value) {
    myEnv.put(name, value);
  }

  @Override
  public void throwExceptionOnNonZeroExitCode(final boolean throwExceptionOnNonZeroExitCode) {
    myThrowExceptionOnNonZeroExitCode = throwExceptionOnNonZeroExitCode;
  }

  @Override
  public void addPostAction(@NotNull Runnable action) {
    myPostActions.add(action);
  }
}