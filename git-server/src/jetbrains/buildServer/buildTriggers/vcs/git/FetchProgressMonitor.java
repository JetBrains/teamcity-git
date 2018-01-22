/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

import org.eclipse.jgit.lib.ProgressMonitor;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Exactly the same as {@link org.eclipse.jgit.lib.TextProgressMonitor}, but writes to the given PrintStream and System.out
 * @author dmitry.neverov
 */
public class FetchProgressMonitor implements ProgressMonitor {

  private final SimpleDateFormat myDateFormat = new SimpleDateFormat("[yyyy-MM-dd HH:mm:ss,SS]");

  private final PrintStream myPrintStream;

  private boolean output;

  private long taskBeganAt;

  private String msg;

  private int lastWorked;

  private int totalWork;

  /** Initialize a new progress monitor. */
  public FetchProgressMonitor(@NotNull PrintStream output) {
    taskBeganAt = System.currentTimeMillis();
    myPrintStream = output;
  }

  public void start(final int totalTasks) {
    // Ignore the number of tasks.
    taskBeganAt = System.currentTimeMillis();
  }

  public void beginTask(final String title, final int total) {
    endTask();
    msg = title;
    lastWorked = 0;
    totalWork = total;
  }

  public void update(final int completed) {
    if (msg == null)
      return;

    final int cmp = lastWorked + completed;
    if (!output && System.currentTimeMillis() - taskBeganAt < 500)
      return;
    if (totalWork == UNKNOWN) {
      display(cmp);
      myPrintStream.flush();
      System.out.flush();
    } else {
      if ((cmp * 100 / totalWork) != (lastWorked * 100) / totalWork) {
        display(cmp);
        myPrintStream.flush();
        System.out.flush();
      }
    }
    lastWorked = cmp;
    output = true;
  }

  @NotNull
  private synchronized String getTimestamp() {
    return myDateFormat.format(new Date());
  }

  private void display(final int cmp) {
    final StringBuilder m = new StringBuilder();
    m.append(getTimestamp()).append(" ");
    m.append(msg);
    m.append(": ");
    while (m.length() < 25)
      m.append(' ');

    if (totalWork == UNKNOWN) {
      m.append(cmp);
    } else {
      final String twstr = String.valueOf(totalWork);
      String cmpstr = String.valueOf(cmp);
      while (cmpstr.length() < twstr.length())
        cmpstr = " " + cmpstr;
      final int pcnt = (cmp * 100 / totalWork);
      if (pcnt < 100)
        m.append(' ');
      if (pcnt < 10)
        m.append(' ');
      m.append(pcnt);
      m.append("% (");
      m.append(cmpstr);
      m.append("/");
      m.append(twstr);
      m.append(")");
    }

    myPrintStream.println(m);
    displayStdout(cmp);
  }

  private void displayStdout(final int cmp) {
    final StringBuilder m = new StringBuilder();
    m.append(msg);
    m.append(": ");
    while (m.length() < 25)
      m.append(' ');

    if (totalWork == UNKNOWN) {
      m.append(cmp);
    } else {
      final String twstr = String.valueOf(totalWork);
      String cmpstr = String.valueOf(cmp);
      while (cmpstr.length() < twstr.length())
        cmpstr = " " + cmpstr;
      final int pcnt = (cmp * 100 / totalWork);
      if (pcnt < 100)
        m.append(' ');
      if (pcnt < 10)
        m.append(' ');
      m.append(pcnt);
      m.append("%");
    }

    System.out.println(m);
  }

  public boolean isCancelled() {
    return false;
  }

  public void endTask() {
    if (output) {
      if (totalWork != UNKNOWN)
        display(totalWork);
      myPrintStream.println();
      System.out.println();
    }
    output = false;
    msg = null;
  }
}
