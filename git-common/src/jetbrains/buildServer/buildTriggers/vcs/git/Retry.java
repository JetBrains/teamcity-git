/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Retries the provided operation with exponential back off
 */
public abstract class Retry {

  private static final long INITIAL_DELAY_MS = TimeUnit.SECONDS.toMillis(5);
//  private static final long BACKOFF_LIMIT_MS = TimeUnit.MINUTES.toMillis(5);

  private static final int DEFAULT_MAX_ATTEMPTS = 10;

  private static final int BACKOFF_FACTOR = 2;
  private static final float BACKOFF_JITTER = 0.1f;

  public interface Retryable<V> {
    boolean requiresRetry(@NotNull Exception e);
    @Nullable V call() throws Exception;
    @NotNull Logger getLogger();
  }

  public static <V> V retry(@NotNull Retryable<V> operation) throws Exception {
    return retry(operation, DEFAULT_MAX_ATTEMPTS);
  }

  public static <V> V retry(@NotNull Retryable<V> operation, int attempts) throws Exception {
    return retry(operation, INITIAL_DELAY_MS, attempts);
  }

  public static <V> V retry(@NotNull Retryable<V> operation, long initialDelay, int attempts) throws Exception {
    long effectiveDelay = initialDelay;
    for (int i = 1; i <= attempts; ++i) {
      try {
        return operation.call();
      } catch (Exception e) {
        if (!operation.requiresRetry(e)) {
          throw e;
        }
        if (i == attempts) {
          operation.getLogger().warnAndDebugDetails("Failed to run operation within " + attempts + StringUtil.pluralize(" attempt", attempts), e);
          throw e;
        }
        if (i > 1) {
          effectiveDelay = backOff(effectiveDelay);
        }
        if (effectiveDelay > 0) {
          operation.getLogger().infoAndDebugDetails("Exception occurred, will repeat operation in " + effectiveDelay + "ms", e);
          Thread.sleep(effectiveDelay);
        }
      }
    }
    throw new IllegalArgumentException("At least one retry attempt expected");
  }

  @SuppressWarnings("unchecked")
  private static long backOff(long previousDelay) {
    return previousDelay * BACKOFF_FACTOR + (long)(new Random().nextGaussian() * previousDelay * BACKOFF_JITTER);
  }
}
