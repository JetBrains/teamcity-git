/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;

/**
 * @author dmitry.neverov
 */
public class WinAskPassGenerator extends AskPassGenerator {

  @NotNull
  @Override
  String getScriptName() {
    return "askpass.bat";
  }

  @NotNull
  @Override
  String getScriptContent(@NotNull final String passwordPath) {
    return "@echo off\n" +
           "\n" +
           "SET passfile=" + passwordPath + "\n" +
           "FOR /F \"delims=\" %%i IN (%passfile%) DO <nul (SET /P aux=%%i)\n" +
           "del %passfile%\n";
  }
}
