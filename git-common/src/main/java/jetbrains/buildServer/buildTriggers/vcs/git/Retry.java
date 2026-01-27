

package jetbrains.buildServer.buildTriggers.vcs.git;

import com.intellij.openapi.diagnostic.Logger;
import java.util.HashMap;
import java.util.Map;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

import static jetbrains.buildServer.buildTriggers.vcs.git.GitCommandRetryPolicy.INITIAL_DELAY_MS;
import static jetbrains.buildServer.buildTriggers.vcs.git.GitCommandRetryPolicy.RetryMode.NOT_REQUIRED;

/**
 * Retries the provided operation with exponential back off
 */
public abstract class Retry {
  private static final int DEFAULT_MAX_ATTEMPTS = 3;

  private static final int BACKOFF_FACTOR = 2;
  private static final float BACKOFF_JITTER = 0.1f;

  public interface Retryable<V> {
    boolean requiresRetry(@NotNull Exception e, int attempt, int maxAttempts);
    @Nullable V call() throws Exception;
    @NotNull Logger getLogger();
    default @NotNull GitCommandRetryPolicy findRetryPolicyForException(@NotNull Exception e, int attempt, int maxAttempts) {
      if (requiresRetry(e, attempt, maxAttempts)) {
        return new GitCommandRetryPolicy();
      }
      return new GitCommandRetryPolicy(NOT_REQUIRED);
    }
  }

  public static <V> V retry(@NotNull Retryable<V> operation) throws Exception {
    return retry(operation, DEFAULT_MAX_ATTEMPTS);
  }

  public static <V> V retry(@NotNull Retryable<V> operation, int attempts) throws Exception {
    return retry(operation, INITIAL_DELAY_MS, attempts);
  }

  public static <V> V retry(@NotNull Retryable<V> operation, long initialDefaultDelay, int attempts) throws Exception {
    long effectiveDelay = initialDefaultDelay;
    for (int i = 1; i <= Math.max(DEFAULT_MAX_ATTEMPTS, attempts); ++i) {
      try {
        return operation.call();
      } catch (Exception e) {
        GitCommandRetryPolicy retryPolicy = operation.findRetryPolicyForException(e, i, attempts);

        if (retryPolicy.getMode() == NOT_REQUIRED) {
          if (i >= attempts) {
            operation.getLogger().warnAndDebugDetails("Failed to run operation within " + attempts + StringUtil.pluralize(" attempt", attempts), e);
          }
          throw e;
        }

        long delayMs;
        if(retryPolicy.getMode() == GitCommandRetryPolicy.RetryMode.CUSTOM) {
          delayMs = retryPolicy.getDelayMs();
        } else {
          if (i > 1) {
            effectiveDelay = backOff(effectiveDelay);
          }
          delayMs = effectiveDelay;
        }

        if (delayMs > 0) {
          operation.getLogger().infoAndDebugDetails("Exception occurred, will repeat operation in " + delayMs + "ms", e);
          Thread.sleep(delayMs);
        }
      }
    }
    throw new IllegalArgumentException("At least one retry attempt expected");
  }

  @SuppressWarnings("unchecked")
  public static long backOff(long previousDelay) {
    return previousDelay * BACKOFF_FACTOR + (long)(new Random().nextGaussian() * previousDelay * BACKOFF_JITTER);
  }

  @NotNull
  public static Map<String, Long> aggregateCustomDelayMessages(@NotNull String customRecoverableMessagesPrefix, @NotNull Map<String, String> properties) {
    Map<String, Map<String, String>> aggregatedCustomProperties = PropertiesHelper.aggregatePropertiesByAlias(properties, customRecoverableMessagesPrefix);

    Map<String, Long> result = new HashMap<>();
    for(Map<String, String> retryProperty : aggregatedCustomProperties.values()) {
      String errorMessage = retryProperty.get(customRecoverableMessagesPrefix + ".msg");
      if (errorMessage != null) {
        long delayMs = INITIAL_DELAY_MS;
        String delayValue = retryProperty.get(customRecoverableMessagesPrefix + ".delayMs");
        if (!StringUtil.isEmptyOrSpaces(delayValue)) {
          delayMs = Long.parseLong(delayValue);
        }

        result.put(errorMessage, delayMs);
      }
    }

    return result;
  }
}