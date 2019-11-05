/*
 * Copyright 2000-2019 JetBrains s.r.o.
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
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.buildTriggers.vcs.git.process.RepositoryFetchXmxStorage;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.BDDAssertions.then;

@Test
public class RepositoryFetchXmxStorageTest extends BaseTestCase {

  @NotNull final private TempFiles myTempFiles = new TempFiles();
  @NotNull File myRepoDir;

  @BeforeMethod
  public void setUp() throws IOException {
    myRepoDir = myTempFiles.createTempDir();
  }

  @Test
  public void test_write() throws Throwable {
    final RepositoryFetchXmxStorage storage = create();
    storage.write(12345);
    then(FileUtil.readText(storage.getStorage())).isEqualTo("fetch.Xmx=12345M");
  }

  @Test
  public void test_read_write_delete() throws Throwable {
    final RepositoryFetchXmxStorage storage = create();
    then(storage.read()).isNull();
    storage.write(1024);
    then(storage.read()).isEqualTo(1024);
    storage.write(2056);
    then(storage.read()).isEqualTo(2056);
    storage.write(null);
    then(storage.read()).isNull();
  }

  @NotNull
  private RepositoryFetchXmxStorage create() {
    return new RepositoryFetchXmxStorage(myRepoDir);
  }
}
