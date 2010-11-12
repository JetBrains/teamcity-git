/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package jetbrains.buildServer.buildTriggers.vcs.git.ssh;

import com.intellij.openapi.diagnostic.Logger;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import jetbrains.buildServer.configuration.ChangeListener;
import jetbrains.buildServer.configuration.FilesWatcher;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.util.FS;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * A {@link SshSessionFactory} that refreshes it states when the content of ~/.ssh directory changes. The factory delegates its work to {@link HeadlessSshSessionFactory}.
 */
public class RefreshableSshConfigSessionFactory extends SshSessionFactory {
  /**
   * logger instance
   */
  private static Logger LOG = Logger.getInstance(RefreshableSshConfigSessionFactory.class.getName());

  /**
   * The delegate factory
   */
  private SshSessionFactory myDelegate;
  /**
   * The lock that guards {@link #myDelegate}
   */
  private final Object delegateLock = new Object();

  /**
   * A constructor
   */
  public RefreshableSshConfigSessionFactory() {
    FilesWatcher watcher = new FilesWatcher(new FilesWatcher.WatchedFilesProvider() {
      public File[] getWatchedFiles() {
        File sshDir = new File(System.getProperty("user.home"), ".ssh");
        return new File[] {sshDir};
      }
    });
    watcher.registerListener(new ChangeListener() {
      public void changeOccured(String requestor) {
        expireDelegate();
      }
    });
    watcher.start();
  }

  /**
   * @return the delegate session factory
   */
  @NotNull
  private SshSessionFactory delegate() {
    synchronized (delegateLock) {
      if (myDelegate == null) {
        LOG.info("Reloading SSH configuration information for Git plugin");
        myDelegate = new HeadlessSshSessionFactory();
      }
      return myDelegate;
    }
  }

  /**
   * Expire the delegate
   */
  private void expireDelegate() {
    synchronized (delegateLock) {
      myDelegate = null;
    }
  }

  /**
   * {@inheritDoc}
   */
  public Session getSession(String user, String pass, String host, int port, FS fs) throws JSchException {
    return delegate().getSession(user, pass, host, port, fs);
  }
}
