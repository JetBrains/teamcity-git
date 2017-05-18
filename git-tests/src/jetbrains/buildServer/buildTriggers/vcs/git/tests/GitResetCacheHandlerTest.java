/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.buildTriggers.vcs.git.GcErrors;
import jetbrains.buildServer.buildTriggers.vcs.git.GitResetCacheHandler;
import jetbrains.buildServer.buildTriggers.vcs.git.RepositoryManager;
import jetbrains.buildServer.serverSide.BasePropertiesModel;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.util.cache.ResetCacheHandler;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static org.testng.AssertJUnit.*;

/**
 * @author dmitry.neverov
 */
@Test
public class GitResetCacheHandlerTest {

  private Mockery myContext;
  private TempFiles myTempFiles;
  private File myCachesDir;
  private ResetCacheHandler myCacheHandler;
  private RepositoryManager myRepositoryManager;

  @BeforeMethod
  public void setUp() throws IOException {
    new TeamCityProperties() {{
      setModel(new BasePropertiesModel() {});
    }};
    myContext = new Mockery() {{
      setImposteriser(ClassImposteriser.INSTANCE);
    }};
    myTempFiles = new TempFiles();
    myCachesDir = myTempFiles.createTempDir();
    myRepositoryManager = myContext.mock(RepositoryManager.class);
    myCacheHandler = new GitResetCacheHandler(myRepositoryManager, new GcErrors());
  }

  @AfterMethod
  public void tearDown() {
    myTempFiles.cleanup();
  }


  public void cache_list_should_contains_entry_for_git() {
    assertEquals(asList("git"), myCacheHandler.listCaches());
  }


  public void git_cache_is_empty_when_there_are_no_mirrors() {
    myContext.checking(new Expectations() {{
      atLeast(1).of(myRepositoryManager).getMappings(); will(returnValue(emptyMap()));
    }});

    assertTrue(myCacheHandler.isEmpty("git"));
    myContext.assertIsSatisfied();
  }


  public void git_cache_is_not_empty_when_mirrors_exist() {
    final Map<String, File> mapping = new HashMap<String, File>() {{
      put("http://some.org/repository1", new File(myCachesDir, "a"));
      put("http://some.org/repository2", new File(myCachesDir, "b"));
    }};
    myContext.checking(new Expectations() {{
      atLeast(1).of(myRepositoryManager).getMappings(); will(returnValue(mapping));
    }});

    assertFalse(myCacheHandler.isEmpty("git"));
    myContext.assertIsSatisfied();
  }


  public void reset_cache_should_reset_caches_for_all_mirrors() {
    final String url1 = "git://some.org/repository1";
    final String url2 = "git://some.org/repository2";
    final File mirror1 = new File(myCachesDir, "a");
    final File mirror2 = new File(myCachesDir, "b");
    mirror1.mkdirs();
    mirror2.mkdirs();
    final Map<String, File> mapping = new HashMap<String, File>() {{
      put(url1, mirror1);
      put(url2, mirror2);
    }};
    myContext.checking(new Expectations() {{
      atLeast(1).of(myRepositoryManager).getMappings(); will(returnValue(mapping));
      atLeast(1).of(myRepositoryManager).getRmLock(mirror1);
      atLeast(1).of(myRepositoryManager).getRmLock(mirror2);
    }});

    myCacheHandler.resetCache("git");
    myContext.assertIsSatisfied();
    assertTrue("Caches dir is not empty", myCachesDir.listFiles().length == 0);
  }
}
