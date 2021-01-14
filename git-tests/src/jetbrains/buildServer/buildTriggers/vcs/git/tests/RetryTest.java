/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.buildTriggers.vcs.git.Retry;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.Test;

@Test
public class RetryTest extends BaseTestCase {
  @SuppressWarnings("MaskedAssertion")
  public void test_always_fail() {
    final StringBuilder result = new StringBuilder();

    final int attempts = 3;
    try {
      Retry.retry(retryable(result, attempts + 1), attempts);
      fail("retry must fail");
    } catch (Throwable throwable) {
      //expected
    }
    assertContains(result.toString(), "WARN Failed to run operation within 3 attempts: jetbrains.buildServer.vcs.VcsException: the exception");
  }

  public void test_succeed() throws Throwable {
    final StringBuilder result = new StringBuilder();

    final int attempts = 3;
    Retry.retry(retryable(result, attempts - 1), attempts);
    assertContains(result.toString(), "INFO Exception occurred, will repeat operation in");
  }

  @NotNull
  private Retry.Retryable<Void> retryable(@NotNull final StringBuilder result, int failAttempts) {
    final Ref<Integer> attemptNum = new Ref(1);
    return new Retry.Retryable<Void>() {
      @Override
      public boolean requiresRetry(@NotNull final Exception e) {
        return true;
      }

      @Nullable
      @Override
      public Void call() throws VcsException {
        int num = attemptNum.get();
        try {
          if (num <= failAttempts) throw new VcsException("the exception");
          return null;
        } finally {
          attemptNum.set(num + 1);
        }
      }

      @NotNull
      @Override
      public Logger getLogger() {
        return new DefaultLogger() {
          @Override
          public void debug(final String message) {
            throw new UnsupportedOperationException();
          }

          @Override
          public void debug(final Throwable t) {
            throw new UnsupportedOperationException();
          }

          @Override
          public void debug(final String message, final Throwable t) {
            append("DEBUG", message, t);
          }

          private void append(final String category, final String message, final Throwable t) {
            result.append(category).append(" ").append(message);
            if (t != null) {
              result.append(": ").append(t.getMessage());
            }
            result.append("\n");
          }

          @Override
          public void error(final String message, final Throwable t, final String... details) {
            throw new UnsupportedOperationException();
          }

          @Override
          public void info(final String message) {
            append("INFO", message, null);
          }

          @Override
          public void info(final String message, final Throwable t) {
            append("INFO", message, t);
          }

          @Override
          public void warn(final String message, final Throwable t) {
            append("WARN", message, t);
          }

          @Override
          public boolean isDebugEnabled() {
            return true;
          }
        };
      }
    };
  }
}
