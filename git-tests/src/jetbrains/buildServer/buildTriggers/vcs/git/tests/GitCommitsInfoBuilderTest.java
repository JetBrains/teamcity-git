package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import jetbrains.buildServer.BaseTestCase;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.buildTriggers.vcs.git.GitCommitsInfoBuilder;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.buildTriggers.vcs.git.GitVcsSupport;
import jetbrains.buildServer.serverSide.ServerPaths;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.vcs.*;
import jetbrains.vcs.api.CommitInfo;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitSupportBuilder.gitSupport;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.GitTestUtil.dataFile;
import static jetbrains.buildServer.buildTriggers.vcs.git.tests.VcsRootBuilder.vcsRoot;

@Test
public class GitCommitsInfoBuilderTest extends BaseTestCase {

  private TempFiles myTempFiles = new TempFiles();
  private File myRepositoryDir;
  private ServerPaths myServerPaths;

  @BeforeMethod
  public void setUp() throws IOException {
    myServerPaths = new ServerPaths(myTempFiles.createTempDir().getAbsolutePath());
    File masterRep = dataFile("repo.git");
    myRepositoryDir = myTempFiles.createTempDir();
    FileUtil.copyDir(masterRep, myRepositoryDir);
  }

  @AfterMethod
  public void tearDown() {
    myTempFiles.cleanup();
  }


  public void test() throws VcsException {
    VcsRoot root = vcsRoot().withFetchUrl(GitUtils.toURL(myRepositoryDir)).withBranch("master").build();
    GitVcsSupport vcs = gitSupport().withServerPaths(myServerPaths).build();
    final List<CommitInfo> commits = new ArrayList<CommitInfo>();

    new GitCommitsInfoBuilder(vcs).collectCommits(root,CheckoutRules.DEFAULT, new CommitsInfoBuilder.CommitsConsumer() {
      public void consumeCommit(@NotNull CommitInfo commit) {
        commits.add(commit);
      }
    });
    List<String> allCommits = asList(
      "ea5e05051fbfaa7d8da97586807b009cbfebae9d",
      "27de3d118ca320d3a8a08320ff05aa0567996590",
      "39679cc440c83671fbf6ad8083d92517f9602300",
      "9395143dd6c3e73abf9281be7a772c4d286e72a5",
      "039e7395725ad8d2a143fd44645a3fb72b001217",
      "777f79b3e89e63ac954fe0881470be3c72b8b0d4",
      "f61e30ce576e76bff877ddf1d00acf22c5c1b07a",
      "3df61e6f11a5a9b919cb3f786a83fdd09f058617",
      "465ad9f630e451b9f2b782ffb09804c6a98c4bb9",
      "5711cbfe566b6c92e331f95d4b236483f4532eed",
      "6cf3cb6a87091d17466607858c699c35edf30d3b",
      "2c7e90053e0f7a5dd25ea2a16ef8909ba71826f6",
      "2494559261ab85e92b1780860b34f876b5e6bce6",
      "e7f491963fbc5c96a27e4169b97746a5a7f83870",
      "1391281d33a83a7205f2f05d3eb64c349c636e87",
      "3b9fbfbb43e7edfad018b482e15e7f93cca4e69f",
      "e6b15b1f4741199857e2fa744eaadfe5a9d9aede",
      "feac610f381e697acf4c1c8ad82b7d76c7643b04",
      "92112555d9eb3e433eaa91fe32ec001ae8fe3c52",
      "778cc3d0105ca1b6b2587804ebfe89c2557a7e46",
      "f5bdd3819df0358a43d9a8f94eaf96bb306e19fe",
      "78cbbed3561de3417467ee819b1795ba14c03dfb",
      "233daeefb335b60c7b5700afde97f745d86cb40d",
      "6eae9acd29db2dba146929634a4bb1e6e72a31fd",
      "ce6044093939bb47283439d97a1c80f759669ff5",
      "2eed4ae6732536f76a65136a606f635e8ada63b9",
      "cbf1073bd3f938e7d7d85718dbc6c3bee10360d9",
      "d1a88fd33c516c1b607db75eb62244b2ea495c42",
      "37c371a6db0acefc77e3be99d16a44413e746591",
      "b5d65401a4e8a09b80b8d73ca4392f1913e99ff5",
      "592c5bcee6d906482177a62a6a44efa0cff9bbc7",
      "1837cf38309496165054af8bf7d62a9fe8997202",
      "049a98762a29677da352405b27b3d910cb94eb3b",
      "7e916b0edd394d0fca76456af89f4ff7f7f65049",
      "0dd03338d20d2e8068fbac9f24899d45d443df38",
      "70dbcf426232f7a33c7e5ebdfbfb26fc8c467a46",
      "a894d7d58ffde625019a9ecf8267f5f1d1e5c341",
      "f3f826ce85d6dad25156b2d7550cedeb1a422f4c",
      "ee886e4adb70fbe3bdc6f3f6393598b3f02e8009",
      "6fce8fe45550eb72796704a919dad68dc44be44a",
      "ad4528ed5c84092fdbe9e0502163cf8d6e6141e7",
      "97442a720324a0bd092fb9235f72246dc8b345bc",
      "2276eaf76a658f96b5cf3eb25f3e1fda90f6b653"
    );

    List<String> reported = new ArrayList<String>();
    for (CommitInfo c : commits) {
      reported.add(c.getVersion());

    }
    assertTrue(reported.containsAll(allCommits));
  }
}
