package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import java.util.Arrays;
import java.util.HashSet;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.GitRemoteUrlInspector;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static jetbrains.buildServer.buildTriggers.vcs.git.GitRemoteUrlInspector.LocalReason.*;

public class GitRemoteUrlInspectorTest extends BaseTestCase {

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
    Assertions.assertThat(GitRemoteUrlInspector.isLocalFileAccess(url))
      .as("Expected local access for: " + url)
      .isTrue();
    Assertions.assertThat(GitRemoteUrlInspector.classify(url))
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
    Assertions.assertThat(GitRemoteUrlInspector.isLocalFileAccess(url))
      .as("Did not expect local access for: " + url)
      .isFalse();
    Assertions.assertThat(GitRemoteUrlInspector.classify(url))
      .as("Expected null reason for: " + url)
      .isNull();
  }

  @DataProvider(name = "disallowedTransportUrls")
  public Object[][] disallowedTransportUrls() {
    return new Object[][]{
      // ext:: / fd:: remote-helper syntax
      {"ext::sh /tmp/x.sh"},
      {"fd::something"},
      {"EXT::sh /tmp/x.sh"},

      // ext:// / fd:// scheme syntax
      {"ext://touch /tmp/x"},
      {"fd://something"},

      // any other remote helper / scheme not in the default allowed set is rejected too
      {"hg::https://example.com/repo"},
      {"s3://bucket/repo"},
      {"foo://anything"}
    };
  }

  @Test(dataProvider = "disallowedTransportUrls")
  public void should_reject_disallowed_transports(String url) {
    Assertions.assertThat(GitRemoteUrlInspector.verifyUrl(url))
      .as("Expected DISALLOWED_TRANSPORT for: " + url)
      .isEqualTo(GitRemoteUrlInspector.UrlRestriction.DISALLOWED_TRANSPORT);
  }

  @DataProvider(name = "allowedUrlsForVerifyUrl")
  public Object[][] allowedUrlsForVerifyUrl() {
    return new Object[][]{
      // allowed network schemes
      {"http://example.com/owner/repo.git"},
      {"https://example.com/owner/repo.git"},
      {"ssh://git@example.com/owner/repo.git"},
      {"git://example.com/repo"},

      // scp-like syntax
      {"user@host:path"},
      {"host:path"},

      // ambiguous bare word with no colon/slash
      {"myhost"}
    };
  }

  @Test(dataProvider = "allowedUrlsForVerifyUrl")
  public void should_not_flag_allowed_urls(String url) {
    Assertions.assertThat(GitRemoteUrlInspector.verifyUrl(url))
      .as("Expected null restriction for: " + url)
      .isNull();
  }

  @Test
  public void should_have_default_allowed_transports() {
    Assertions.assertThat(GitRemoteUrlInspector.getEffectiveAllowedTransports())
      .isEqualTo(new HashSet<>(Arrays.asList("http", "https", "ssh", "git")));
  }

  @Test
  public void should_allow_transport_added_via_override_property() {
    setInternalProperty(Constants.ADDITIONAL_ALLOWED_URL_TRANSPORTS, "hg");

    Assertions.assertThat(GitRemoteUrlInspector.verifyUrl("hg::https://example.com/repo"))
      .as("Expected 'hg' to be allowed once added to the override property")
      .isNull();
    Assertions.assertThat(GitRemoteUrlInspector.verifyUrl("ext::sh /tmp/x.sh"))
      .as("Expected 'ext' to remain rejected even when the override only lists 'hg'")
      .isEqualTo(GitRemoteUrlInspector.UrlRestriction.DISALLOWED_TRANSPORT);
  }

  @Test
  public void should_keep_default_transports_allowed_when_override_property_is_set() {
    setInternalProperty(Constants.ADDITIONAL_ALLOWED_URL_TRANSPORTS, "hg");

    Assertions.assertThat(GitRemoteUrlInspector.verifyUrl("http://example.com/owner/repo.git")).isNull();
    Assertions.assertThat(GitRemoteUrlInspector.verifyUrl("ssh://git@example.com/owner/repo.git")).isNull();
  }

  @Test
  public void should_allow_ext_when_explicitly_added_to_override_property() {
    setInternalProperty(Constants.ADDITIONAL_ALLOWED_URL_TRANSPORTS, "ext");

    Assertions.assertThat(GitRemoteUrlInspector.verifyUrl("ext::sh /tmp/x.sh"))
      .as("The override is trusted uniformly, including for known-dangerous transports")
      .isNull();
  }
}
