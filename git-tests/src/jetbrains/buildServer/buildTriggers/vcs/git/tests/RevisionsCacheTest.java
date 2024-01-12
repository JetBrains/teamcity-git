

package jetbrains.buildServer.buildTriggers.vcs.git.tests;

import jetbrains.buildServer.buildTriggers.vcs.git.RepositoryRevisionCache;
import jetbrains.buildServer.buildTriggers.vcs.git.RevisionCacheType;
import jetbrains.buildServer.buildTriggers.vcs.git.RevisionsCache;
import jetbrains.buildServer.buildTriggers.vcs.git.ServerPluginConfig;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.TestFor;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.function.BiConsumer;

import static jetbrains.buildServer.buildTriggers.vcs.git.tests.PluginConfigBuilder.pluginConfig;
import static jetbrains.buildServer.util.CollectionsUtil.setOf;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.BDDAssertions.then;

@Test
public class RevisionsCacheTest extends BaseRemoteRepositoryTest {

  private PluginConfigBuilder myConfigBuilder;
  private ServerPluginConfig myConfig;
  private RevisionsCache myCache;

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myConfigBuilder = pluginConfig().setTempFiles(myTempFiles);
    myConfig = myConfigBuilder.build();
    myCache = new RevisionsCache(myConfig);
  }


  public void get_repo_cache_by_dir() throws Exception {
    RepositoryRevisionCache repoCache1 = myCache.getRepositoryCache(repository("1"), RevisionCacheType.COMMIT_CACHE);
    RepositoryRevisionCache repoCache2 = myCache.getRepositoryCache(repository("2"), RevisionCacheType.COMMIT_CACHE);

    then(repoCache1).isNotNull();
    then(repoCache2).isNotNull();
    then(repoCache1).isNotSameAs(repoCache2);
    then(repoCache1).isSameAs(myCache.getRepositoryCache(repository("1"), RevisionCacheType.COMMIT_CACHE));
  }


  public void save_revision_entry() throws Exception {
    File repo = repository("1");
    RepositoryRevisionCache commitCache = myCache.getRepositoryCache(repo, RevisionCacheType.COMMIT_CACHE);
    commitCache.saveRevision("v1", false, commitCache.getResetCounter());
    commitCache.saveRevision("v2", true, commitCache.getResetCounter());
    commitCache.saveRevision("v3", false, commitCache.getResetCounter());

    then(commitCache.hasRevision("v1")).isFalse();
    then(commitCache.hasRevision("v2")).isTrue();
    then(commitCache.hasRevision("v3")).isFalse();
    then(commitCache).isEqualTo(new RevisionsCache(myConfig).getRepositoryCache(repo, RevisionCacheType.COMMIT_CACHE));
  }


  public void save_revision_entry_different_types() throws Exception {
    File repo = repository("1");
    RepositoryRevisionCache commitCache = myCache.getRepositoryCache(repo, RevisionCacheType.COMMIT_CACHE);
    RepositoryRevisionCache hintCache = myCache.getRepositoryCache(repo, RevisionCacheType.HINT_CACHE);
    commitCache.saveRevision("v1", false, commitCache.getResetCounter());
    hintCache.saveRevision("v2", true, hintCache.getResetCounter());

    then(commitCache.hasRevision("v1")).isFalse();
    then(commitCache.hasRevision("v2")).isNull();
    then(hintCache.hasRevision("v1")).isNull();
    then(hintCache.hasRevision("v2")).isTrue();
    then(commitCache).isEqualTo(new RevisionsCache(myConfig).getRepositoryCache(repo, RevisionCacheType.COMMIT_CACHE));
    then(hintCache).isEqualTo(new RevisionsCache(myConfig).getRepositoryCache(repo, RevisionCacheType.HINT_CACHE));
  }


  public void reset_negative_entries() throws Exception {
    File repo = repository("1");
    RepositoryRevisionCache commitCache = myCache.getRepositoryCache(repo, RevisionCacheType.COMMIT_CACHE);
    commitCache.saveRevision("v1", false, commitCache.getResetCounter());
    commitCache.saveRevision("v2", true, commitCache.getResetCounter());
    commitCache.saveRevision("v3", false, commitCache.getResetCounter());

    myCache.resetNegativeEntries(repo);

    then(commitCache.hasRevision("v1")).isNull();
    then(commitCache.hasRevision("v3")).isNull();
    then(commitCache).isEqualTo(new RevisionsCache(myConfig).getRepositoryCache(repo, RevisionCacheType.COMMIT_CACHE));
  }


  public void reset_negative_entries_different_types() throws Exception {
    File repo = repository("1");
    RepositoryRevisionCache commitCache = myCache.getRepositoryCache(repo, RevisionCacheType.COMMIT_CACHE);
    commitCache.saveRevision("v1", false, commitCache.getResetCounter());
    commitCache.saveRevision("v2", true, commitCache.getResetCounter());
    commitCache.saveRevision("v3", false, commitCache.getResetCounter());
    RepositoryRevisionCache hintCache = myCache.getRepositoryCache(repo, RevisionCacheType.HINT_CACHE);
    hintCache.saveRevision("v4", false, hintCache.getResetCounter());

    myCache.resetNegativeEntries(repo);

    for (RevisionCacheType type : RevisionCacheType.values()) {
      then(myCache.getRepositoryCache(repo, type).hasRevision("v1")).isNull();
      then(myCache.getRepositoryCache(repo, type).hasRevision("v3")).isNull();
      then(myCache.getRepositoryCache(repo, type).hasRevision("v4")).isNull();
    }
    then(commitCache).isEqualTo(new RevisionsCache(myConfig).getRepositoryCache(repo, RevisionCacheType.COMMIT_CACHE));
    then(hintCache).isEqualTo(new RevisionsCache(myConfig).getRepositoryCache(repo, RevisionCacheType.HINT_CACHE));
  }


  public void reset_negative_entries_with_new_commits() throws Exception {
    File repo = repository("1");
    RepositoryRevisionCache repoCache = myCache.getRepositoryCache(repo, RevisionCacheType.COMMIT_CACHE);
    repoCache.saveRevision("v1", false, repoCache.getResetCounter());
    repoCache.saveRevision("v2", true, repoCache.getResetCounter());
    repoCache.saveRevision("v3", false, repoCache.getResetCounter());

    myCache.resetNegativeEntries(repo, setOf("v3", "v4"));

    then(repoCache.hasRevision("v1")).isFalse();
    then(repoCache.hasRevision("v2")).isTrue();
    then(repoCache.hasRevision("v3")).isTrue();
    then(repoCache).isEqualTo(new RevisionsCache(myConfig).getRepositoryCache(repo, RevisionCacheType.COMMIT_CACHE));
  }


  public void test_equals() throws Exception {
    RepositoryRevisionCache cache1 = new RepositoryRevisionCache(myConfig, repository("1"), RevisionCacheType.COMMIT_CACHE, 100);
    RepositoryRevisionCache cache2 = new RepositoryRevisionCache(myConfig, repository("1"), RevisionCacheType.COMMIT_CACHE, 100);
    RepositoryRevisionCache cache3 = new RepositoryRevisionCache(myConfig, repository("1"), RevisionCacheType.HINT_CACHE, 100);
    then(cache1).isEqualTo(cache2);
    then(cache1).isNotEqualTo(cache3);

    cache1.saveRevision("v1", true, cache1.getResetCounter());
    cache1.saveRevision("v2", false, cache1.getResetCounter());
    cache2.saveRevision("v1", true, cache2.getResetCounter());
    cache2.saveRevision("v2", false, cache2.getResetCounter());

    then(cache1).isEqualTo(cache2);

    cache1.saveRevision("v3", true, cache1.getResetCounter());

    then(cache1).isNotEqualTo(cache2);

    cache2.saveRevision("v3", true, cache2.getResetCounter());
    cache2.saveRevision("v4", false, cache2.getResetCounter());

    then(cache1).isNotEqualTo(cache2);
  }


  public void should_write_empty_cache() throws Exception {
    File repo = repository("1");
    RepositoryRevisionCache repoCache = myCache.getRepositoryCache(repo, RevisionCacheType.COMMIT_CACHE);
    repoCache.saveRevision("v1", false, repoCache.getResetCounter());

    myCache.resetNegativeEntries(repo);

    then(repoCache).isEqualTo(new RevisionsCache(myConfig).getRepositoryCache(repo, RevisionCacheType.COMMIT_CACHE));
  }


  public void should_detect_broken_cache() throws Exception {
    File repo = repository("1");
    RepositoryRevisionCache repoCache = myCache.getRepositoryCache(repo, RevisionCacheType.COMMIT_CACHE);
    repoCache.saveRevision("v1", false, repoCache.getResetCounter());
    repoCache.saveRevision("v2", true, repoCache.getResetCounter());
    repoCache.saveRevision("v3", false, repoCache.getResetCounter());

    FileUtil.writeFile(RepositoryRevisionCache.getCacheFile(repo, RevisionCacheType.COMMIT_CACHE), "broken\n+data");

    try {
      RepositoryRevisionCache.read(myConfig, repo, RevisionCacheType.COMMIT_CACHE, 100);
      fail("Should fail to read broken cache");
    } catch (IOException e) {
      then(e.getMessage()).isEqualTo("Bad cache line 'broken'");
    }
  }


  public void disable_persist() throws Exception {
    myConfigBuilder.setPersistentCacheEnabled(false);

    File repo = repository("1");
    RepositoryRevisionCache repoCache = myCache.getRepositoryCache(repo, RevisionCacheType.COMMIT_CACHE);
    repoCache.saveRevision("v1", false, repoCache.getResetCounter());
    then(RepositoryRevisionCache.getCacheFile(repo, RevisionCacheType.COMMIT_CACHE)).doesNotExist();

    myConfigBuilder.setPersistentCacheEnabled(true);
    repoCache.saveRevision("v1", false, repoCache.getResetCounter());
    repoCache.saveRevision("v2", false, repoCache.getResetCounter());
    then(RepositoryRevisionCache.getCacheFile(repo, RevisionCacheType.COMMIT_CACHE)).exists();
  }


  public void disabled_persist_remove_cache_file() throws Exception {
    //in order to not load stale data after restart
    File repo = repository("1");
    RepositoryRevisionCache repoCache = myCache.getRepositoryCache(repo, RevisionCacheType.COMMIT_CACHE);
    repoCache.saveRevision("v1", false, repoCache.getResetCounter());

    myConfigBuilder.setPersistentCacheEnabled(false);
    repoCache.saveRevision("v1", true, repoCache.getResetCounter());

    myConfigBuilder.setPersistentCacheEnabled(true);
    RepositoryRevisionCache repoCache2 = new RevisionsCache(myConfig).getRepositoryCache(repo, RevisionCacheType.COMMIT_CACHE);
    then(repoCache2.hasRevision("v1")).isNull();//should have no information on v1 after restart, especially 'false'
  }


  public void should_write_to_disk_only_if_cache_was_updated() throws Exception {
    File repo = repository("1");
    RepositoryRevisionCache repoCache = myCache.getRepositoryCache(repo, RevisionCacheType.COMMIT_CACHE);
    File cacheFile = RepositoryRevisionCache.getCacheFile(repo, RevisionCacheType.COMMIT_CACHE);
    repoCache.saveRevision("v1", true, repoCache.getResetCounter());
    then(cacheFile).exists();

    FileUtil.delete(cacheFile);

    repoCache.saveRevision("v1", true, repoCache.getResetCounter());
    then(cacheFile).doesNotExist();

    myCache.resetNegativeEntries(repo);
    then(cacheFile).doesNotExist();

    myCache.resetNegativeEntries(repo, setOf("v2"));
    then(cacheFile).doesNotExist();
  }


  @Test(dataProvider = "true,false")
  public void should_contain_last_n_revisions(boolean afterRestart) throws Exception {
    int cacheSize = 10;
    myConfigBuilder.setMapFullPathRevisionCacheSize(cacheSize);
    ServerPluginConfig config = myConfigBuilder.build();
    myCache = new RevisionsCache(config);

    File repo = repository("1");
    RepositoryRevisionCache repoCache = myCache.getRepositoryCache(repo, RevisionCacheType.COMMIT_CACHE);
    for (int i = 0; i < 100; i++) {
      repoCache.saveRevision("v" + i, true, repoCache.getResetCounter());
    }

    if (afterRestart) {
      myCache = new RevisionsCache(config);
      repoCache = myCache.getRepositoryCache(repo, RevisionCacheType.COMMIT_CACHE);
    }

    for (int i = 100 - cacheSize; i < 100; i++) {
      then(repoCache.hasRevision("v" + i))
        .overridingErrorMessage("Doesn't contain entry for revision v" + i)
        .isTrue();
    }
  }


  @DataProvider
  public static Object[][] reset() {
    return new Object[][]{
      new Object[] {new ResetCacheConsumer("reset all") {
        @Override
        protected void run(RevisionsCache cache, File repo) throws Exception {
          cache.resetNegativeEntries(repo);
        }
      }},

      new Object[] {new ResetCacheConsumer("reset v1") {
        @Override
        protected void run(RevisionsCache cache, File repo) throws Exception {
          cache.resetNegativeEntries(repo, setOf("v1"));
        }
      }},

      new Object[] {new ResetCacheConsumer("reset v2") {
        @Override
        protected void run(RevisionsCache cache, File repo) throws Exception {
          cache.resetNegativeEntries(repo, setOf("v2"));
        }
      }}
    };
  }


  @TestFor(issues = {"TW-23460"})
  @Test(dataProvider = "reset")
  public void should_not_save_revision_if_there_was_reset(@NotNull BiConsumer<RevisionsCache, File> reset) throws Exception {
    File repo = repository("1");
    Semaphore s1 = new Semaphore(1);
    s1.acquire();
    Semaphore s2 = new Semaphore(1);
    s2.acquire();
    Thread mapFullPath = new Thread(() -> {
      try {
        RepositoryRevisionCache repoCache = myCache.getRepositoryCache(repo, RevisionCacheType.COMMIT_CACHE);
        long resetCounter = repoCache.getResetCounter();
        if (repoCache.hasRevision("v1") == null) {
          s1.release();
          //long commit lookup (we didn't find it)
          s2.acquire();
          repoCache.saveRevision("v1", false, resetCounter);
        }
      } catch (Exception e) {
        e.printStackTrace();
        throw new RuntimeException(e);
      }
    });
    mapFullPath.start();

    //collect changes which resets cache:
    s1.acquire();
    reset.accept(myCache, repo);
    s2.release();
    mapFullPath.join();

    then(myCache.getRepositoryCache(repo, RevisionCacheType.COMMIT_CACHE).hasRevision("v1")).isNull();
  }


  public void hint_cache_size_should_be_enough_to_keep_all_repositories() throws Exception {
    //create more repositories than configured cache size
    myConfigBuilder.setMapFullPathRevisionCacheSize(2);
    List<File> repositories = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      repositories.add(repository(String.valueOf(i)));
    }
    myCache = new RevisionsCache(myConfig);

    //for each repo remember that it contain one its hint revision
    //and doesn't contain revisions from other repositories
    for (File repo : repositories) {
      RepositoryRevisionCache cache = myCache.getRepositoryCache(repo, RevisionCacheType.HINT_CACHE);
      int repoNum = Integer.parseInt(repo.getName());
      for (int i = 0; i < 4; i++) {
        boolean contains = repoNum == i;
        cache.saveRevision("v" + i, contains, cache.getResetCounter());
      }
    }

    //check that all revisions are in cache
    for (File repo : repositories) {
      int repoNum = Integer.parseInt(repo.getName());
      RepositoryRevisionCache cache = myCache.getRepositoryCache(repo, RevisionCacheType.HINT_CACHE);
      for (int i = 0; i < 4; i++) {
        boolean contains = repoNum == i;
        then(cache.hasRevision("v" + i)).isEqualTo(contains);
      }
    }

    RepositoryRevisionCache cache5 = myCache.getRepositoryCache(repository("5"), RevisionCacheType.HINT_CACHE);
    cache5.saveRevision("v1", false, cache5.getResetCounter());
    cache5.saveRevision("v2", false, cache5.getResetCounter());
    cache5.saveRevision("v3", false, cache5.getResetCounter());
    then(cache5.hasRevision("v1")).isFalse();
    then(cache5.hasRevision("v2")).isFalse();
    then(cache5.hasRevision("v3")).isFalse();
  }


  public void reset_cache() throws Exception {
    File repo = repository("1");
    RepositoryRevisionCache repoCache = myCache.getRepositoryCache(repo, RevisionCacheType.COMMIT_CACHE);
    long resetCounter = repoCache.getResetCounter();
    repoCache.saveRevision("v1", true, resetCounter);

    repoCache.reset();

    then(repoCache.hasRevision("v1")).isNull();
    then(RepositoryRevisionCache.getCacheFile(repo, RevisionCacheType.COMMIT_CACHE)).doesNotExist();

    repoCache.saveRevision("v2", false, resetCounter);
    then(repoCache.hasRevision("v2")).isNull();
  }


  public void reset_all_caches() throws Exception {
    List<File> repos = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      File repo = repository(String.valueOf(i));
      repos.add(repo);
      for (RevisionCacheType type : RevisionCacheType.values()) {
        RepositoryRevisionCache repoCache = myCache.getRepositoryCache(repo, type);
        repoCache.saveRevision("v1", true, repoCache.getResetCounter());
      }
    }

    myCache.reset();

    for (File repo : repos) {
      for (RevisionCacheType type : RevisionCacheType.values()) {
        RepositoryRevisionCache repoCache = myCache.getRepositoryCache(repo, type);
        then(repoCache.hasRevision("v1")).isNull();
        then(RepositoryRevisionCache.getCacheFile(repo, type)).doesNotExist();
        repoCache.saveRevision("v2", false, 0);
        then(repoCache.hasRevision("v2")).isNull();
      }
    }
  }


  @NotNull
  private File repository(@NotNull String name) {
    File result = new File(myConfig.getCachesDir(), name);
    result.mkdirs();
    return result;
  }


  private static abstract class ResetCacheConsumer implements BiConsumer<RevisionsCache, File> {
    private final String myName;
    public ResetCacheConsumer(@NotNull String name) {
      myName = name;
    }

    @Override
    public void accept(final RevisionsCache cache, final File repo) {
      try {
        run(cache, repo);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    protected abstract void run(final RevisionsCache revisionsCache, final File file) throws Exception;

    @Override
    public String toString() {
      return myName;
    }
  }
}