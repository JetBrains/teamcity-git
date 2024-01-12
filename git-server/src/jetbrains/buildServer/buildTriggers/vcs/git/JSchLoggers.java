

package jetbrains.buildServer.buildTriggers.vcs.git;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Logger;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import org.apache.log4j.Level;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Callable;

public interface JSchLoggers {

  @NotNull Logger STD_JSCH_LOGGER = createStdLogger(false);
  @NotNull Logger STD_DEBUG_JSCH_LOGGER = createStdLogger(true);

  @NotNull JSchLogger LOG4J_JSCH_LOGGER = new JSchLogger();

  static void initJSchLogger() {
    JSch.setLogger(LOG4J_JSCH_LOGGER);
  }

  static <V> V evaluateWithLoggingLevel(@NotNull Level level, @NotNull Callable<V> action) throws Exception {
    final Level prevLevel = LOG4J_JSCH_LOGGER.currentLevel.get();
    try {
      LOG4J_JSCH_LOGGER.currentLevel.set(level);
      return action.call();
    } finally {
      LOG4J_JSCH_LOGGER.currentLevel.set(prevLevel);
    }
  }

  final class JSchLogger implements Logger {

    @NotNull private final ThreadLocal<Level> currentLevel = ThreadLocal.withInitial(() -> Level.ERROR);

    @Override
    public boolean isEnabled(int level) {
      return isLevelEnabled(mapLevel(level));
    }

    @Override
    public void log(int level, final String message) {
      final Level log4jLevel = mapLevel(level);
      if (isLevelEnabled(log4jLevel)) {
        if (log4jLevel.isGreaterOrEqual(Level.ERROR)) {
          Loggers.VCS.error(message);
        } else if (log4jLevel.isGreaterOrEqual(Level.WARN)) {
          Loggers.VCS.warn(message);
        } else if (log4jLevel.isGreaterOrEqual(Level.INFO)) {
          Loggers.VCS.info(message);
        } else if (isLevelEnabled(Level.DEBUG) && !Loggers.VCS.isDebugEnabled()) { // try to be more verbose
          Loggers.VCS.info(message);
        } else {
          Loggers.VCS.debug(message);
        }
      }
    }

    private boolean isLevelEnabled(@NotNull Level level) {
      return level.isGreaterOrEqual(Level.toLevel(TeamCityProperties.getPropertyOrNull("teamcity.git.sshLoggingLevel"), currentLevel.get()));
    }

    @NotNull
    private Level mapLevel(int level) {
      switch (level) {
        case FATAL:
        case ERROR:
          return Level.ERROR;
        case WARN:
          return Level.WARN;
        case INFO:
          return Level.INFO;
        case DEBUG:
        default:
          return Level.DEBUG;
      }
    }
  }

  @NotNull
  static Logger createStdLogger(boolean verbose) {
    return new Logger() {
      @Override
      public boolean isEnabled(final int level) {
        return verbose || level > INFO;
      }

      @Override
      public void log(final int level, final String message) {
        if (isEnabled(level)) {
          switch (level) {
            case FATAL:
            case ERROR:
              System.err.println(message);
              break;
            case WARN:
            case INFO:
            case DEBUG:
            default:
              System.out.println(message);
              break;
          }
        }
      }
    };
  }
}