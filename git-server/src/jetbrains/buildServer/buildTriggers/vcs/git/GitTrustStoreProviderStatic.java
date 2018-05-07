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

package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.util.ssl.TrustStoreIO;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.security.KeyStore;

/**
 * Implementation of {@link GitTrustStoreProvider} for static folder.
 *
 * @author Mikhail Khorkov
 * @since tc-2018.1
 */
public class GitTrustStoreProviderStatic implements GitTrustStoreProvider {

  @Nullable
  private String myTrustedCertificatesDir;

  public GitTrustStoreProviderStatic(@Nullable final String trustedCertificatesDir) {
    myTrustedCertificatesDir = trustedCertificatesDir;
  }

  @Nullable
  @Override
  public KeyStore getTrustStore() {
    if (myTrustedCertificatesDir == null) {
      return null;
    } else {
      return TrustStoreIO.readTrustStoreFromDirectory(myTrustedCertificatesDir);
    }
  }

  @NotNull
  @Override
  public String serialize() {
    return myTrustedCertificatesDir != null ? myTrustedCertificatesDir : "";
  }

  @NotNull
  @Override
  public GitTrustStoreProvider deserialize(@NotNull final String serialized) {
    if (StringUtil.isEmptyOrSpaces(serialized)) {
      myTrustedCertificatesDir = null;
    } else {
      myTrustedCertificatesDir = serialized;
    }
    return this;
  }
}
