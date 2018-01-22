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

package jetbrains.buildServer.buildTriggers.vcs.git.agent;

import org.jetbrains.annotations.NotNull;

public interface GitProgressLogger {

  void openBlock(@NotNull String name);

  void message(@NotNull String message);

  void progressMessage(@NotNull String message);

  void closeBlock(@NotNull String name);

  GitProgressLogger NO_OP = new GitProgressLogger() {
    public void openBlock(@NotNull final String name) {
    }
    public void message(@NotNull final String message) {
    }
    public void progressMessage(@NotNull final String message) {
    }
    public void closeBlock(@NotNull final String name) {
    }
  };
}
