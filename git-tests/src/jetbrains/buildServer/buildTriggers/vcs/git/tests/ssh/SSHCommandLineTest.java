

package jetbrains.buildServer.buildTriggers.vcs.git.tests.ssh;

import com.jcraft.jsch.Logger;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.JSchClient;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * @author Mikhail Khorkov
 */
@Test
public class SSHCommandLineTest {

  @DataProvider(name = "createData")
  public static Object[][] createData() {
    return new Object[][]{
      /*
      line
      host
      user
      port
      command
      options
      logger
       */
      new Object[]{
        "git@server.com -o StrictHostKeyChecking=no git-upload-pack -o Option1=5 \"user/repo.git\"".split(" "),
        "server.com",
        "git",
        null,
        "git-upload-pack \"user/repo.git\"",
        toMap("StrictHostKeyChecking", "no", "Option1", "5"),
        logger()
      },
      new Object[]{
        "server.com -o StrictHostKeyChecking=no -p 123 git-upload-pack".split(" "),
        "server.com",
        null,
        123,
        "git-upload-pack",
        toMap("StrictHostKeyChecking", "no"),
        logger()
      }
    };
  }

  @Test(dataProvider = "createData")
  public void testParseTest(String[] args, String host, String user, Integer port, String command, Map<String, String> options, Logger logger) {
    final JSchClient.SSHCommandLine line = JSchClient.SSHCommandLine.parse(args, logger);

    Assert.assertEquals(line.getHost(), host);
    Assert.assertEquals(line.getUser(), user);
    Assert.assertEquals(line.getPort(), port);
    Assert.assertEquals(line.getCommand(), command);
    Assert.assertEquals(line.getOptions(), options);
    Assert.assertEquals(line.getLogger(), logger);
  }

  private static Map<String, String> toMap(String... list) {
    if (list == null || list.length == 0) {
      return Collections.emptyMap();
    }
    final Map<String, String> map = new HashMap<>();
    for (int i = 0; i < list.length; i += 2) {
      map.put(list[i], list[i + 1]);
    }
    return map;
  }

  private static Logger logger() {
    return new Logger() {
      @Override
      public boolean isEnabled(final int level) {
        return false;
      }
      @Override
      public void log(final int level, final String message) {
      }
    };
  }
}