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
    final Level prevLevel = LOG4J_JSCH_LOGGER.currentLevel;
    try {
      LOG4J_JSCH_LOGGER.currentLevel = level;
      return action.call();
    } finally {
      LOG4J_JSCH_LOGGER.currentLevel = prevLevel;
    }
  }

  final class JSchLogger implements Logger {

    @NotNull private volatile Level currentLevel = Level.WARN;

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
        } else if (log4jLevel.isGreaterOrEqual(Level.INFO) || isLevelEnabled(Level.DEBUG) && !Loggers.VCS.isDebugEnabled()) {
          Loggers.VCS.info(message);
        } else {
          Loggers.VCS.debug(message);
        }
      }
    }

    private boolean isLevelEnabled(@NotNull Level level) {
      return level.isGreaterOrEqual(Level.toLevel(TeamCityProperties.getPropertyOrNull("teamcity.git.sshLoggingLevel"), currentLevel));
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
