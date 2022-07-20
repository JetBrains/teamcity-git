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

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.security.KeyStore;

/**
 * Abstract provider of trust store for ssl connections.
 *
 * @author Mikhail Khorkov
 * @since tc-2018.1
 */
public interface GitTrustStoreProvider {

  /**
   * Returns trust store or <code>null</code>.
   */
  @Nullable
  KeyStore getTrustStore();

  /**
   * @return directory where trusted certificates are located or null if this directory is unknown
   */
  @Nullable
  File getTrustedCertificatesDir();
}
