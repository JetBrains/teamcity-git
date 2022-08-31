/*
 * Copyright 2000-2022 JetBrains s.r.o.
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
import org.testng.Assert;
import org.testng.annotations.Test;

@Test
public class RetryTest extends BaseTestCase {
  @SuppressWarnings("MaskedAssertion")
  public void test_always_fail() {
    final StringBuilder result = new StringBuilder();

    final int attempts = 2;
    final Ref<Integer> attemptNum = new Ref(1);
    try {
      Retry.retry(new CommonRetryable(attemptNum, attempts + 1, result), attempts);
      fail("retry must fail");
    } catch (Throwable throwable) {
      //expected
    }
    assertContains(result.toString(), "WARN Failed to run operation within 2 attempts: jetbrains.buildServer.vcs.VcsException: the exception");
    assertEquals((int)attemptNum.get(), 3);
  }

  public void test_succeed() throws Throwable {
    final StringBuilder result = new StringBuilder();

    final int attempts = 3;
    Retry.retry(retryable(result, attempts - 1), attempts);
    assertContains(result.toString(), "INFO Exception occurred, will repeat operation in");
    //check count of attempts
  }

  public void test_always_retryable() throws Exception {
    StringBuilder result = new StringBuilder();

    final int attempts = 1;
    final Ref<Integer> attemptNum = new Ref(1);
    try {
      Retry.retry(new AlwaysRetryable(attemptNum, attempts, result), 0, attempts);
      Assert.fail();
    } catch (IllegalArgumentException e) {
      Assert.assertEquals(e.getMessage(), "At least one retry attempt expected");
    }
    Assert.assertEquals((int)attemptNum.get(), 4);
  }

  public void test_always_retryable_more_times() throws Exception {
    StringBuilder result = new StringBuilder();

    final int attempts = 11;
    final Ref<Integer> attemptNum = new Ref(1);
    try {
      Retry.retry(new AlwaysRetryable(attemptNum, attempts, result), 0, attempts);
      Assert.fail();
    } catch (IllegalArgumentException e) {
      Assert.assertEquals(e.getMessage(), "At least one retry attempt expected");
    }
    Assert.assertEquals((int)attemptNum.get(), 12);
  }

  @NotNull
  private Retry.Retryable<Void> retryable(@NotNull final StringBuilder result, int failAttempts) {
    final Ref<Integer> attemptNum = new Ref(1);
    return new CommonRetryable(attemptNum, failAttempts, result);
  }

  class CommonRetryable implements Retry.Retryable<Void> {
    Ref<Integer> myAttemptNum;
    int myFailAttempts;
    StringBuilder myResult;

    CommonRetryable(Ref<Integer> attemptNum, int failAttempts, StringBuilder result) {
      myAttemptNum = attemptNum;
      myFailAttempts = failAttempts;
      myResult = result;
    }

    @Override
    public boolean requiresRetry(@NotNull final Exception e, int attempt, int maxAttempts) {
      return attempt < maxAttempts;
    }

    @Nullable
    @Override
    public Void call() throws VcsException {
      int num = myAttemptNum.get();
      try {
        if (num <= myFailAttempts) throw new VcsException("the exception");
        return null;
      } finally {
        myAttemptNum.set(num + 1);
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
          myResult.append(category).append(" ").append(message);
          if (t != null) {
            myResult.append(": ").append(t.getMessage());
          }
          myResult.append("\n");
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
  }

  class AlwaysRetryable extends CommonRetryable {

    AlwaysRetryable(Ref<Integer> attemptNum, int failAttempts, StringBuilder result) {
      super(attemptNum, failAttempts, result);
    }

    @Override
    public boolean requiresRetry(@NotNull Exception e, int attempt, int maxAttempts) {
      return "must retry".equals(e.getMessage());
    }

    @Nullable
    @Override
    public Void call() throws VcsException {
      try {
        throw new VcsException("must retry");
      } finally {
        myAttemptNum.set(myAttemptNum.get()+1);
      }
    }
  }
}
