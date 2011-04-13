/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package jetbrains.buildServer.buildTriggers.vcs.git;

import jetbrains.buildServer.buildTriggers.vcs.AbstractVcsPropertiesProcessor;
import jetbrains.buildServer.serverSide.InvalidProperty;
import org.eclipse.jgit.transport.URIish;

import java.net.URISyntaxException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;

/**
 * @author dmitry.neverov
 */
public class VcsPropertiesProcessor extends AbstractVcsPropertiesProcessor {

  public Collection<InvalidProperty> process(Map<String, String> properties) {
    Collection<InvalidProperty> rc = new LinkedList<InvalidProperty>();
    String url = properties.get(Constants.FETCH_URL);
    if (isEmpty(url)) {
      rc.add(new InvalidProperty(Constants.FETCH_URL, "The URL must be specified"));
    } else {
      try {
        new URIish(url);
      } catch (URISyntaxException e) {
        rc.add(new InvalidProperty(Constants.FETCH_URL, "Invalid URL syntax: " + url));
      }
    }
    String pushUrl = properties.get(Constants.PUSH_URL);
    if (isEmpty(pushUrl)) {
      try {
        new URIish(pushUrl);
      } catch (URISyntaxException e) {
        rc.add(new InvalidProperty(Constants.PUSH_URL, "Invalid URL syntax: " + pushUrl));
      }
    }
    String authMethod = properties.get(Constants.AUTH_METHOD);
    AuthenticationMethod authenticationMethod =
      authMethod == null ? AuthenticationMethod.ANONYMOUS : Enum.valueOf(AuthenticationMethod.class, authMethod);
    switch (authenticationMethod) {
      case PRIVATE_KEY_FILE:
        String pkFile = properties.get(Constants.PRIVATE_KEY_PATH);
        if (isEmpty(pkFile)) {
          rc.add(new InvalidProperty(Constants.PRIVATE_KEY_PATH, "The private key path must be specified."));
        }
        break;
    }
    return rc;
  }

}
