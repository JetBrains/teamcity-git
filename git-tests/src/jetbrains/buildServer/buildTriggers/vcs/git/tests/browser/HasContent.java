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

import jetbrains.buildServer.buildTriggers.vcs.git.browse.GitFile;
import jetbrains.buildServer.util.browser.Element;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.jetbrains.annotations.NotNull;

import java.io.*;

/**
 * @author dmitry.neverov
 */
public class HasContent extends TypeSafeMatcher<Element> {

  private String myExpectedContent;

  public HasContent() {
  }

  @Override
  public boolean matchesSafely(Element element) {
    try {
      if (!(element instanceof GitFile))
        return false;
      GitFile file = (GitFile) element;
      byte[] expected = file.getGit().getContent(file.getFullName(), file.getRoot(), file.getRevision());
      myExpectedContent = new String(expected);
      return myExpectedContent.equals(readStream(file.getInputStream()));
    } catch (Exception e) {
      return false;
    }
  }

  private String readStream(@NotNull InputStream stream) throws IOException {
    StringBuilder sb = new StringBuilder();
    char[] chars = new char[512];
    Reader processInput = new BufferedReader(new InputStreamReader(stream));
    int count;
    while ((count = processInput.read(chars)) != -1) {
      sb.append(chars, 0, count);
    }
    return sb.toString();
  }

  public void describeTo(Description description) {
    description.appendText("file element with content ").appendValue(myExpectedContent);
  }

  public static HasContent hasContentAsInRepository() {
    return new HasContent();
  }
}
