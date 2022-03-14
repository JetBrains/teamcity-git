package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import java.util.Collections;
import java.util.Map;
import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.buildTriggers.vcs.git.command.impl.RefImpl;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.vcs.VcsException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.jetbrains.annotations.NotNull;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test
public class NativeGitTestConnectionRunnerTest extends BaseTestCase {
  private PluginConfigBuilder myConfig;
  private TempFiles myTempFiles;
  private Mockery myMockery;


  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myTempFiles = new TempFiles();
    myConfig = new PluginConfigBuilder(new ServerPaths(myTempFiles.createTempDir().getAbsolutePath()));
    myMockery = new Mockery();
  }

  @Test
  public void testJGitFailsUnexpectidly() {
    final GitRepoOperations operations = myMockery.mock(GitRepoOperations.class);
    myMockery.checking(new Expectations() {{
      one(operations).lsRemoteCommand(false);
      will(returnValue(failingUnexpectidlyLsRemoteCommand()));
    }});
    final GitSupportBuilder builder = new GitSupportBuilder().withPluginConfig(myConfig).withGitRepoOperations(operations);
    assertNull(new CachingNativeGitTestConnectionRunner(builder.build()).testConnection(new VcsRootBuilder().withFetchUrl("some/url").build()));
  }

  @Test
  public void testJGitFails() {
    final GitRepoOperations operations = myMockery.mock(GitRepoOperations.class);
    myMockery.checking(new Expectations() {{
      one(operations).lsRemoteCommand(false);
      will(returnValue(failingLsRemoteCommand()));
    }});
    final GitSupportBuilder builder = new GitSupportBuilder().withPluginConfig(myConfig).withGitRepoOperations(operations);
    assertNull(new CachingNativeGitTestConnectionRunner(builder.build()).testConnection(new VcsRootBuilder().withFetchUrl("some/url").build()));
  }

  @Test
  public void testConnectionFailure() {
    final GitRepoOperations operations = myMockery.mock(GitRepoOperations.class);
    myMockery.checking(new Expectations() {{
      one(operations).lsRemoteCommand(false);
      will(returnValue(succeedingLsRemoteCommand()));
      one(operations).lsRemoteCommand(true);
      will(returnValue(failingLsRemoteCommand()));
    }});
    final GitSupportBuilder builder = new GitSupportBuilder().withPluginConfig(myConfig).withGitRepoOperations(operations);
    assertContains(new CachingNativeGitTestConnectionRunner(builder.build()).testConnection(new VcsRootBuilder().withFetchUrl("some/url").build()), "Test connection fails");
  }

  @Test
  public void testUnexpectedException() {
    final GitRepoOperations operations = myMockery.mock(GitRepoOperations.class);
    myMockery.checking(new Expectations() {{
      one(operations).lsRemoteCommand(false);
      will(returnValue(succeedingLsRemoteCommand()));
      one(operations).lsRemoteCommand(true);
      will(returnValue(failingUnexpectidlyLsRemoteCommand()));
    }});
    final GitSupportBuilder builder = new GitSupportBuilder().withPluginConfig(myConfig).withGitRepoOperations(operations);
    assertContains(new CachingNativeGitTestConnectionRunner(builder.build()).testConnection(new VcsRootBuilder().withFetchUrl("some/url").build()), "Unexpected exception");
  }

  @Test
  public void testConnectionSuccess() {
    final GitRepoOperations operations = myMockery.mock(GitRepoOperations.class);
    myMockery.checking(new Expectations() {{
      one(operations).lsRemoteCommand(false);
      will(returnValue(succeedingLsRemoteCommand()));
      one(operations).lsRemoteCommand(true);
      will(returnValue(succeedingLsRemoteCommand()));
    }});
    final GitSupportBuilder builder = new GitSupportBuilder().withPluginConfig(myConfig).withGitRepoOperations(operations);
    assertNull(new CachingNativeGitTestConnectionRunner(builder.build()).testConnection(new VcsRootBuilder().withFetchUrl("some/url").build()));
  }

  @NotNull
  private LsRemoteCommand succeedingLsRemoteCommand() {
    return new LsRemoteCommand() {
      @NotNull
      @Override
      public Map<String, Ref> lsRemote(@NotNull Repository db, @NotNull GitVcsRoot gitRoot, @NotNull FetchSettings settings) throws VcsException {
        return Collections.singletonMap("master", new RefImpl("master", "aae11faf2e9f1695cb872b1db2bc45ce4131eece"));
      }
    };
  }

  @NotNull
  private LsRemoteCommand failingLsRemoteCommand() {
    return new LsRemoteCommand() {
      @NotNull
      @Override
      public Map<String, Ref> lsRemote(@NotNull Repository db, @NotNull GitVcsRoot gitRoot, @NotNull FetchSettings settings) throws VcsException {
        throw new VcsException("Test connection fails");
      }
    };
  }

  @NotNull
  private LsRemoteCommand failingUnexpectidlyLsRemoteCommand() {
    return new LsRemoteCommand() {
      @NotNull
      @Override
      public Map<String, Ref> lsRemote(@NotNull Repository db, @NotNull GitVcsRoot gitRoot, @NotNull FetchSettings settings) throws VcsException {
        throw new RuntimeException("Unexpected exception");
      }
    };
  }
}
