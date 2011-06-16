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

import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.buildTriggers.vcs.git.GitServerUtil;
import jetbrains.buildServer.util.FileUtil;
import junit.framework.TestCase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.URIish;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.io.File;

/**
 * @author dmitry.neverov
 */
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

}
