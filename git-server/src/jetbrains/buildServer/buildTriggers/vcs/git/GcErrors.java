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

package jetbrains.buildServer.buildTriggers.vcs.git;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class GcErrors {

  private final ConcurrentMap<File, String> myErrors = new ConcurrentHashMap<>();

  void registerError(@NotNull File cloneDir, @NotNull String error) {
    myErrors.put(cloneDir, error);
  }

  void registerError(@NotNull File cloneDir, @NotNull Exception error) {
    myErrors.put(cloneDir, error.toString());
  }

  void registerError(@NotNull File cloneDir, @NotNull String description, @NotNull Exception error) {
    myErrors.put(cloneDir, description + " " + error.toString());
  }

  void clearError(@NotNull File cloneDir) {
    myErrors.remove(cloneDir);
  }

  public void retainErrors(@NotNull Collection<File> files) {
    myErrors.keySet().retainAll(new HashSet<>(files));
  }

  @NotNull
  public Map<File, String> getErrors() {
    return new HashMap<>(myErrors);
  }
}
