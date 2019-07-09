/*
 * Copyright 2000-2019 JetBrains s.r.o.
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

import jetbrains.buildServer.util.ssl.SSLContextUtil;
import org.apache.http.*;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.HttpContext;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.transport.http.apache.HttpClientConnection;
import org.eclipse.jgit.transport.http.apache.TemporaryBufferEntity;
import org.eclipse.jgit.transport.http.apache.internal.HttpApacheText;
import org.eclipse.jgit.util.TemporaryBuffer;
import org.jetbrains.annotations.NotNull;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.eclipse.jgit.util.HttpSupport.*;
import static org.eclipse.jgit.util.HttpSupport.METHOD_POST;

/**
 * {@link HttpClientConnection} with support of custom ssl trust store.
 *
 * It is almost copy-paste of {@link HttpClientConnection}.
 *
 * @author Mikhail Khorkov
 * @since 2018.1
 */
public class SSLHttpClientConnection implements HttpConnection {
  CloseableHttpClient client;

  URL url;

  HttpUriRequest req;

  CloseableHttpResponse resp = null;

  String method = "GET"; //$NON-NLS-1$

  private TemporaryBufferEntity entity;

  private boolean isUsingProxy = false;

  private Proxy proxy;

  private Integer timeout = null;

  private Integer readTimeout;

  private Boolean followRedirects;

  private HostnameVerifier hostnameverifier;

  SSLContext ctx;

  private Map<String, Object> attributes = new HashMap<String, Object>();

  @NotNull
  private Supplier<KeyStore> myTrustStoreGetter = () -> null;

  private SSLSchemePatcher mySSLSchemePatcher;

  public SSLHttpClientConnection(
    final String urlStr,
    final SSLSchemePatcher sslSchemePatcher
  ) throws MalformedURLException {
    this(urlStr, null, sslSchemePatcher);
  }

  public SSLHttpClientConnection(
    final String urlStr,
    final Proxy proxy,
    final SSLSchemePatcher sslSchemePatcher
  ) throws MalformedURLException {
    this(urlStr, proxy, null, sslSchemePatcher);
  }

  public SSLHttpClientConnection(
    final String urlStr,
    final Proxy proxy,
    final CloseableHttpClient cl,
    final SSLSchemePatcher sslSchemePatcher
  ) throws MalformedURLException {
    this.client = cl;
    this.url = new URL(urlStr);
    this.proxy = proxy;

    mySSLSchemePatcher = sslSchemePatcher;
  }

  private CloseableHttpClient getClient() {
    if (client == null) {
      HttpClientBuilder clientBuilder = HttpClients.custom();
      RequestConfig.Builder configBuilder = RequestConfig.custom();
      if (proxy != null && !Proxy.NO_PROXY.equals(proxy)) {
        isUsingProxy = true;
        InetSocketAddress adr = (InetSocketAddress) proxy.address();
        clientBuilder.setProxy(
          new HttpHost(adr.getHostName(), adr.getPort()));
      }
      if (timeout != null) {
        configBuilder.setConnectTimeout(timeout.intValue());
      }
      if (readTimeout != null) {
        configBuilder.setSocketTimeout(readTimeout.intValue());
      }
      if (followRedirects != null) {
        configBuilder
          .setRedirectsEnabled(followRedirects.booleanValue());
      }
      mySSLSchemePatcher.apply(clientBuilder, getSSLContext(), hostnameverifier);
      clientBuilder.setDefaultRequestConfig(configBuilder.build());
      client = clientBuilder.build();
    }

    return client;
  }

  private SSLContext getSSLContext() {
    final KeyStore trustStore = myTrustStoreGetter.get();
    if (trustStore != null) {
      ctx = SSLContextUtil.createUserSSLContext(trustStore);
    }
    if (ctx == null) {
      try {
        ctx = SSLContext.getInstance("TLS"); //$NON-NLS-1$
        ctx.init(null, null, null);
      } catch (NoSuchAlgorithmException | KeyManagementException e) {
        throw new IllegalStateException(
          HttpApacheText.get().unexpectedSSLContextException, e);
      }
    }
    return ctx;
  }

  /**
   * Sets the buffer from which to take the request body
   *
   * @param buffer
   */
  public void setBuffer(TemporaryBuffer buffer) {
    this.entity = new TemporaryBufferEntity(buffer);
  }

  /** {@inheritDoc} */
  @Override
  public int getResponseCode() throws IOException {
    execute();
    return resp.getStatusLine().getStatusCode();
  }

  /** {@inheritDoc} */
  @Override
  public URL getURL() {
    return url;
  }

  /** {@inheritDoc} */
  @Override
  public String getResponseMessage() throws IOException {
    execute();
    return resp.getStatusLine().getReasonPhrase();
  }

  private void execute() throws IOException, ClientProtocolException {
    if (resp != null) {
      return;
    }

    if (entity == null) {
      resp = getClient().execute(req, new SSLHttpClientConnection.ConnectionHttpContext());
      return;
    }

    try {
      if (req instanceof HttpEntityEnclosingRequest) {
        HttpEntityEnclosingRequest eReq = (HttpEntityEnclosingRequest) req;
        eReq.setEntity(entity);
      }
      resp = getClient().execute(req, new SSLHttpClientConnection.ConnectionHttpContext());
    } finally {
      entity.close();
      entity = null;
    }
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, List<String>> getHeaderFields() {
    Map<String, List<String>> ret = new HashMap<>();
    for (Header hdr : resp.getAllHeaders()) {
      List<String> list = ret.get(hdr.getName());
      if (list == null) {
        list = new LinkedList<>();
        ret.put(hdr.getName(), list);
      }
      for (HeaderElement hdrElem : hdr.getElements()) {
        list.add(hdrElem.toString());
      }
    }
    return ret;
  }

  /** {@inheritDoc} */
  @Override
  public void setRequestProperty(String name, String value) {
    req.addHeader(name, value);
  }

  /** {@inheritDoc} */
  @Override
  public void setRequestMethod(String method) throws ProtocolException {
    this.method = method;
    if (METHOD_GET.equalsIgnoreCase(method)) {
      req = new HttpGet(url.toString());
    } else if (METHOD_HEAD.equalsIgnoreCase(method)) {
      req = new HttpHead(url.toString());
    } else if (METHOD_PUT.equalsIgnoreCase(method)) {
      req = new HttpPut(url.toString());
    } else if (METHOD_POST.equalsIgnoreCase(method)) {
      req = new HttpPost(url.toString());
    } else {
      this.method = null;
      throw new UnsupportedOperationException();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setUseCaches(boolean usecaches) {
    // not needed
  }

  /** {@inheritDoc} */
  @Override
  public void setConnectTimeout(int timeout) {
    this.timeout = Integer.valueOf(timeout);
  }

  /** {@inheritDoc} */
  @Override
  public void setReadTimeout(int readTimeout) {
    this.readTimeout = Integer.valueOf(readTimeout);
  }

  /** {@inheritDoc} */
  @Override
  public String getContentType() {
    HttpEntity responseEntity = resp.getEntity();
    if (responseEntity != null) {
      Header contentType = responseEntity.getContentType();
      if (contentType != null)
        return contentType.getValue();
    }
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public InputStream getInputStream() throws IOException {
    final InputStream delegate = resp.getEntity().getContent();
    return new InputStream() {
      public int read() throws IOException {
        return delegate.read();
      }

      public int read(@NotNull final byte[] b) throws IOException {
        return delegate.read(b);
      }

      public int read(@NotNull final byte[] b, final int off, final int len) throws IOException {
        return delegate.read(b, off, len);
      }

      public long skip(final long n) throws IOException {
        return delegate.skip(n);
      }

      public int available() throws IOException {
        return delegate.available();
      }

      public void close() throws IOException {
        try {
          delegate.close();
        } catch (Throwable ignore) {}
        try {
          resp.close();
        } catch (Throwable ignore) {}
        try {
          client.close();
          client = null;
        } catch (Throwable ignore) {}
      }

      public void mark(final int readlimit) {
        delegate.mark(readlimit);
      }

      public void reset() throws IOException {
        delegate.reset();
      }

      public boolean markSupported() {
        return delegate.markSupported();
      }
    };
  }

  // will return only the first field
  /** {@inheritDoc} */
  @Override
  public String getHeaderField(@NonNull String name) {
    Header header = resp.getFirstHeader(name);
    return (header == null) ? null : header.getValue();
  }

  @Override
  public List<String> getHeaderFields(@NonNull String name) {
    return Collections.unmodifiableList(Arrays.asList(resp.getHeaders(name))
                                          .stream().map(Header::getValue).collect(Collectors.toList()));
  }

  /** {@inheritDoc} */
  @Override
  public int getContentLength() {
    Header contentLength = resp.getFirstHeader("content-length"); //$NON-NLS-1$
    if (contentLength == null) {
      return -1;
    }

    try {
      int l = Integer.parseInt(contentLength.getValue());
      return l < 0 ? -1 : l;
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setInstanceFollowRedirects(boolean followRedirects) {
    this.followRedirects = Boolean.valueOf(followRedirects);
  }

  /** {@inheritDoc} */
  @Override
  public void setDoOutput(boolean dooutput) {
    // TODO: check whether we can really ignore this.
  }

  /** {@inheritDoc} */
  @Override
  public void setFixedLengthStreamingMode(int contentLength) {
    if (entity != null)
      throw new IllegalArgumentException();
    entity = new TemporaryBufferEntity(new TemporaryBuffer.LocalFile(null));
    entity.setContentLength(contentLength);
  }

  /** {@inheritDoc} */
  @Override
  public OutputStream getOutputStream() throws IOException {
    if (entity == null)
      entity = new TemporaryBufferEntity(new TemporaryBuffer.LocalFile(null));
    return entity.getBuffer();
  }

  /** {@inheritDoc} */
  @Override
  public void setChunkedStreamingMode(int chunklen) {
    if (entity == null)
      entity = new TemporaryBufferEntity(new TemporaryBuffer.LocalFile(null));
    entity.setChunked(true);
  }

  /** {@inheritDoc} */
  @Override
  public String getRequestMethod() {
    return method;
  }

  /** {@inheritDoc} */
  @Override
  public boolean usingProxy() {
    return isUsingProxy;
  }

  /** {@inheritDoc} */
  @Override
  public void connect() throws IOException {
    execute();
  }

  /** {@inheritDoc} */
  @Override
  public void setHostnameVerifier(HostnameVerifier hostnameverifier) {
    this.hostnameverifier = hostnameverifier;
  }

  /** {@inheritDoc} */
  @Override
  public void configure(KeyManager[] km, TrustManager[] tm,
                        SecureRandom random) throws KeyManagementException {
    getSSLContext().init(km, tm, random);
  }

  public synchronized void setAttribute(String name, Object value) {
    attributes.put(name, value);
  }

  private class ConnectionHttpContext implements HttpContext {
    public synchronized Object getAttribute(String s) {
      return attributes.get(s);
    }

    public synchronized void setAttribute(String s, Object o) {
      attributes.put(s, o);
    }

    public synchronized Object removeAttribute(String s) {
      return attributes.remove(s);
    }
  }

  public void setTrustStoreGetter(@NotNull final Supplier<KeyStore> trustStoreGetter) {
    myTrustStoreGetter = trustStoreGetter;
  }
}

