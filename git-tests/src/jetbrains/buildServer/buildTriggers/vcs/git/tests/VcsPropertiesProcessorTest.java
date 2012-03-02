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
import junit.framework.TestCase;
import org.testng.annotations.Test;

import java.util.Collection;
import java.util.HashMap;

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

}
