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

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.DirectoryCleanersProviderContext;
import jetbrains.buildServer.agent.DirectoryCleanersRegistry;
import jetbrains.buildServer.buildTriggers.vcs.git.MirrorManager;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.AgentMirrorCleaner;
import jetbrains.buildServer.util.Dates;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.VcsRootEntry;
import org.jetbrains.annotations.NotNull;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static java.util.Arrays.asList;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static jetbrains.buildServer.util.Util.map;

@Test
public class AgentMirrorCleanerTest {
  private Mockery myContext = new Mockery();
  private MirrorManager myMirrorManager;
  private AgentMirrorCleaner myAgentMirrorCleaner;

  @BeforeMethod
  public void setUp() {
    myMirrorManager = myContext.mock(MirrorManager.class);
    myAgentMirrorCleaner = new AgentMirrorCleaner(myMirrorManager);
  }


  public void should_register_mirrors_not_used_in_current_build() {
    final DirectoryCleanersRegistry registry = myContext.mock(DirectoryCleanersRegistry.class);
    final File r3mirror = new File("r3");
    final File r4mirror = new File("r4");
    final Date r3lastAccess = Dates.makeDate(2012, 10, 29);
    final Date r4lastAccess = Dates.makeDate(2012, 10, 27);
    List<String> repositoriesInBuild = asList("git://some.org/r1", "git://some.org/r2");
    myContext.checking(new Expectations() {{
      one(myMirrorManager).getMappings(); will(returnValue(map("git://some.org/r1", new File("r1"),
                                                               "git://some.org/r2", new File("r2"),
                                                               "git://some.org/r3", r3mirror,
                                                               "git://some.org/r4", r4mirror)));
      one(myMirrorManager).getLastUsedTime(r3mirror); will(returnValue(r3lastAccess.getTime()));
      one(myMirrorManager).getLastUsedTime(r4mirror); will(returnValue(r4lastAccess.getTime()));

      one(registry).addCleaner(r3mirror, r3lastAccess);
      one(registry).addCleaner(r4mirror, r4lastAccess);
    }});
    myAgentMirrorCleaner.registerDirectoryCleaners(createCleanerContext(repositoriesInBuild), registry);
  }


  private DirectoryCleanersProviderContext createCleanerContext(@NotNull final List<String> repositoriesInBuild) {
    final DirectoryCleanersProviderContext context = myContext.mock(DirectoryCleanersProviderContext.class);
    final AgentRunningBuild build = myContext.mock(AgentRunningBuild.class);
    myContext.checking(new Expectations() {{
      allowing(context).getRunningBuild(); will(returnValue(build));
      allowing(build).getVcsRootEntries(); will(returnValue(createVcsRootEntries(repositoriesInBuild)));
    }});
    return context;
  }


  private List<VcsRootEntry> createVcsRootEntries(@NotNull List<String> repositories) {
    List<VcsRootEntry> entries = new ArrayList<VcsRootEntry>();
    for (String r : repositories) {
      entries.add(new VcsRootEntry(vcsRoot().withFetchUrl(r).build(), CheckoutRules.DEFAULT));
    }
    return entries;
  }

}
