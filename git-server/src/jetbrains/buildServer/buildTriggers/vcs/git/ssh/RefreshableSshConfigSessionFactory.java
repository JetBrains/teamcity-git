/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import jetbrains.buildServer.buildTriggers.vcs.git.ServerPluginConfig;
import jetbrains.buildServer.configuration.ChangeListener;
import jetbrains.buildServer.configuration.FilesWatcher;
import jetbrains.buildServer.util.FileUtil;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.util.FS;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

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
  private final FilesWatcher myWatcher;
  private final ServerPluginConfig myConfig;

  public RefreshableSshConfigSessionFactory(@NotNull final ServerPluginConfig config, final boolean monitorDotSshChanges) {
    myConfig = config;
    if (monitorDotSshChanges) {
      final File sshDir = new File(System.getProperty("user.home"), ".ssh");
      myWatcher = new FilesWatcher(new FilesWatcher.WatchedFilesProvider() {
        public File[] getWatchedFiles() {
          return getAllFiles(sshDir);
        }
      });
      myWatcher.registerListener(new ChangeListener() {
        public void changeOccured(String requester) {
          expireDelegate();
        }
      });
      myWatcher.start();
    } else {
      myWatcher = null;
    }
  }

  public void stopMonitoringConfigs() {
    if (myWatcher != null) {
      myWatcher.stop();
    }
  }

  /**
   * @return the delegate session factory
   */
  @NotNull
  private SshSessionFactory delegate() {
    synchronized (delegateLock) {
      if (myDelegate == null) {
        LOG.info("Reloading SSH configuration information for Git plugin");
        myDelegate = new HeadlessSshSessionFactory(myConfig);
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
   * @param dir directory of interest
   * @return all files from specified dir recursively
   */
  private File[] getAllFiles(File dir) {
    List<File> files = new ArrayList<File>();
    FileUtil.collectMatchedFiles(dir, Pattern.compile(".*"), files);
    return files.toArray(new File[0]);
  }

  @Override
  public Session getSession(String user, String pass, String host, int port, CredentialsProvider credentialsProvider, FS fs)
    throws JSchException {
    return delegate().getSession(user, pass, host, port, credentialsProvider, fs);
  }
}
