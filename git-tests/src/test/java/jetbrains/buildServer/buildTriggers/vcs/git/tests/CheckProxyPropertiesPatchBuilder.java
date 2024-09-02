

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import jetbrains.buildServer.buildTriggers.vcs.git.patch.GitPatchProcess;
import org.jetbrains.annotations.NotNull;

public class CheckProxyPropertiesPatchBuilder {
  public static void main(String... args) throws Exception {
    assertSystemProperty("http.proxyHost", "httpProxyHost");
    assertSystemProperty("http.proxyPort", "81");
    assertSystemProperty("https.proxyHost", "httpsProxyHost");
    assertSystemProperty("https.proxyPort", "82");
    assertSystemProperty("http.nonProxyHosts", "some.org");
    assertSystemProperty("teamcity.git.sshProxyType", "http");
    assertSystemProperty("teamcity.git.sshProxyHost", "sshProxyHost");
    assertSystemProperty("teamcity.git.sshProxyPort", "83");
    GitPatchProcess.main(args);
  }

  private static void assertSystemProperty(@NotNull String name, @NotNull String expectedValue) {
    String actualValue = System.getProperty(name);
    if (!expectedValue.equals(actualValue)) {
      if (actualValue == null)
        throw new RuntimeException("System property " + name + " is not specified, expected value " + expectedValue);
      throw new RuntimeException("System property " + name + " = " + actualValue + ", expected value " + expectedValue);
    }
  }
}