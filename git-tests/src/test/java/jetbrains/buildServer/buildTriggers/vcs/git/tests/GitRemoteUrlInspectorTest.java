package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import java.util.Arrays;
import java.util.HashSet;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.GitRemoteUrlInspector;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static jetbrains.buildServer.buildTriggers.vcs.git.GitRemoteUrlInspector.LocalReason.*;
import static jetbrains.buildServer.buildTriggers.vcs.git.GitRemoteUrlInspector.UrlRestriction.DISALLOWED_TRANSPORT;
import static jetbrains.buildServer.buildTriggers.vcs.git.GitRemoteUrlInspector.UrlRestriction.MALFORMED_URL;

public class GitRemoteUrlInspectorTest extends BaseTestCase {

  @DataProvider(name = "localAccessCases")
  public Object[][] localAccessCases() {
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
      {"path\\to\\repo", UNIX_RELATIVE},

      // Network schemes - not local
      {"ssh://git@example.com/owner/repo.git", null},
      {"https://example.com/owner/repo.git", null},
      {"git://example.com/repo", null},

      // scp-like syntax - not local
      {"git@example.com:owner/repo.git", null},
      {"user@host:~/repo.git", null},
      {"host:org/repo.git", null},

      // No separators (conservatively not marked as local)
      {"repo.git", null},
      {"origin", null},

      // one-letter remote helper, not a Windows drive letter
      {"s::evil", null},
      {"x::sh -c id", null},
      {"C::evil", null},

      // one-letter scheme, not a Windows drive letter
      {"x://repo", null},
      {"C://repo", null}
    };
  }

  @Test(dataProvider = "localAccessCases")
  public void should_classify_local_access(@NotNull String url, @Nullable GitRemoteUrlInspector.LocalReason expectedReason) {
    Assertions.assertThat(GitRemoteUrlInspector.isLocalFileAccess(url))
      .as("Expected local access = " + (expectedReason != null) + " for: " + url)
      .isEqualTo(expectedReason != null);
    Assertions.assertThat(GitRemoteUrlInspector.classify(url))
      .as("Expected reason for: " + url)
      .isEqualTo(expectedReason);
  }

  @DataProvider(name = "urlRestrictionCases")
  public Object[][] urlRestrictionCases() {
    return new Object[][]{
      // ext:: / fd:: remote-helper syntax
      {"ext::sh /tmp/x.sh", DISALLOWED_TRANSPORT},
      {"fd::something", DISALLOWED_TRANSPORT},
      {"EXT::sh /tmp/x.sh", DISALLOWED_TRANSPORT},

      // ext:// / fd:// scheme syntax
      {"ext://touch /tmp/x", DISALLOWED_TRANSPORT},
      {"fd://something", DISALLOWED_TRANSPORT},

      // any other remote helper / scheme not in the default allowed set is rejected too
      {"hg::https://example.com/repo", DISALLOWED_TRANSPORT},
      {"s3://bucket/repo", DISALLOWED_TRANSPORT},
      {"foo://anything", DISALLOWED_TRANSPORT},

      // one-letter remote helper, not a Windows drive letter
      {"s::evil", DISALLOWED_TRANSPORT},
      {"x::sh -c id", DISALLOWED_TRANSPORT},
      {"C::evil", DISALLOWED_TRANSPORT},

      // one-letter scheme, not a Windows drive letter
      {"x://repo", DISALLOWED_TRANSPORT},
      {"C://repo", DISALLOWED_TRANSPORT},

      // transport matching is case-sensitive
      {"SSH://host/repo", DISALLOWED_TRANSPORT},
      {"HTTP://example.com/repo", DISALLOWED_TRANSPORT},
      {"Git://example.com/repo", DISALLOWED_TRANSPORT},

      // allowed network schemes
      {"http://example.com/owner/repo.git", null},
      {"https://example.com/owner/repo.git", null},
      {"ssh://git@example.com/owner/repo.git", null},
      {"git://example.com/repo", null},

      // git+ssh / ssh+git are native-ssh aliases
      {"git+ssh://host/repo", null},
      {"ssh+git://host/repo", null},

      // scp-like syntax
      {"user@host:path", null},
      {"host:path", null},
      {"git@10.128.93.163:/srv/git/privaterepo.git", null},
      {"foo+bar@host:repo", null},

      // scp-like syntax with a bracketed (e.g. IPv6) host
      {"[::1]:repo", null},
      {"user@[2001:db8::1]:repo", null},
      {"ssh://[::1]/repo", null},

      // a '/' inside brackets is not a valid IPv6-style host - real git treats this as a local path
      // (confirmed via GIT_TRACE=1: it runs git-upload-pack locally, no ssh), so it must not be
      // recognized as scp-like ssh either
      {"[foo/bar]:repo", MALFORMED_URL},

      // ambiguous bare word with no colon/slash
      {"myhost", null},

      // CLI-flag-shaped values must never get a free pass as "presumably scp-like" or "bare word"
      {"-oProxyCommand=id", MALFORMED_URL},
      {"--upload-pack=id", MALFORMED_URL},
      {"-oProxyCommand=x:evil", MALFORMED_URL},
      {"-oProxyCommand=x@host:path", MALFORMED_URL}
    };
  }

  @Test(dataProvider = "urlRestrictionCases")
  public void should_classify_url_restriction(String url, GitRemoteUrlInspector.UrlRestriction expectedRestriction) {
    Assertions.assertThat(GitRemoteUrlInspector.verifyUrl(url))
      .as("Expected " + expectedRestriction + " for: " + url)
      .isEqualTo(expectedRestriction);
  }

  @DataProvider(name = "scpLikeUrls")
  public Object[][] scpLikeUrls() {
    return new Object[][]{
      {"user@host:path"},
      {"host:path"},
      {"git@10.128.93.163:/srv/git/privaterepo.git"},
      {"user@host:~/repo.git"},
      {"foo+bar@host:repo"},
      {"[::1]:repo"},
      {"user@[2001:db8::1]:repo"},
      {"git+ssh://host/repo"},
      {"ssh+git://host/repo"}
    };
  }

  @Test(dataProvider = "scpLikeUrls")
  public void should_identify_scp_like_syntax_as_ssh_transport(String url) {
    Assertions.assertThat(GitRemoteUrlInspector.getTransportName(url))
      .as("scp-like syntax is an implicit ssh transport: " + url)
      .isEqualTo("ssh");
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

  @Test
  public void should_match_override_property_case_sensitively() {
    setInternalProperty(Constants.ADDITIONAL_ALLOWED_URL_TRANSPORTS, "MyHelper");

    Assertions.assertThat(GitRemoteUrlInspector.verifyUrl("MyHelper::something"))
      .as("Exact case from the override property must be allowed")
      .isNull();
    Assertions.assertThat(GitRemoteUrlInspector.verifyUrl("myhelper::something"))
      .as("A different case is not the same helper name and must stay rejected")
      .isEqualTo(GitRemoteUrlInspector.UrlRestriction.DISALLOWED_TRANSPORT);
  }

  @Test
  public void should_check_one_letter_scheme_against_allowlist_when_file_urls_are_allowed() {
    setInternalProperty(Constants.ALLOW_FILE_URL, "true");

    Assertions.assertThat(GitRemoteUrlInspector.verifyUrl("x://repo"))
      .as("A one-letter scheme is a transport, so allowing file URLs must not let it bypass the allowlist")
      .isEqualTo(GitRemoteUrlInspector.UrlRestriction.DISALLOWED_TRANSPORT);
  }

  @Test
  public void should_allow_one_letter_scheme_added_via_override_property() {
    setInternalProperty(Constants.ADDITIONAL_ALLOWED_URL_TRANSPORTS, "x");

    Assertions.assertThat(GitRemoteUrlInspector.verifyUrl("x://repo"))
      .as("Expected 'x' to be allowed once added to the override property")
      .isNull();
  }

  @Test
  public void should_skip_transport_and_malformed_checks_when_url_checks_are_disabled() {
    setInternalProperty(Constants.REMOTE_URL_CHECKS_ENABLED, "false");

    Assertions.assertThat(GitRemoteUrlInspector.verifyUrl("ext::sh /tmp/x.sh")).isNull();
    Assertions.assertThat(GitRemoteUrlInspector.verifyUrl("-oProxyCommand=id")).isNull();
  }

  @Test
  public void should_keep_local_file_check_when_url_checks_are_disabled() {
    setInternalProperty(Constants.REMOTE_URL_CHECKS_ENABLED, "false");

    Assertions.assertThat(GitRemoteUrlInspector.verifyUrl("file:///tmp/repo.git"))
      .as("The local file check has its own '" + Constants.ALLOW_FILE_URL + "' switch")
      .isEqualTo(GitRemoteUrlInspector.UrlRestriction.LOCAL_FILE_ACCESS);
  }

  @Test
  public void should_detect_disallowed_transport_regardless_of_url_checks_toggle() {
    setInternalProperty(Constants.REMOTE_URL_CHECKS_ENABLED, "false");

    Assertions.assertThat(GitRemoteUrlInspector.isDisallowedTransport("ext::sh /tmp/x.sh")).isTrue();
    Assertions.assertThat(GitRemoteUrlInspector.isDisallowedTransport("https://example.com/repo.git")).isFalse();
    Assertions.assertThat(GitRemoteUrlInspector.isDisallowedTransport("file:///tmp/repo.git"))
      .as("Local file URLs are reported by the local file URL check, not as a disallowed transport")
      .isFalse();
  }
}
