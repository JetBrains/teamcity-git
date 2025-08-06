

package jetbrains.buildServer.buildTriggers.vcs.git.command.credentials;

import com.intellij.openapi.diagnostic.Logger;
import java.io.*;
import jetbrains.buildServer.buildTriggers.vcs.git.AuthSettings;
import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.StringUtil;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ScriptGen {
  protected final Class<?>[] myClasses;

  protected final File myTempDir;

  public ScriptGen(@NotNull File tempDir) {
    myClasses = ArrayUtils.addAll(getLoggingClasses(), CredentialsHelper.class);
    myTempDir = tempDir;
  }

  @NotNull
  public abstract File generateAskPass(@NotNull AuthSettings authSettings) throws IOException;

  @NotNull
  public abstract File generateAskPass(@Nullable String password) throws IOException;

  @NotNull
  public abstract File generateCredentialHelper() throws IOException;

  protected String getJavaPath() {
    String javaHome = System.getProperty("java.home");
    if (StringUtil.isNotEmpty(javaHome)) {
      return "\"" + javaHome + File.separatorChar + "bin" + File.separatorChar + "java\"";
    }
    return "java";
  }

  protected String getLoggingSystemProperties() {
    if (TeamCityProperties.getBooleanOrTrue(Constants.CREDHELPER_LOGGING_ENABLED) && Logger.getInstance(ScriptGen.class).isDebugEnabled()) {
      return String.format("-Dlog4j2.configurationFile=%s -D%s=true -Dteamcity.git.debug.prefix=%s",
                           Constants.CREDHELPER_LOGGING_CONFIGURATION_FILE,
                           Constants.CREDHELPER_LOGGING_ENABLED,
                           Constants.GIT_LOGGING_PREFIX);
    }

    return "";
  }

  private Class<?>[] getLoggingClasses() {
    return new Class[]{
      org.slf4j.LoggerFactory.class,
      org.slf4j.impl.StaticLoggerBinder.class,
      com.intellij.openapi.diagnostic.Logger.class,
      org.apache.logging.log4j.Logger.class,
      org.apache.logging.log4j.LogManager.class,
      org.apache.logging.log4j.core.Core.class,
      org.apache.logging.log4j.core.layout.PatternLayout.class,
      org.apache.logging.log4j.core.appender.ConsoleAppender.class,
      org.apache.logging.log4j.core.config.LoggerConfig.class
    };
  }
}