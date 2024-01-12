

package jetbrains.buildServer.buildTriggers.vcs.git;

import org.apache.http.impl.client.HttpClientBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

/**
 * Abstract patcher of https schemes in client builder.
 *
 * @author Mikhail Khorkov
 * @since 2019.1
 */
public interface SSLSchemePatcher {

  void apply(@NotNull HttpClientBuilder clientBuilder, @NotNull SSLContext sslContext, @Nullable HostnameVerifier hostnameVerifier);
}