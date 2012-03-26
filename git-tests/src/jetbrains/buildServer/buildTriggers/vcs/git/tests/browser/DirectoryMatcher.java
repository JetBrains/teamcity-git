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

package jetbrains.buildServer.buildTriggers.vcs.git.tests.browser;

import jetbrains.buildServer.util.browser.Element;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

/**
* @author dmitry.neverov
*/
public class DirectoryMatcher extends TypeSafeMatcher<Element> {

  public static DirectoryMatcher directory() {
    return new DirectoryMatcher();
  }

  @Override
  public boolean matchesSafely(Element element) {
    return !element.isLeaf() && !element.isContentAvailable();
  }

  public void describeTo(Description description) {
    description.appendText("element which is not leaf and has not available content");
  }
}
