

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.agent.AgentLifeCycleListener;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.DirectoryCleanersProviderContext;
import jetbrains.buildServer.agent.DirectoryCleanersRegistry;
import jetbrains.buildServer.agent.oauth.AgentTokenRetriever;
import jetbrains.buildServer.agent.oauth.AgentTokenStorage;
import jetbrains.buildServer.agent.oauth.InvalidAccessToken;
import jetbrains.buildServer.buildTriggers.vcs.git.MirrorManager;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.AgentMirrorCleaner;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.SubmoduleManagerImpl;
import jetbrains.buildServer.connections.ExpiringAccessToken;
import jetbrains.buildServer.util.Dates;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.VcsRootEntry;
import org.jetbrains.annotations.NotNull;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static java.util.Arrays.asList;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;
import static jetbrains.buildServer.util.Util.map;

@Test
public class AgentMirrorCleanerTest {
  private Mockery myContext;
  private MirrorManager myMirrorManager;
  private AgentMirrorCleaner myAgentMirrorCleaner;
  private SubmoduleManagerImpl mySubmoduleManager;

  @BeforeMethod
  public void setUp() {
    myContext = new Mockery();
    myMirrorManager = myContext.mock(MirrorManager.class);
    mySubmoduleManager = new SubmoduleManagerImpl(myMirrorManager);
    AgentTokenRetriever tokenRetriever = new AgentTokenRetriever() {
      @NotNull
      @Override
      public ExpiringAccessToken retrieveToken(@NotNull String tokenId) {
        return new InvalidAccessToken();
      }
    };
    AgentTokenStorage tokenStorage = new AgentTokenStorage(EventDispatcher.create(AgentLifeCycleListener.class), tokenRetriever);
    myAgentMirrorCleaner = new AgentMirrorCleaner(myMirrorManager, mySubmoduleManager, tokenStorage);
  }

  public void should_register_mirrors_not_used_in_current_build() throws IOException {
    TempFiles tmpFiles = new TempFiles();
    try {
      final DirectoryCleanersRegistry registry = myContext.mock(DirectoryCleanersRegistry.class);
      final File baseMirrorsDir = tmpFiles.createTempDir();
      final File r1mirror = createMirror(baseMirrorsDir);
      final File r2mirror = createMirror(baseMirrorsDir);
      final File r3mirror = createMirror(baseMirrorsDir);
      final File r4mirror = createMirror(baseMirrorsDir);
      final Date r3lastAccess = Dates.makeDate(2012, 10, 29);
      final Date r4lastAccess = Dates.makeDate(2012, 10, 27);
      List<String> repositoriesInBuild = asList("git://some.org/r1", "git://some.org/r2");
      myContext.checking(new Expectations() {{
        one(myMirrorManager).getMappings();
        will(returnValue(map("git://some.org/r1", r1mirror,
                             "git://some.org/r2", r2mirror,
                             "git://some.org/r3", r3mirror,
                             "git://some.org/r4", r4mirror)));
        one(myMirrorManager).getBaseMirrorsDir(); will(returnValue(baseMirrorsDir));

        one(myMirrorManager).isInvalidDirName(r1mirror.getName()); will(returnValue(false));
        one(myMirrorManager).isInvalidDirName(r2mirror.getName()); will(returnValue(false));
        one(myMirrorManager).isInvalidDirName(r3mirror.getName()); will(returnValue(false));
        one(myMirrorManager).isInvalidDirName(r4mirror.getName()); will(returnValue(false));

        one(myMirrorManager).getUrl(r1mirror.getName()); will(returnValue("git://some.org/r1"));
        one(myMirrorManager).getUrl(r2mirror.getName()); will(returnValue("git://some.org/r2"));
        one(myMirrorManager).getUrl(r3mirror.getName()); will(returnValue("git://some.org/r3"));
        one(myMirrorManager).getUrl(r4mirror.getName()); will(returnValue("git://some.org/r4"));

        one(myMirrorManager).getLastUsedTime(r3mirror); will(returnValue(r3lastAccess.getTime()));
        one(myMirrorManager).getLastUsedTime(r4mirror); will(returnValue(r4lastAccess.getTime()));
        //one(myMirrorManager).getLastUsedTime(r1mirror); will(returnValue(1234567L));
        //one(myMirrorManager).getLastUsedTime(r2mirror); will(returnValue(1234567L));

        allowing(myMirrorManager).getMirrorDir("git://some.org/r1"); will(returnValue(r1mirror));
        allowing(myMirrorManager).getMirrorDir("git://some.org/r2"); will(returnValue(r2mirror));
        allowing(myMirrorManager).getMirrorDir("git://some.org/r3"); will(returnValue(r3mirror));
        allowing(myMirrorManager).getMirrorDir("git://some.org/r4"); will(returnValue(r4mirror));

        one(registry).addCleaner(with(r3mirror), with(r3lastAccess), with(any(Runnable.class)));
        one(registry).addCleaner(with(r4mirror), with(r4lastAccess), with(any(Runnable.class)));
      }});
      myAgentMirrorCleaner.registerDirectoryCleaners(createCleanerContext(repositoriesInBuild), registry);
    } finally {
      tmpFiles.cleanup();
    }
  }

  public void should_resolve_submodules() throws IOException {
    TempFiles tmpFiles = new TempFiles();
    try {
      final DirectoryCleanersRegistry registry = myContext.mock(DirectoryCleanersRegistry.class);
      final File baseMirrorsDir = tmpFiles.createTempDir();
      final File r1mirror = createMirror(baseMirrorsDir);
      final File r2mirror = createMirror(baseMirrorsDir);
      final File r3mirror = createMirror(baseMirrorsDir);
      final File r4mirror = createMirror(baseMirrorsDir);
      final Date r1lastAccess = Dates.makeDate(2012, 10, 26);
      final Date r2lastAccess = Dates.makeDate(2012, 10, 28);
      final Date r3lastAccess = Dates.makeDate(2012, 10, 29);
      final Date r4lastAccess = Dates.makeDate(2012, 10, 27);
      List<String> repositoriesInBuild = Collections.singletonList("git://some.org/r1");
      myContext.checking(new Expectations() {{
        one(myMirrorManager).getMappings();
        will(returnValue(map("git://some.org/r1", r1mirror,
                             "git://some.org/r2", r2mirror,
                             "git://some.org/r3", r3mirror,
                             "git://some.org/r4", r4mirror)));
        one(myMirrorManager).getBaseMirrorsDir(); will(returnValue(baseMirrorsDir));

        one(myMirrorManager).isInvalidDirName(r1mirror.getName()); will(returnValue(false));
        one(myMirrorManager).isInvalidDirName(r2mirror.getName()); will(returnValue(false));
        one(myMirrorManager).isInvalidDirName(r3mirror.getName()); will(returnValue(false));
        one(myMirrorManager).isInvalidDirName(r4mirror.getName()); will(returnValue(false));

        one(myMirrorManager).getUrl(r1mirror.getName()); will(returnValue("git://some.org/r1"));
        one(myMirrorManager).getUrl(r2mirror.getName()); will(returnValue("git://some.org/r2"));
        one(myMirrorManager).getUrl(r3mirror.getName()); will(returnValue("git://some.org/r3"));
        one(myMirrorManager).getUrl(r4mirror.getName()); will(returnValue("git://some.org/r4"));

        one(myMirrorManager).getLastUsedTime(r1mirror); will(returnValue(r1lastAccess.getTime()));
        one(myMirrorManager).getLastUsedTime(r2mirror); will(returnValue(r2lastAccess.getTime()));
        one(myMirrorManager).getLastUsedTime(r3mirror); will(returnValue(r3lastAccess.getTime()));
        one(myMirrorManager).getLastUsedTime(r4mirror); will(returnValue(r4lastAccess.getTime()));

        allowing(myMirrorManager).getMirrorDir("git://some.org/r1"); will(returnValue(r1mirror));
        allowing(myMirrorManager).getMirrorDir("git://some.org/r2"); will(returnValue(r2mirror));
        allowing(myMirrorManager).getMirrorDir("git://some.org/r3"); will(returnValue(r3mirror));
        allowing(myMirrorManager).getMirrorDir("git://some.org/r4"); will(returnValue(r4mirror));
        allowing(myMirrorManager).getLastUsedTime(with(any(File.class))); will(returnValue(1234567L));
      }});
      mySubmoduleManager.persistSubmodules("git://some.org/r1", asList("git://some.org/r2", "git://some.org/r3"));
      mySubmoduleManager.persistSubmodules("git://some.org/r2", Collections.singletonList("git://some.org/r4"));
      myAgentMirrorCleaner.registerDirectoryCleaners(createCleanerContext(repositoriesInBuild), registry);
    } finally {
      tmpFiles.cleanup();
    }
  }

  public void should_delete_invalid_mirror() throws IOException{
    final TempFiles tmpFiles = new TempFiles();
    try {
      final DirectoryCleanersRegistry registry = myContext.mock(DirectoryCleanersRegistry.class);
      final File baseMirrorsDir = tmpFiles.createTempDir();
      final File invalidMirror = createMirror(baseMirrorsDir);
      Assert.assertTrue(invalidMirror.isDirectory());
      List<String> repositoriesInBuild = Collections.singletonList("git://some.org/r1");
      myContext.checking(new Expectations() {{
        one(myMirrorManager).getMappings(); will(returnValue(map("git://some.org/r1", invalidMirror)));
        allowing(myMirrorManager).getLastUsedTime(with(any(File.class))); will(returnValue(1234567L));
        one(myMirrorManager).getBaseMirrorsDir(); will(returnValue(baseMirrorsDir));
        one(myMirrorManager).isInvalidDirName(invalidMirror.getName()); will(returnValue(true));
        allowing(myMirrorManager).getMirrorDir("git://some.org/r1"); will(returnValue(invalidMirror));
        one(registry).addCleaner(with(invalidMirror), with(any(Date.class)), with(any(Runnable.class)));}});
      myAgentMirrorCleaner.registerDirectoryCleaners(createCleanerContext(repositoriesInBuild), registry);
    } finally {
      tmpFiles.cleanup();
    }
  }

  private DirectoryCleanersProviderContext createCleanerContext(@NotNull final List<String> repositoriesInBuild) {
    final DirectoryCleanersProviderContext context = myContext.mock(DirectoryCleanersProviderContext.class);
    final AgentRunningBuild build = myContext.mock(AgentRunningBuild.class);
    myContext.checking(new Expectations() {{
      allowing(context).getRunningBuild(); will(returnValue(build));
      allowing(build).getSharedConfigParameters(); will(returnValue(Collections.emptyMap()));
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

  @NotNull
  private File createMirror(final File baseMirrorsDir) throws IOException {
    return FileUtil.createTempDirectory("mirror", "", baseMirrorsDir);
  }
}