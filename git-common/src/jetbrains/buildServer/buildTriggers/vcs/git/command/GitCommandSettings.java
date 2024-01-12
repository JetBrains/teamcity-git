

package jetbrains.buildServer.buildTriggers.vcs.git.command;

import java.util.Collections;
import java.util.Map;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthSettings;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.CommandUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GitCommandSettings {

  private Integer myTimeout = CommandUtil.DEFAULT_COMMAND_TIMEOUT_SEC;
  private AuthSettings myAuthSettings;
  private boolean myUseNativeSsh = false;
  private Map<String, String> myTraceEnv = Collections.emptyMap();
  private byte[] myInput = new byte[0];

  public static GitCommandSettings with() {
    return new GitCommandSettings();
  }

  public GitCommandSettings timeout(int timeout) {
    myTimeout = timeout;
    return this;
  }

  public GitCommandSettings authSettings(@NotNull AuthSettings authSettings) {
    myAuthSettings = authSettings;
    return this;
  }

  public GitCommandSettings useNativeSsh(boolean doUseNativeSsh) {
    myUseNativeSsh = doUseNativeSsh;
    return this;
  }

  public GitCommandSettings addInput(@NotNull byte[] input) {
    myInput = input;
    return this;
  }

  public GitCommandSettings trace(@NotNull Map<String, String> gitTraceEnv) {
    myTraceEnv = gitTraceEnv;
    return this;
  }

  public int getTimeout() {
    return myTimeout;
  }

  @Nullable
  public AuthSettings getAuthSettings() {
    return myAuthSettings;
  }

  public boolean isUseNativeSsh() {
    return myUseNativeSsh;
  }

  @NotNull
  public byte[] getInput() {
    return myInput;
  }

  @NotNull
  public Map<String, String> getTraceEnv() {
    return myTraceEnv;
  }

  boolean isTrace() {
    return !getTraceEnv().isEmpty();
  }
}