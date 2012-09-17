/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import jetbrains.buildServer.buildTriggers.vcs.git.Constants;
import jetbrains.buildServer.buildTriggers.vcs.git.VcsPropertiesProcessor;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.util.TestFor;
import junit.framework.TestCase;
import org.testng.annotations.Test;

import java.util.Collection;
import java.util.HashMap;

import static jetbrains.buildServer.util.Util.map;

/**
 * @author dmitry.neverov
 */
@Test
public class VcsPropertiesProcessorTest extends TestCase {

  private VcsPropertiesProcessor myProcessor = new VcsPropertiesProcessor();


  public void empty_push_url_is_allowed() {
    Collection<InvalidProperty> invalids = myProcessor.process(new HashMap<String, String>() {{
      put(Constants.FETCH_URL, "git://some.org/repository");
    }});
    assertTrue(invalids.isEmpty());
  }


  public void non_default_key_auth_requires_private_key_path() {
    Collection<InvalidProperty> invalids = myProcessor.process(new HashMap<String, String>() {{
      put(Constants.FETCH_URL, "git://some.org/repository");
      put(Constants.AUTH_METHOD, "PRIVATE_KEY_FILE");
    }});
    assertEquals(1, invalids.size());
    assertEquals(Constants.PRIVATE_KEY_PATH, invalids.iterator().next().getPropertyName());
  }


  public void branch_starts_with_slash() {
    Collection<InvalidProperty> invalids = myProcessor.process(
      map(Constants.FETCH_URL, "git://some.org/repository",
          Constants.BRANCH_NAME, "/refs/heads/master",
          Constants.BRANCH_SPEC, "+:refs/heads/*"));
    assertEquals(1, invalids.size());
    assertEquals(Constants.BRANCH_NAME, invalids.iterator().next().getPropertyName());
  }


  public void branchSpec_contains_pattern_started_from_slash() {
    Collection<InvalidProperty> invalids = myProcessor.process(
      map(Constants.FETCH_URL, "git://some.org/repository",
          Constants.BRANCH_NAME, "refs/heads/master",
          Constants.BRANCH_SPEC, "+:/refs/heads/*"));
    assertEquals(1, invalids.size());
    assertEquals(Constants.BRANCH_SPEC, invalids.iterator().next().getPropertyName());
  }


  @TestFor(issues = "TW-23174")
  public void branchSpec_count_empty_lines() {
    Collection<InvalidProperty> invalids = myProcessor.process(
      map(Constants.FETCH_URL, "git://some.org/repository",
          Constants.BRANCH_NAME, "refs/heads/master",
          Constants.BRANCH_SPEC, "+:refs/heads/*\n\n-:/refs/heads/release"));
    assertEquals(1, invalids.size());
    String reason = invalids.iterator().next().getInvalidReason();
    assertTrue(reason != null && reason.startsWith("Line 3:"));
  }


  @TestFor(issues = {"TW-23424", "TW-23220"})
  public void should_not_try_to_parse_urls_with_parameters() {
    Collection<InvalidProperty> errors = myProcessor.process(map(Constants.FETCH_URL, "file://c:%5CWork\\Test\\git\\%url.param%",
                                                                 Constants.PUSH_URL, "file://c:%5CWork\\Test\\git\\%url.param%"));
    assertTrue(errors.isEmpty());
  }

}
