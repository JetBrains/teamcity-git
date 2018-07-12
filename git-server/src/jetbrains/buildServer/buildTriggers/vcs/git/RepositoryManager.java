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

import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;

/**
 * @author dmitry.neverov
 */
public interface RepositoryManager extends MirrorManager {

  @NotNull
  List<File> getExpiredDirs();

  @NotNull
  Repository openRepository(@NotNull URIish fetchUrl) throws VcsException;

  @NotNull
  Repository openRepository(@NotNull File dir, @NotNull URIish fetchUrl) throws VcsException;

  void closeRepository(@NotNull Repository repository);

  @NotNull
  Object getWriteLock(@NotNull File dir);

  @NotNull
  ReadWriteLock getRmLock(@NotNull File dir);

  <T> T runWithDisabledRemove(@NotNull File dir, @NotNull VcsOperation<T> operation) throws VcsException;

  void runWithDisabledRemove(@NotNull File dir, @NotNull VcsAction action) throws VcsException;

  void cleanLocksFor(@NotNull File dir);
}
