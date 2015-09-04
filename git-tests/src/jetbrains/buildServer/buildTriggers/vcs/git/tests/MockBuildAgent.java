/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import jetbrains.buildServer.XmlRpcHandlerManager;
import jetbrains.buildServer.agent.AgentBuildRunner;
import jetbrains.buildServer.agent.BuildAgent;
import jetbrains.buildServer.agent.BuildAgentConfiguration;
import jetbrains.buildServer.agentServer.AgentBuild;
import jetbrains.buildServer.agentServer.AgentBuildResult;
import jetbrains.buildServer.agentServer.Server;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Date;
import java.util.Vector;

public class MockBuildAgent implements BuildAgent {

  public XmlRpcHandlerManager getXmlRpcHandlerManager() {
    return new XmlRpcHandlerManager() {
      public void addHandler(final String handlerName, final Object handler) {
      }
      public void addSessionHandler(final String handlerName, final Object handler) {
      }
    };
  }

  public void start() {
    throw new UnsupportedOperationException();
  }

  public boolean shutdown() {
    throw new UnsupportedOperationException();
  }

  public boolean shutdownWaitForBuild() {
    throw new UnsupportedOperationException();
  }

  public Integer getId() {
    throw new UnsupportedOperationException();
  }

  @Nullable
  public String getBuildId() {
    throw new UnsupportedOperationException();
  }

  @Nullable
  public String getAccessCode() {
    throw new UnsupportedOperationException();
  }

  public void ensureIdle() {
    throw new UnsupportedOperationException();
  }

  public void ensureNoPendingCommands() {
    throw new UnsupportedOperationException();
  }

  public void init(final String[] args) {
    throw new UnsupportedOperationException();
  }

  public Collection<AgentBuildRunner> getRunners() {
    throw new UnsupportedOperationException();
  }

  @Nullable
  public Server getServerProxy() {
    throw new UnsupportedOperationException();
  }

  public BuildAgentConfiguration getConfiguration() {
    throw new UnsupportedOperationException();
  }

  public boolean registerOnBuildServer(final String buildId) {
    throw new UnsupportedOperationException();
  }

  public void unregisterFromBuildServer() {
    throw new UnsupportedOperationException();
  }

  public boolean isRunning() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  public AgentBuildResult runBuild(final AgentBuild agentBuild) {
    throw new UnsupportedOperationException();
  }

  public String runBuild(final String serializedAgentBuild) {
    throw new UnsupportedOperationException();
  }

  public boolean stopBuild() {
    throw new UnsupportedOperationException();
  }

  public boolean upgrade(final Vector pluginNames) {
    throw new UnsupportedOperationException();
  }

  public String ping2() {
    throw new UnsupportedOperationException();
  }

  public boolean log(final String buildId, final Vector messagesXml) {
    throw new UnsupportedOperationException();
  }

  public boolean buildFinished(final String buildId, final Date finishDate, final boolean buildFailed) {
    throw new UnsupportedOperationException();
  }

  public boolean buildInterrupted(final String buildId) {
    throw new UnsupportedOperationException();
  }

  public boolean isBuildFailing(final String buildId) throws InterruptedException {
    throw new UnsupportedOperationException();
  }

  public boolean markCheckoutDirDirty(final String buildId) {
    throw new UnsupportedOperationException();
  }

  public boolean markCheckoutDirClean(final String buildId) {
    throw new UnsupportedOperationException();
  }
}
