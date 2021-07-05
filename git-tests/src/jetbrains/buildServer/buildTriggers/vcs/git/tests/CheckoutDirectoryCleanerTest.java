package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import com.intellij.execution.configurations.GeneralCommandLine;
import java.io.File;
import java.util.Collections;
import java.util.Date;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.SimpleCommandLineProcessRunner;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.DirectoryCleanersProviderContext;
import jetbrains.buildServer.agent.DirectoryCleanersRegistry;
import jetbrains.buildServer.agent.impl.BuildParametersMapImpl;
import jetbrains.buildServer.agent.impl.directories.DirectoryMap;
import jetbrains.buildServer.buildTriggers.vcs.git.CommandLineUtil;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.CheckoutDirectoryCleaner;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitDetector;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitDetectorImpl;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitPathResolverImpl;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.api.Invocation;
import org.jmock.lib.action.CustomAction;
import org.testng.SkipException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static jetbrains.buildServer.util.Util.map;

@Test
public class CheckoutDirectoryCleanerTest extends BaseTestCase {
  private Mockery myContext;
  private DirectoryMap myDirectoryMap;
  private GitDetector myGitDetector;
  private CheckoutDirectoryCleaner myCleaner;
  private String myGitPath;
  private File myRepo;
  private AgentRunningBuild myRunningBuild;
  private DirectoryCleanersProviderContext myProviderContext;
  private DirectoryCleanersRegistry myRegistry;

  @BeforeMethod
  public void setUp() throws Exception {
    myGitPath = System.getenv("TEAMCITY_GIT_PATH");
    myRepo = createTempDir();
    myContext = new Mockery();
    myDirectoryMap = myContext.mock(DirectoryMap.class);
    myGitDetector = new GitDetectorImpl(new GitPathResolverImpl());
    myRunningBuild = myContext.mock(AgentRunningBuild.class);
    myProviderContext = myContext.mock(DirectoryCleanersProviderContext.class);
    myRegistry = myContext.mock(DirectoryCleanersRegistry.class);

    myCleaner = new CheckoutDirectoryCleaner(myDirectoryMap, myGitDetector) {
      protected void runGitCommand(@NotNull File repo, @NotNull String pathToGit, @NotNull String cmdName, int timeout, @NotNull String... params) {
        assertEquals("git", pathToGit);
        assertEquals(4, params.length);
        assertEquals("clean", params[0]);
        super.runGitCommand(repo, pathToGit, cmdName, timeout, params);
      }
    };
  }

  public void test_clean_enabled() throws Exception {
    if (myGitPath == null) throw new SkipException("No git executable detected");

    prepareRepo(true);

    final Date date = new Date();
    myContext.checking(new Expectations() {{
      allowing(myProviderContext).getRunningBuild(); will(returnValue(myRunningBuild));
      allowing(myRunningBuild).getSharedBuildParameters(); will(returnValue(new BuildParametersMapImpl(Collections.emptyMap())));
      one(myDirectoryMap).getRegisteredRemovableItems(myRunningBuild);will(returnValue(map(myRepo, date)));
      one(myRegistry).addCleaner(with(myRepo), with(date), with(any(Runnable.class))); will(new CustomAction("run cleaner") {
        @Override
        public Object invoke(Invocation invocation) {
          ((Runnable)invocation.getParameter(2)).run();
          return null;
        }
      });
    }});

    myCleaner.registerDirectoryCleaners(myProviderContext, myRegistry);

    assertTrue(new File(myRepo, "vcs_file").isFile());
    assertFalse(new File(myRepo, "non_vcs_file").exists());
  }

  public void test_clean_disabled() throws Exception {
    if (myGitPath == null) throw new SkipException("No git executable detected");

    prepareRepo(false);

    final Date date = new Date();
    myContext.checking(new Expectations() {{
      allowing(myProviderContext).getRunningBuild(); will(returnValue(myRunningBuild));
      allowing(myRunningBuild).getSharedBuildParameters(); will(returnValue(new BuildParametersMapImpl(Collections.emptyMap())));
      one(myDirectoryMap).getRegisteredRemovableItems(myRunningBuild);will(returnValue(map(myRepo, date)));
    }});

    myCleaner.registerDirectoryCleaners(myProviderContext, myRegistry);

    assertTrue(new File(myRepo, "non_vcs_file").isFile());
    assertTrue(new File(myRepo, "vcs_file").isFile());
  }

  private void prepareRepo(boolean enableCleanup) throws Exception {
    runCommand("init");

    final File vcs_file = new File(myRepo, "vcs_file");
    final File non_vcs_file = new File(myRepo, "non_vcs_file");
    FileUtil.writeFile(vcs_file, "expected to be preserved", "UTF-8");
    FileUtil.writeFile(non_vcs_file, "non-vcs file", "UTF-8");

    runCommand("add", "vcs_file");
    runCommand("commit", "-m", "initial commit");
    if (enableCleanup) {
      runCommand("config", "--local", "teamcity.freeDiskSpaceCleanupEnabled", "true");
    }

    assertTrue(non_vcs_file.isFile());
    assertTrue(vcs_file.isFile());
  }

  private void runCommand(@NotNull String name, String... params) throws VcsException {
    final GeneralCommandLine cl = new GeneralCommandLine();
    cl.setExePath(myGitPath);
    cl.setWorkDirectory(myRepo.getAbsolutePath());
    cl.addParameter(name);
    cl.addParameters(params);

    final ExecResult result = SimpleCommandLineProcessRunner.runCommand(cl, null);
    final VcsException commandError = CommandLineUtil.getCommandLineError(name, result);
    if (commandError != null) throw commandError;
  }
}
