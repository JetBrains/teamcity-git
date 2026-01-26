package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.oauth.AgentTokenStorage;
import jetbrains.buildServer.buildTriggers.vcs.git.*;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.AgentGitVcsRoot;
import jetbrains.buildServer.buildTriggers.vcs.git.agent.GitAgentVcsSupport;
import jetbrains.buildServer.buildTriggers.vcs.git.tests.builders.AgentRunningBuildBuilder;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.serverSide.oauth.TokenRefresher;
import jetbrains.buildServer.vcs.CheckoutRules;
import jetbrains.buildServer.vcs.MergeOptions;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.mockito.Mockito;
import org.testcontainers.shaded.com.google.common.io.Files;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.assertj.core.api.BDDAssertions.then;

public class FileBasedUrlTest extends BaseRemoteRepositoryTest {
  private GitVcsSupport myGit;
  private VcsRoot myRoot;
  private String myPreviousPropertyValue;

  private final String ERROR_MSG = "VCS root 'file:///tmp/repo.git' is using local file fetch URL 'file:///tmp/repo.git', which is forbidden for security reasons. " +
                                  "Please configure remote repository URLs to use network protocols like SSH or HTTPS.";

  @BeforeClass
  public void setUp() throws Exception {
    super.setUp();
    ServerPaths paths = new ServerPaths(myTempFiles.createTempDir().getAbsolutePath());
    myGit = GitSupportBuilder.gitSupport().withServerPaths(paths).build();
    myRoot = VcsRootBuilder.vcsRoot().withFetchUrl("file:///tmp/repo.git").build();
    myPreviousPropertyValue = System.getProperty(Constants.ALLOW_FILE_URL);
    System.setProperty(Constants.ALLOW_FILE_URL, "false");
  }

  @AfterClass
  public void tearDown() {
    super.tearDown();
    System.setProperty(Constants.ALLOW_FILE_URL, myPreviousPropertyValue);
  }

  @Test
  public void creation_of_GitVcsRoots_with_file_url_throws() {
    List<Callable> ctors = new ArrayList<Callable>() {{
      add(() -> new SGitVcsRoot(Mockito.mock(MirrorManager.class), myRoot, Mockito.mock(URIishHelper.class), Mockito.mock(TokenRefresher.class)));
    }};

    for (Callable ctor : ctors) {
      try {
        ctor.call();
        failBecauseExceptionWasNotThrown(VcsException.class);
      } catch (VcsException e) {
        then(e.getMessage()).contains(ERROR_MSG);
      } catch (Exception e) {
        fail("Must have thrown VcsException, but got: " + e.getClass().getName() + "," + e.getMessage());
      }
    }
  }

  @Test
  public void testConnection_fails_with_file_url() {
    try {
      myGit.testConnection(myRoot);
      failBecauseExceptionWasNotThrown(VcsException.class);
    } catch (VcsException e) {
      then(e.getMessage()).contains(ERROR_MSG);
    }
  }

  @Test
  public void getCurrentState_fails_with_file_url() {
    try {
      myGit.getCurrentState(myRoot);
      failBecauseExceptionWasNotThrown(VcsException.class);
    } catch (VcsException e) {
      then(e.getMessage()).contains(ERROR_MSG);
    }
  }

  @Test
  public void labellingSupport_fails_with_file_url() {
    try {
      myGit.getLabelingSupport().label("123", "123", myRoot, CheckoutRules.DEFAULT);
      failBecauseExceptionWasNotThrown(VcsException.class);
    } catch (VcsException e) {
      then(e.getMessage()).contains(ERROR_MSG);
    }
  }

  @Test
  public void commitSupport_fails_with_file_url() {
    GitCommitSupport commitSupport = new GitCommitSupport(myGit, Mockito.mock(CommitLoader.class), Mockito.mock(RepositoryManager.class), Mockito.mock(GitRepoOperations.class));
    try {
      commitSupport.getCommitPatchBuilder(myRoot);
      failBecauseExceptionWasNotThrown(VcsException.class);
    } catch (VcsException e) {
      then(e.getMessage()).contains(ERROR_MSG);
    }
  }

  @Test
  public void mergeSupport_fails_with_file_url() {
    GitMergeSupport mergeSupport = new GitMergeSupport(myGit, Mockito.mock(CommitLoader.class), Mockito.mock(RepositoryManager.class), Mockito.mock(ServerPluginConfig.class), Mockito.mock(GitRepoOperations.class));
    try {
      mergeSupport.merge(myRoot, "123", "123", "123", Mockito.mock(MergeOptions.class));
      failBecauseExceptionWasNotThrown(VcsException.class);
    } catch (VcsException e) {
      then(e.getMessage()).contains(ERROR_MSG);
    }
  }
}
