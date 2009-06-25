/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.submodules.Submodule;
import jetbrains.buildServer.buildTriggers.vcs.git.submodules.SubmodulesConfig;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.dataFile;
import org.spearce.jgit.lib.BlobBasedConfig;
import org.spearce.jgit.lib.Repository;
import static org.testng.Assert.assertEquals;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;

/**
 * The test for submodule utilities
 */
public class SubmoduleTest {
  /**
   * Test loading mapping for submodules
   *
   * @throws IOException if there is IO problem
   */
  @Test
  public void testSubmoduleMapping() throws IOException {
    File masterRep = dataFile("repo.git");
    Repository r = new Repository(masterRep);
    try {
      SubmodulesConfig s = new SubmodulesConfig(r.getConfig(), new BlobBasedConfig(null, r.mapCommit(
        GitUtils.versionRevision(GitVcsSupportTest.SUBMODULE_ADDED_VERSION)), ".gitmodules"));
      Submodule m = s.findEntry("submodule");
      assertEquals(m.getName(), "submodule");
      assertEquals(m.getPath(), "submodule");
      assertEquals(m.getUrl(), "../submodule.git");
    } finally {
      r.close();
    }
  }
}
