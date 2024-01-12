

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.buildTriggers.vcs.git.GitUtils;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import static com.intellij.openapi.util.io.FileUtil.copyDir;


public class GitTestUtil {

  private GitTestUtil() {}

  /**
   * Test data file
   *
   * @param path the file path relatively to data directory
   * @return the IO file object (the file is absolute)
   */
  public static File dataFile(String... path) {
    File f = new File("git-tests", "data");
    for (String p : path) {
      f = new File(f, p);
    }
    return f.getAbsoluteFile();
  }

  public static VcsRoot getVcsRoot() {
    return getVcsRoot("repo.git");
  }

  public static VcsRoot getVcsRoot(String repositoryName) {
    return getVcsRoot(dataFile(repositoryName));
  }


  public static VcsRootImpl getVcsRoot(File repositoryDir) {
    return new VcsRootBuilder()
      .withFetchUrl(GitUtils.toURL(repositoryDir))
      .withBranch("master")
      .build();
  }

  public static void copyRepository(@NotNull File srcDir, @NotNull File destDir) throws IOException {
    copyDir(srcDir, destDir);
    File refs = new File(destDir, "refs");
    if (!refs.isDirectory()) {
      refs.mkdirs();
    }
  }

  @NotNull
  static File copyRepository(@NotNull TempFiles tempFiles, @NotNull File srcDir, @NotNull String dstName) throws IOException {
    File parentDir = tempFiles.createTempDir();
    File result = new File(parentDir, dstName);
    copyRepository(srcDir, result);
    return result;
  }


  public static Properties copyCurrentProperties() {
    Properties result = new Properties();
    result.putAll(System.getProperties());
    return result;
  }


  public static void restoreProperties(@NotNull Properties properties) {
    Set<Object> currentKeys = new HashSet<Object>(System.getProperties().keySet());
    for (Object key : currentKeys) {
      System.clearProperty((String)key);
    }
    System.setProperties(properties);
  }
}