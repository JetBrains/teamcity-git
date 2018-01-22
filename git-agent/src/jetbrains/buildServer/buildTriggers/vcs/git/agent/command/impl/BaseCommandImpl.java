/*
 * Copyright 2000-2018 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.buildTriggers.vcs.git.agent.command.impl;

import com.intellij.openapi.util.Pair;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitCommandLine;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.command.BaseCommand;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class BaseCommandImpl implements BaseCommand {

  private final GitCommandLine myCmd;
  private final List<Pair<String, String>> myConfigs = new ArrayList<Pair<String, String>>();
  private final Map<String, String> myEnv = new HashMap<String, String>();
  private final List<Runnable> myPostActions = new ArrayList<Runnable>();

  BaseCommandImpl(@NotNull GitCommandLine cmd) {
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
    return myCmd;
  }


  public void addConfig(@NotNull String name, @NotNull String value) {
    myConfigs.add(Pair.create(name, value));
  }


  public void setEnv(@NotNull String name, @NotNull String value) {
    myEnv.put(name, value);
  }


  @Override
  public void addPostAction(@NotNull Runnable action) {
    myPostActions.add(action);
  }
}
