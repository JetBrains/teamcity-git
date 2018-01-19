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

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Handles the case when git-plugin from newer version is installed into
 * a server which doesn't have jetbrains.buildServer.vcs.ChangedFilesSupport extension.
 * In this case our implementation is not registered.
 *
 * Can be dropped once we release a major version with ChangedFilesSupport extension.
 */
public class GitChangedFilesSupportCreator {
  private static final Logger LOG = Logger.getInstance(GitChangedFilesSupportCreator.class.getName());

  public GitChangedFilesSupportCreator(@NotNull GitVcsSupport vcs,
                                       @NotNull RepositoryManager repositoryManager,
                                       @NotNull CommitLoader commitLoader) {
    try {
      Class<?> supportClass = Class.forName("jetbrains.buildServer.vcs.ChangedFilesSupport");
      Class<?> reporterClass = Class.forName("jetbrains.buildServer.vcs.ChangedFilesSupport$ChangedFilesReporter");
      Class<?> consumer = Class.forName("jetbrains.buildServer.vcs.ChangedFilesSupport$ChangedFilesConsumer");
      Method computeChangedFiles = supportClass.getDeclaredMethod("computeChangedFiles", VcsRoot.class, String.class, String.class, consumer);
      Method createReporter = supportClass.getDeclaredMethod("createChangedFilesReporter", VcsRoot.class);
      Method reporterComputeChangedFiles = reporterClass.getDeclaredMethod("computeChangedFiles", String.class, String.class, consumer);
      if (computeChangedFiles != null && createReporter != null && reporterComputeChangedFiles != null) {
        Class<?> implClass = Class.forName("jetbrains.buildServer.buildTriggers.vcs.git.GitChangedFilesSupport");
        Constructor<?> constructor = implClass.getConstructor(GitVcsSupport.class, RepositoryManager.class, CommitLoader.class);
        Object impl = constructor.newInstance(vcs, repositoryManager, commitLoader);
        if (impl instanceof GitServerExtension) {
          vcs.addExtension((GitServerExtension)impl);
        }
      }
    } catch (ClassNotFoundException e) {
      //old TeamCity version
      LOG.debug("Error while registering GitChangedFilesSupport, continue without it", e);
    } catch (Exception e) {
      LOG.warnAndDebugDetails("Error while registering GitChangedFilesSupport, continue without it", e);
    }
  }
}
