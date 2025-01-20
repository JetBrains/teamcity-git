package jetbrains.buildServer.buildTriggers.vcs.git.tests.gitProxy;

import java.util.*;
import jetbrains.buildServer.buildTriggers.vcs.git.gitProxy.ChangesCollectorCache;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.impl.VcsRootImpl;
import org.testng.annotations.Test;

import static jetbrains.buildServer.util.Util.map;
import static org.assertj.core.api.BDDAssertions.then;

public class ChangesCollectorCacheTest {

  @Test
  public void concurrencyTest() throws InterruptedException {
    int cntTrheads = 1000;
    ChangesCollectorCache cache = new ChangesCollectorCache();
    RepositoryStateData fromState = RepositoryStateData.createVersionState("master", map("master", "rev1", "branch1", "rev2"));
    RepositoryStateData toState = RepositoryStateData.createVersionState("master", map("master", "rev1", "branch1", "rev5"));

    List<VcsRoot> vcsRoots = new ArrayList<>();
    List<List<ModificationData>> results = new ArrayList<List<ModificationData>>(Collections.nCopies(cntTrheads, null));

    List<Thread> threads = new ArrayList<>();

    for (int i = 0; i < cntTrheads; i++) {
      int _i = i;
      VcsRoot root = new VcsRootImpl(i + 1, "git");
      vcsRoots.add(root);
      Thread thread = new Thread(() -> {
        ChangesCollectorCache.Key key = cache.getKey(fromState, toState, "github.com");
        ChangesCollectorCache.Result result = cache.getOrCreateNew(key, root);
        if (result.getType() == ChangesCollectorCache.ResultType.NEW) {
          ModificationData md = new ModificationData(new Date(1L * 1000),
                                                           Arrays.asList(new VcsChange(VcsChangeInfo.Type.REMOVED, null, "file0", "file0", "rev2", "rev3")),
                                                           "commit3",
                                                           "user",
                                                           root,
                                                           "rev3", "rev3");
          md.setParentRevisions(Collections.singletonList("rev2"));
          md.setAttribute("teamcity.commit.time", "2000");
          result.complete(Arrays.asList(md));
          md.setAttribute("a", "b");
          try {
            results.set(_i, result.getResult(10));
          } catch (Throwable t) {
            throw new RuntimeException(t);
          }
        } else {
          try {
            results.set(_i, result.getResult(10));
          } catch (Throwable t) {
            throw new RuntimeException(t);
          }
        }
      });
      thread.start();
      threads.add(thread);
    }

    for (Thread thread : threads) {
      thread.join(10000);
    }

    for (int i = 0; i < cntTrheads; i++) {
      then(results.get(i)).hasSize(1);
      then(results.get(i).get(0).getDescription()).isEqualTo("commit3");
      then(results.get(i).get(0).getAttributes()).hasSize(1);
      then(results.get(i).get(0).getVcsRoot().getId()).isEqualTo(vcsRoots.get(i).getId());
    }
  }
}
