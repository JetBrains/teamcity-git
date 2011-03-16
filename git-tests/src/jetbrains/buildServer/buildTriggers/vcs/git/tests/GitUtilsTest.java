/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import org.testng.annotations.Test;

/**
 * @author dmitry.neverov
 */
public class GitUtilsTest extends BaseTestCase {

  @Test
  public void test_branchRef() {
    assertEquals("refs/heads/master", GitUtils.expandRef("master"));
    assertEquals("refs/heads/master", GitUtils.expandRef("refs/heads/master"));
    assertEquals("refs/remote-run/tw/12345", GitUtils.expandRef("refs/remote-run/tw/12345"));
  }


  @Test
  public void test_remotesBranchRef() {
    assertEquals("refs/remotes/origin/master", GitUtils.createRemoteRef("master"));
    assertEquals("refs/remotes/origin/master", GitUtils.createRemoteRef("refs/heads/master"));
    assertEquals("refs/remotes/origin/remote-run/tw/12345", GitUtils.createRemoteRef("refs/remote-run/tw/12345"));
    assertEquals("refs/tags/v1.0", GitUtils.createRemoteRef("refs/tags/v1.0"));
  }

}
