package jetbrains.buildServer.buildTriggers.vcs.git.tests.ssh;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Logger;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.JSchClient;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.jetbrains.annotations.NotNull;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class JSchClientTest {

  @BeforeMethod
  public void setUp() {
    new TeamCityProperties() {{
      setBaseModel();
    }};
  }

  @Test(timeOut = 5_000 /* millis */, expectedExceptions = JSchException.class)
  public void connect_timeout_must_terminate() throws Exception {
    final NonResponsiveServer nonResponsiveServer = new NonResponsiveServer();
    nonResponsiveServer.start();

    final JSchClient.SSHCommandLine commandLine = JSchClient.SSHCommandLine.parse(new String[]{"localhost", "-p", String.valueOf(nonResponsiveServer.getPort())}, new NoOpLogger());
    final JSchClient client = commandLine.createClient();
    final Map<String, String> environment = ImmutableMap.of("TEAMCITY_SSH_CONNECT_TIMEOUT_SECONDS", "1");
    client.setEnvironmentAccessor(environment::get);

    try {
      client.run();
    } finally {
      nonResponsiveServer.shutdown();
    }
  }

  private static class NonResponsiveServer extends Thread {

    @NotNull
    private final ServerSocket myServerSocket;

    private volatile boolean myStopped;

    public NonResponsiveServer() throws IOException {
      myServerSocket = new ServerSocket(0);
      myStopped = false;
    }

    @Override
    public void run() {
      while (true) {
        if (myStopped) {
          doShutDown();
          return;
        }

        try {
          // do nothing with the connection
          myServerSocket.accept();
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }

    public int getPort() {
      return myServerSocket.getLocalPort();
    }

    public void shutdown() {
      myStopped = true;
    }

    private void doShutDown() {
      try {
        myServerSocket.close();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static class NoOpLogger implements Logger {
    @Override
    public boolean isEnabled(int level) {
      return false;
    }

    @Override
    public void log(int level, String message) {
    }
  }
}
