

package jetbrains.buildServer.buildTriggers.vcs.git.command.credentials;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Git credentials helper.
 *
 * Called by scripts generated by {@link ScriptGen}.
 * Supports only the 'get' operation. The generated script handles the 'erase' operation.
 * Credentials are passed via environment variable by agent-checkout process.
 * Clients can use {@link CredentialsHelperConfig} class to generate environment variable
 */
public class CredentialsHelper {

  public static final String SUPPORTED_OPERATION = "get";
  private static final String CRED_PREFIX = "TEAMCITY_GIT_CREDENTIALS_";
  static final String CRED_URL = "URL";
  static final String CRED_USER = "USER";
  static final String CRED_PWD = "PWD";
  static final String CRED_MATCH_ALL_URLS = "MATCH_ALL_URLS";

  private final InputStream myIn;
  private final OutputStream myOut;
  private final Map<String, String> myEnv;
  private final String[] myArgs;

  public CredentialsHelper(@NotNull InputStream in,
                           @NotNull OutputStream out,
                           @NotNull Map<String, String> env,
                           @NotNull String... args) {
    myIn = in;
    myOut = out;
    myEnv = env;
    myArgs = args;
  }

  public static void main(String... args) throws Exception {
    new CredentialsHelper(System.in, System.out, System.getenv(), args).run();
  }


  public void run() throws IOException {
    if (myArgs.length < 1)
      return;

    String operation = myArgs[0];
    if (!SUPPORTED_OPERATION.equals(operation))
      return;

    Credentials credentials = Credentials.parseCredentials(myEnv);
    Context context = Context.parseContext(myIn);
    if (credentials.fill(context))
      context.printResult(myOut);
  }


  private static class Context {
    private String myProtocol;
    private String myHost;
    private String myPath;
    private String myUsername;
    private String myPassword;


    @NotNull
    private static Context parseContext(@NotNull InputStream in) throws IOException {
      Context result = new Context();
      final LinkedHashMap<String, String> attributes = new LinkedHashMap<String, String>();
      final BufferedReader reader = new BufferedReader(new InputStreamReader(in));
      String line = null;
      while ((line = reader.readLine()) != null) {
        if (line.length() == 0) {
          break;
        } else {
          int idx = line.indexOf("=");
          if (idx > 0)
            attributes.put(line.substring(0, idx), line.substring(idx + 1, line.length()));
        }
      }
      if (attributes.get("protocol") != null)
        result.myProtocol = attributes.get("protocol");
      if (attributes.get("host") != null)
        result.myHost = attributes.get("host");
      if (attributes.get("path") != null)
        result.myPath = attributes.get("path");
      if (attributes.get("username") != null)
        result.myUsername = attributes.get("username");
      if (attributes.get("password") != null)
        result.myPassword = attributes.get("password");
      return result;
    }


    private void printResult(@NotNull OutputStream out) throws IOException {
      PrintWriter writer = new PrintWriter(out);
      if (myProtocol != null)
        writer.print("protocol=" + myProtocol + "\n");
      if (myHost != null)
        writer.print("host=" + myHost + "\n");
      if (myPath != null)
        writer.print("path=" + myPath + "\n");
      if (myUsername != null)
        writer.print("username=" + myUsername + "\n");
      if (myPassword != null)
        writer.print("password=" + myPassword + "\n");
      writer.print("\n");
      writer.flush();
    }
  }


  private static class Credentials {
    private boolean myMatchAllUrls = false;//when set to true credentials are provided for every URL
    //url -> credentials
    private final Map<String, Cred> myCredentials = new HashMap<String, Cred>();

    boolean fill(@NotNull Context context) {
      Cred credentials = findCredentials(context);
      if (credentials != null) {
        context.myUsername = credentials.myUsername;
        context.myPassword = credentials.myPassword;
        return true;
      } else {
        return false;
      }
    }

    @Nullable
    private Cred findCredentials(@NotNull Context context) {
      for (Map.Entry<String, Cred> e : myCredentials.entrySet()) {
        try {
          URL url = new URL(e.getKey());
          if (matches(context, url)) {
            Cred cred = e.getValue();
            if (context.myUsername == null || cred.myUsername.equals(context.myUsername))
              return cred;
          }
        } catch (MalformedURLException ignored) {
        }
      }
      return null;
    }

    private boolean matches(@NotNull Context context, @NotNull URL url) {
      if (myMatchAllUrls)
        return true;
      if (!url.getProtocol().equals(context.myProtocol))
        return false;
      String hostPort = url.getHost();
      if (url.getPort() != -1)
        hostPort += ":" + url.getPort();
      if (!hostPort.equals(context.myHost))
        return false;

      String path = url.getPath();
      return context.myPath == null || path.equals(context.myPath);
    }


    @NotNull
    static Credentials parseCredentials(@NotNull Map<String, String> env) {
      int i = 1;
      Credentials result = new Credentials();
      while (true) {
        String url = env.get(credEnv(i, CRED_URL));
        if (url == null)
          break;
        String username = env.get(credEnv(i, CRED_USER));
        String password = env.get(credEnv(i, CRED_PWD));
        result.addCredentials(url, username, password);
        i++;
      }
      String matchAllUrls = env.get(credEnv(CRED_MATCH_ALL_URLS));
      if (matchAllUrls != null)
        result.setMatchAllUrls(Boolean.valueOf(matchAllUrls));
      return result;
    }

    private void addCredentials(@NotNull String url, @Nullable String username, @NotNull String password) {
      myCredentials.put(url, new Cred(username, password));
    }


    private void setMatchAllUrls(boolean matchAllUrls) {
      myMatchAllUrls = matchAllUrls;
    }


    private final static class Cred {
      private final String myUsername;
      private final String myPassword;
      Cred(String username, String password) {
        myUsername = username;
        myPassword = password;
      }
    }
  }


  static String credEnv(@NotNull String name) {
    return CredentialsHelper.CRED_PREFIX + name;
  }

  static String credEnv(int i, @NotNull String name) {
    return CredentialsHelper.CRED_PREFIX + i + "_" + name;
  }
}