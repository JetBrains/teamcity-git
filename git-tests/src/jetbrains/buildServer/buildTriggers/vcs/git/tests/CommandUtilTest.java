/*
 * Copyright 2000-2022 JetBrains s.r.o.
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

import org.testng.annotations.Test;

import java.util.List;

import static jetbrains.buildServer.BaseTestCase.*;
import static jetbrains.buildServer.buildTriggers.vcs.git.command.impl.CommandUtil.splitByLines;

public class CommandUtilTest {
  @Test
  public void split_lines_empty() {
    assertEquals(0, splitByLines("").size());
  }

  @Test
  public void split_lines_only_separator() {
    assertEquals(0, splitByLines("\n").size());
  }

  @Test
  public void split_lines_only_separators() {
    assertEquals(0, splitByLines("\n\n\n\n").size());
  }

  @Test
  public void split_lines_one_separator_before() {
    final List<String> res = splitByLines("\n123");
    assertEquals(1, res.size());
    assertEquals("123", res.iterator().next());
  }

  @Test
  public void split_lines_one_separator_after() {
    final List<String> res = splitByLines("123\n");
    assertEquals(1, res.size());
    assertEquals("123", res.iterator().next());
  }

  @Test
  public void split_lines_one() {
    final List<String> res = splitByLines("123");
    assertEquals(1, res.size());
    assertEquals("123", res.iterator().next());
  }

  @Test
  public void split_multiple_lines() {
    final List<String> res = splitByLines("123\n234\n\n345\n456\n");
    assertEquals(4, res.size());
    assertContains(res, "123", "234", "345", "456");
  }
}
