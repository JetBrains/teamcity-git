

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


  // This test fails on Windows if 8.3 (short) names are disabled. All ways to check if they are enabled either require administrative privileges
  // or are equivalent to the code that is tested. We probably need to investigate it further, but at least on Windows 10 (Version 10.0.19043.1706)
  // with Git version 2.23.0.windows.1 and short names disabled TeamCity agent works fine with SSH URLs even if temporary directory path contains spaces
  @Test(enabled = false)
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