package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import jetbrains.buildServer.buildTriggers.vcs.git.GitRemoteUrlInspector;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static jetbrains.buildServer.buildTriggers.vcs.git.GitRemoteUrlInspector.LocalReason.*;
import static org.assertj.core.api.Assertions.assertThat;

public class GitRemoteUrlInspectorTest {

  @DataProvider(name = "unsafeUrls")
  public Object[][] unsafeUrls() {
    return new Object[][]{
      // file: scheme
      {"file:/repo", FILE_SCHEME},
      {"file:///var/repos/repo.git", FILE_SCHEME},
      {"FILE://C:/repo", FILE_SCHEME},

      // Windows UNC
      {"\\\\server\\share\\repo.git", WINDOWS_UNC},

      // Windows drive
      {"C:/projects/repo", WINDOWS_DRIVE},
      {"D:relative/path", WINDOWS_DRIVE},
      {"z:foo", WINDOWS_DRIVE},

      // Unix absolute
      {"/var/lib/git/repo.git", UNIX_ABSOLUTE},

      // Unix/Windows relative
      {"./repo", UNIX_RELATIVE},
      {"../repo",  UNIX_RELATIVE},
      {"~/repo", UNIX_RELATIVE},
      {".\\repo", UNIX_RELATIVE},
      {"..\\repo", UNIX_RELATIVE},
      {"~\\repo", UNIX_RELATIVE},

      // Bare path with separator (treated as local relative)
      {"path/to/repo", UNIX_RELATIVE},
      {"path\\to\\repo", UNIX_RELATIVE}
    };
  }

  @Test(dataProvider = "unsafeUrls")
  public void should_detect_unsafe_local_urls(@NotNull String url, @NotNull GitRemoteUrlInspector.LocalReason expectedReason) {
    assertThat(GitRemoteUrlInspector.isLocalFileAccess(url))
      .as("Expected local access for: " + url)
      .isTrue();
    assertThat(GitRemoteUrlInspector.classify(url))
      .as("Expected reason for: " + url)
      .isEqualTo(expectedReason);
  }

  @DataProvider(name = "safeUrls")
  public Object[][] safeUrls() {
    return new Object[][]{
      // Network schemes
      {"ssh://git@example.com/owner/repo.git"},
      {"https://example.com/owner/repo.git"},
      {"git://example.com/repo"},

      // scp-like syntax
      {"git@example.com:owner/repo.git"},
      {"user@host:~/repo.git"},
      {"host:org/repo.git"},

      // No separators (conservatively not marked as local)
      {"repo.git"},
      {"origin"}
    };
  }

  @Test(dataProvider = "safeUrls")
  public void should_not_flag_safe_remote_urls_as_local(String url) {
    assertThat(GitRemoteUrlInspector.isLocalFileAccess(url))
      .as("Did not expect local access for: " + url)
      .isFalse();
    assertThat(GitRemoteUrlInspector.classify(url))
      .as("Expected null reason for: " + url)
      .isNull();
  }
}
