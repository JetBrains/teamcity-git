

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import com.intellij.openapi.util.io.StreamUtil;
import com.sun.net.httpserver.*;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

class GitHttpServer {
  private final String myGitPath;
  private final File myRepo;
  private HttpServer myServer;
  private String myUser;
  private String myPassword;
  private int myPort = 8888;

  GitHttpServer(@NotNull String gitPath, @NotNull File repo) {
    myGitPath = gitPath;
    myRepo = repo;
  }


  void setCredentials(@NotNull String user, @NotNull String password) {
    myUser = user;
    myPassword = password;
  }


  @NotNull
  String getRepoUrl() {
    return "http://localhost:" + myPort + "/" + myRepo.getName();
  }

  public String getUser() {
    return myUser;
  }

  public String getPassword() {
    return myPassword;
  }

  void start() throws IOException {
    myServer = HttpServer.create(new InetSocketAddress(myPort), 0);
    HttpContext context = myServer.createContext("/" + myRepo.getName(), new HttpHandler() {
      @Override
      public void handle(final HttpExchange httpExchange) throws IOException {
        try {
          handleRequest(httpExchange);
        } catch (Exception e) {
          System.out.println("Error while handling request " + httpExchange.getRequestURI() + ": " + e.toString());
          e.printStackTrace();
          throw e;
        }
      }
    });
    context.setAuthenticator(new BasicAuthenticator(myRepo.getName()) {
      @Override
      public boolean checkCredentials(String user, String pwd) {
        if (myUser == null || myPassword == null)
          return true;
        return myUser.equals(user) && myPassword.equals(pwd);
      }
    });
    myServer.start();
  }


  void stop() {
    myServer.stop(0);
  }


  private void handleRequest(final HttpExchange httpExchange) throws IOException {
    ProcessBuilder processBuilder = new ProcessBuilder(myGitPath, "http-backend");
    configureEnv(httpExchange, processBuilder);
    Process process = processBuilder.start();
    writeStdin(httpExchange, process);
    Response response = Response.parse(StreamUtil.loadFromStream(process.getInputStream()));
    for (Map.Entry<String, String> e : response.getHeaders().entrySet()) {
      httpExchange.getResponseHeaders().add(e.getKey(), e.getValue());
    }
    httpExchange.sendResponseHeaders(response.getStatus(), response.getBody().length);
    OutputStream responseBody = httpExchange.getResponseBody();
    responseBody.write(response.getBody());
    responseBody.close();
  }


  private void writeStdin(@NotNull HttpExchange httpExchange, @NotNull Process process) throws IOException {
    StreamUtil.copyStreamContent(httpExchange.getRequestBody(), process.getOutputStream());
    process.getOutputStream().close();
  }


  private void configureEnv(@NotNull HttpExchange httpExchange, @NotNull ProcessBuilder processBuilder) throws IOException {
    Map<String, String> env = processBuilder.environment();
    env.put("REQUEST_METHOD", httpExchange.getRequestMethod());
    env.put("GIT_HTTP_EXPORT_ALL", "1");
    env.put("GIT_PROJECT_ROOT", myRepo.getCanonicalPath());
    env.put("PATH_INFO", httpExchange.getRequestURI().getPath().substring(("/" + myRepo.getName()).length()));
    String query = httpExchange.getRequestURI().getQuery();
    if (!StringUtil.isEmpty(query))
      env.put("QUERY_STRING", query);
    String contentType = httpExchange.getRequestHeaders().getFirst("Content-Type");
    if (!StringUtil.isEmpty(contentType))
      env.put("CONTENT_TYPE", contentType);
  }


  private static class Response {
    private byte[] myBody;
    private Map<String, String> myHeaders = new HashMap<>();
    private int myStatus;

    static Response parse(byte[] out) {
      Response result = new Response();

      int lineStart = 0;
      for (int i = 0, j = 1; j < out.length; i++, j++) {
        if (out[i] == '\r' && out[j] == '\n') {
          if (i >= 2 && out[i - 2] == '\r' && out[i - 1] == '\n') {//empty line after headers, rest is response body
            int bodyStart = j + 1;
            int bodyLen = out.length - bodyStart;
            result.myBody = new byte[bodyLen];
            System.arraycopy(out, bodyStart, result.myBody, 0, bodyLen);
            break;
          } else {
            String line = new String(out, lineStart, i - lineStart);
            int idx = line.indexOf(":");
            if (idx != -1)
              result.myHeaders.put(line.substring(0, idx), line.substring(idx + 1, line.length()).trim());
            lineStart = j + 1;
          }
        }
      }

      String statusHdr = result.myHeaders.remove("Status");
      if (statusHdr == null) {
        result.myStatus = 200;
      } else {
        result.myStatus = Integer.parseInt(statusHdr.split(" ")[0]);
      }
      return result;
    }

    byte[] getBody() {
      return myBody;
    }

    Map<String, String> getHeaders() {
      return myHeaders;
    }

    int getStatus() {
      return myStatus;
    }
  }
}