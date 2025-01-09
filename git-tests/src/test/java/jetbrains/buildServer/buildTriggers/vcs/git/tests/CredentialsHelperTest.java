

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.buildTriggers.vcs.git.command.credentials.CredentialsHelper;
import jetbrains.buildServer.buildTriggers.vcs.git.command.credentials.CredentialsHelperConfig;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.TestFor;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;

import static jetbrains.buildServer.buildTriggers.vcs.git.command.credentials.CredentialsHelper.*;
import static jetbrains.buildServer.util.Util.map;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.data.MapEntry.entry;

@Test
public class CredentialsHelperTest {

  public void should_produce_no_output_when_credentials_not_found() throws Exception {
    Map<String, String> out = run(map("protocol", "https",
                                      "host", "acme.org",
                                      "username", "git",
                                      "path", "/user/repo.git"),
                                  map());
    then(out).isEmpty();
  }


  public void fill_credentials() throws Exception {
    CredentialsHelperConfig config = new CredentialsHelperConfig();
    config.addCredentials("https://acme.org/user/repo.git", "git", "secret");
    Map<String, String> out = run(
      map("protocol", "https",
          "host", "acme.org",
          "path", "/user/repo.git"),
      config.getEnv());

    then(out).contains(entry("protocol", "https"),
                       entry("host", "acme.org"),
                       entry("path", "/user/repo.git"),
                       entry("username", "git"),
                       entry("password", "secret"));
  }


  public void several_credentials() throws Exception {
    CredentialsHelperConfig config = new CredentialsHelperConfig();
    config.addCredentials("https://unknown.org/user/repo.git", "user1", "secret1");
    config.addCredentials("https://acme.org/user/repo.git", "user2", "secret2");
    Map<String, String> out = run(
      map("protocol", "https",
          "host", "acme.org",
          "path", "/user/repo.git"),
      config.getEnv());

    then(out).contains(entry("protocol", "https"),
                       entry("host", "acme.org"),
                       entry("path", "/user/repo.git"),
                       entry("username", "user2"),
                       entry("password", "secret2"));
  }


  @TestFor(issues = "TW-49376")
  public void match_all_urls() throws Exception {
    CredentialsHelperConfig config = new CredentialsHelperConfig();
    config.addCredentials("https://acme.org/user/repo.git", "git", "secret");
    config.setMatchAllUrls(true);
    Map<String, String> out = run(
      map("protocol", "https",
          "host", "unknown.org",
          "path", "/some/path"),
      config.getEnv());

    then(out).contains(entry("protocol", "https"),
                       entry("host", "unknown.org"),
                       entry("path", "/some/path"),
                       entry("username", "git"),
                       entry("password", "secret"));
  }

  @TestFor(issues = "TW-73873")
  public void null_username() throws Exception {
    CredentialsHelperConfig config = new CredentialsHelperConfig();
    config.addCredentials("https://acme.org/user/repo.git", null, "secret");
    config.setMatchAllUrls(true);
    Map<String, String> out = run(
      map("protocol", "https",
          "host", "unknown.org",
          "path", "/some/path"),
      config.getEnv());

    then(out).contains(entry("protocol", "https"),
                       entry("host", "unknown.org"),
                       entry("path", "/some/path"),
                       entry("password", "secret"));
  }

  @TestFor(issues = "TW-91547")
  public void null_password() throws Exception {
    Map<String, String> env = map(
      credEnv(1, CRED_URL), "https://acme.org/user/repo.git",
      credEnv(1, CRED_USER), "user"
    );
    Map<String, String> out = run(
      map("protocol", "https",
          "host", "unknown.org",
          "path", "/some/path"),
      env);

    then(out).isEmpty();
  }


  @NotNull
  private Map<String, String> run(@NotNull Map<String, String> input,
                                  @NotNull Map<String, String> env) throws IOException {
    StringBuilder in = new StringBuilder();
    for (Map.Entry<String, String> e : input.entrySet()) {
      in.append(e.getKey()).append("=").append(e.getValue()).append("\n");
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    new CredentialsHelper(new ByteArrayInputStream(in.toString().getBytes()), out, env, CredentialsHelper.SUPPORTED_OPERATION).run();
    String output = new String(out.toByteArray());
    Map<String, String> result = new HashMap<String, String>();
    for (String line : StringUtil.splitByLines(output)) {
      int idx = line.indexOf("=");
      if (idx > 0) {
        result.put(line.substring(0, idx), line.substring(idx + 1, line.length()));
      }
    }
    return result;
  }
}