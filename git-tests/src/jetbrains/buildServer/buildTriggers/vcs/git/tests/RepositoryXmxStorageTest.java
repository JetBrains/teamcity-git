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
import jetbrains.buildServer.buildTriggers.vcs.git.process.RepositoryXmxStorage;
import jetbrains.buildServer.util.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.BDDAssertions.then;

@Test
public class RepositoryXmxStorageTest extends BaseTestCase {

  @NotNull final private TempFiles myTempFiles = new TempFiles();
  @NotNull File myRepoDir;

  @BeforeMethod
  public void setUp() throws IOException {
    myRepoDir = myTempFiles.createTempDir();
  }

  @Test
  public void test_write() throws Throwable {
    final RepositoryXmxStorage storage = create("fetch");
    storage.write(12345);

    final List<String> lines = new ArrayList<>(FileUtil.readFile(storage.getStorage()));
    lines.removeIf(s -> s.startsWith("#"));
    then(lines).containsExactly("fetch.Xmx=12345M");
  }

  @Test
  public void test_read_write_delete() throws Throwable {
    final RepositoryXmxStorage storage = create("fetch");
    then(storage.read()).isNull();
    storage.write(1024);
    then(storage.read()).isEqualTo(1024);
    storage.write(2056);
    then(storage.read()).isEqualTo(2056);
    storage.write(null);
    then(storage.read()).isNull();
  }

  @Test
  public void test_several_processes() throws Throwable {
    final RepositoryXmxStorage fetch = create("fetch");
    then(fetch.read()).isNull();

    final RepositoryXmxStorage patch = create("patch");
    then(patch.read()).isNull();

    fetch.write(1024);
    then(fetch.read()).isEqualTo(1024);
    then(patch.read()).isNull();

    patch.write(2056);
    then(fetch.read()).isEqualTo(1024);
    then(patch.read()).isEqualTo(2056);

    {
      final List<String> lines = new ArrayList<>(FileUtil.readFile(fetch.getStorage()));
      lines.removeIf(s -> s.startsWith("#"));
      then(lines).containsExactly("fetch.Xmx=1024M", "patch.Xmx=2056M");
    }

    fetch.write(2056);
    then(fetch.read()).isEqualTo(2056);
    then(patch.read()).isEqualTo(2056);

    patch.write(1024);
    then(fetch.read()).isEqualTo(2056);
    then(patch.read()).isEqualTo(1024);

    {
      final List<String> lines = new ArrayList<>(FileUtil.readFile(fetch.getStorage()));
      lines.removeIf(s -> s.startsWith("#"));
      then(lines).containsExactly("fetch.Xmx=2056M", "patch.Xmx=1024M");
    }

    fetch.write(null);
    then(fetch.read()).isNull();
    then(patch.read()).isEqualTo(1024);

    patch.write(null);
    then(fetch.read()).isNull();
    then(patch.read()).isNull();

    then(fetch.getStorage()).doesNotExist();
  }

  @NotNull
  private RepositoryXmxStorage create(@NotNull String key) {
    return new RepositoryXmxStorage(myRepoDir, key);
  }
}
