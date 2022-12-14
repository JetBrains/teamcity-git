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

import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.EscapeEchoArgumentUnix;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.EscapeEchoArgumentWin;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertEquals;

@Test
public class EchoArgumentEscapingTest {

  public void windows_escaping() {
    EscapeEchoArgumentWin escaper = new EscapeEchoArgumentWin();
    assertEquals("a^^b", escaper.escape("a^b"));
    assertEquals("a^&b", escaper.escape("a&b"));
    assertEquals("a^<b", escaper.escape("a<b"));
    assertEquals("a^>b", escaper.escape("a>b"));
    assertEquals("a^|b", escaper.escape("a|b"));
    assertEquals("a%%b", escaper.escape("a%b"));
    assertEquals("", escaper.escape(null));
  }

  public void unix_escaping() {
    EscapeEchoArgumentUnix escaper = new EscapeEchoArgumentUnix();
    assertEquals("'ab'", escaper.escape("ab"));

    // We no longer need to escape backslashes as on *nix OSes
    // we pass this value to printf as a second argument
    assertEquals("'a\\\"b'", escaper.escape("a\\\"b"));

    assertEquals("''", escaper.escape(null));
    assertEquals("'a'\\''b'", escaper.escape("a'b"));//TW-51968
  }

}
