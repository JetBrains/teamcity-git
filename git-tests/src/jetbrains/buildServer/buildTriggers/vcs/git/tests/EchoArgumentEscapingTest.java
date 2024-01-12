

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
    assertEquals("^\"", escaper.escape("\""));
    assertEquals("^\"^<", escaper.escape("\"<"));
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