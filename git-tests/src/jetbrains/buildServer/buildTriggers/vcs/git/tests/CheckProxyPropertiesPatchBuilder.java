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
