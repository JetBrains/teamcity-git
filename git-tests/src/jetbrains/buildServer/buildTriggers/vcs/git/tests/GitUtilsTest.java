/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.util.SystemInfo;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.util.FileUtil;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.io.File;

import static org.assertj.core.api.BDDAssertions.then;

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
    assertEquals("refs/remote-run/tw/12345", GitUtils.createRemoteRef("refs/remote-run/tw/12345"));
    assertEquals("refs/tags/v1.0", GitUtils.createRemoteRef("refs/tags/v1.0"));
  }


  @Test
  public void short_file_name_should_not_contain_spaces() throws Exception {
    if (!SystemInfo.isWindows)
      throw new SkipException("Windows only test");

    File tmpDir = createTempDir();
    File dirWithSpaces = new File(tmpDir, "dir with spaces");
    File fileWithSpaces = new File(dirWithSpaces, "file with spaces");
    final String content = "content";
    writeTextToFile(fileWithSpaces, content);

    String shortFileName = GitUtils.getShortFileName(fileWithSpaces);
    then(shortFileName).doesNotContain(" ");
    assertTrue("File references by a short name doesn't exist", new File(shortFileName).exists());
    assertEquals("Short name file content doesn't match", content, FileUtil.readText(new File(shortFileName)));
  }


  @Test
  public void ssh_client_version() {
    then(GitUtils.getSshClientVersion("SSH-1.0", "whatever"))
      .isEqualTo("SSH-1.0");
    then(GitUtils.getSshClientVersion("SSH-2.0", "TeamCity Server 2017.2.1"))
      .isEqualTo("SSH-2.0-TeamCity-Server-2017.2.1");
    then(GitUtils.getSshClientVersion("SSH-2.0-LIB-VERSION", "TeamCity-Server-2017.2.1"))
      .isEqualTo("SSH-2.0-TeamCity-Server-2017.2.1-LIB-VERSION");
    then(GitUtils.getSshClientVersion("SSH-2.0-LIB-VERSION", "TeamCity Server 2017.2.1 EAP"))
      .isEqualTo("SSH-2.0-TeamCity-Server-2017.2.1-EAP-LIB-VERSION");
  }
}
