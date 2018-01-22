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

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.buildTriggers.vcs.git.GitServerUtil;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.TestFor;
import jetbrains.buildServer.vcs.VcsException;
import junit.framework.TestCase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.io.File;

/**
 * @author dmitry.neverov
 */
@SuppressWarnings("ConstantConditions")
@Test
public class GitServerUtilTest extends TestCase {

  protected TempFiles myTempFiles = new TempFiles();

  @AfterMethod
  public void tearDown() {
    myTempFiles.cleanup();
  }

  //TW-17171, TW-17333
  public void should_recover_from_invalid_config_file() throws Exception {
    File dir = myTempFiles.createTempDir();
    Repository db = GitServerUtil.getRepository(dir, new URIish("git://some.org/repository"));
    FileUtil.delete(new File(dir, "config"));//emulate invalid config
    db = GitServerUtil.getRepository(dir, new URIish("git://some.org/repository"));
  }


  public void convertMemorySizeToBytes() {
    assertNull(GitServerUtil.convertMemorySizeToBytes(null));
    assertNull(GitServerUtil.convertMemorySizeToBytes(""));
    assertNull(GitServerUtil.convertMemorySizeToBytes(" "));
    assertNull(GitServerUtil.convertMemorySizeToBytes("512x"));
    assertNull(GitServerUtil.convertMemorySizeToBytes("x512m"));

    assertEquals(GitServerUtil.KB, (long)GitServerUtil.convertMemorySizeToBytes("1k"));
    assertEquals(GitServerUtil.KB, (long)GitServerUtil.convertMemorySizeToBytes("1K"));
    assertEquals(2 * GitServerUtil.KB, (long)GitServerUtil.convertMemorySizeToBytes("2K"));

    assertEquals(GitServerUtil.MB, (long)GitServerUtil.convertMemorySizeToBytes("1m"));
    assertEquals(GitServerUtil.MB, (long)GitServerUtil.convertMemorySizeToBytes("1M"));
    assertEquals(2 * GitServerUtil.MB, (long)GitServerUtil.convertMemorySizeToBytes("2M"));

    assertEquals(GitServerUtil.GB, (long)GitServerUtil.convertMemorySizeToBytes("1g"));
    assertEquals(GitServerUtil.GB, (long)GitServerUtil.convertMemorySizeToBytes("1G"));
    assertEquals(2 * GitServerUtil.GB, (long)GitServerUtil.convertMemorySizeToBytes("2G"));
  }


  @TestFor(issues = "TW-50043")
  @Test(dataProviderClass = GitVcsRootTest.class, dataProvider = "urlsWithNewLines")
  public void url_with_newline(@NotNull String url) throws Exception {
    File dir = myTempFiles.createTempDir();
    try {
      GitServerUtil.getRepository(dir, new URIish(url));
      fail("No error for url '" + url + "'");
    } catch (VcsException e) {
      //expected
    }
  }
}
