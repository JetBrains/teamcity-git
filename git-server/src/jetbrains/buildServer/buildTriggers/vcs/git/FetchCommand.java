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

import java.io.IOException;
import java.util.Collection;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;

/**
 * @author dmitry.neverov
 */
public interface FetchCommand extends GitCommand {

  /**
   * Makes a fetch into local repository (db.getDirectory() should be not null)
   */
  void fetch(@NotNull Repository db,
                    @NotNull URIish fetchURI,
                    @NotNull Collection<RefSpec> refspecs,
                    @NotNull FetchSettings settings) throws IOException, VcsException;

}
