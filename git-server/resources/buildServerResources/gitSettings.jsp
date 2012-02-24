<%@ page import="jetbrains.buildServer.buildTriggers.vcs.git.Constants" %>
<%@ page import="java.io.File" %>
<%@include file="/include.jsp" %>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%--
  ~ Copyright 2000-2012 JetBrains s.r.o.
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~ http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  --%>

<jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean"/>
<c:set var="gitPathEnv" value="<%= Constants.TEAMCITY_AGENT_GIT_PATH %>"/>
<table class="runnerFormTable">
  <c:set var="userHome"
         value='<%=new File(System.getProperty("user.home"), ".ssh"+File.separator+"config").getAbsolutePath() %>'/>
  <l:settingsGroup title="General Settings">
    <tr>
      <th><label for="url">Fetch URL: <l:star/></label></th>
      <td><props:textProperty name="url" className="longField"/>
        <div class="smallNote" style="margin: 0;">It is used for fetching data from repository.</div>
        <span class="error" id="error_url"></span></td>
    </tr>
    <tr>
      <th><label for="url">Push URL:</label></th>
      <td><props:textProperty name="push_url" className="longField"/>
        <div class="smallNote" style="margin: 0;">It is used for pushing tags to the remote repository.
          If blank, the fetch url is used.
        </div>
        <span class="error" id="error_push_url"></span>
      </td>
    </tr>
    <tr>
      <th><label for="branch">Ref name: <l:star/></label></th>
      <td><props:textProperty name="branch"/></td>
    </tr>
    <tr>
      <th><label for="path">Clone repository to: </label></th>
      <td><props:textProperty name="path" className="longField"/>
        <div class="smallNote" style="margin: 0;">Provide path to a directory on TeamCity server where a
          bare cloned repository should be created. Leave blank to use default path.
        </div>
      </td>
    </tr>
    <tr>
      <th><label for="usernameStyle">User Name Style:</label></th>
      <td><props:selectProperty name="usernameStyle">
        <props:option value="USERID">UserId (jsmith)</props:option>
        <props:option value="NAME">Author Name (John Smith)</props:option>
        <props:option value="FULL">Author Name and Email (John Smith &lt;jsmith@example.org&gt;)</props:option>
        <props:option value="EMAIL">Author Email (jsmith@example.org)</props:option>
      </props:selectProperty>
        <div class="smallNote" style="margin: 0;">Changing user name style will affect only newly collected changes.
          Old changes will continue to be stored with the style that was active at the time of collecting changes.
        </div>
      </td>
    </tr>
    <tr>
      <th><label for="submoduleCheckout">Submodules:</label></th>
      <td><props:selectProperty name="submoduleCheckout">
        <props:option value="IGNORE">Ignore</props:option>
        <props:option value="CHECKOUT">Checkout</props:option>
      </props:selectProperty>
        <div class="smallNote" style="margin: 0">If the option "Checkout" is selected, the submodules are
          treated as part of the source tree.
        </div>
      </td>
    </tr>
    <tr id="userForTags">
      <th><label for="userForTags">Username for tags:</label></th>
      <td>
        <props:textProperty name="userForTags"/>
        <div class="smallNote" style="margin: 0">Format: User Name &lt;email&gt;</div>
      </td>
    </tr>
  </l:settingsGroup>
  <l:settingsGroup title="Authentication settings">
    <tr>
      <th><label for="authMethod">Authentication Method:</label></th>
      <td><props:selectProperty name="authMethod" onchange="gitSelectAuthentication()">
        <props:option value="ANONYMOUS">Anonymous</props:option>
        <props:option value="PRIVATE_KEY_DEFAULT">Default Private Key</props:option>
        <props:option value="PASSWORD">Password</props:option>
        <props:option value="PRIVATE_KEY_FILE">Private Key</props:option>
      </props:selectProperty>
        <div id="sshPrivateKeyNote" class="smallNote" style="margin: 0">Valid only for SSH protocol and
          applicable to both fetch and push urls.
        </div>
        <div id="defaultPrivateKeyNote" class="smallNote" style="margin: 0">Uses mapping specified in the file
          ${userHome} if that that file exists.
        </div>
      </td>
    </tr>
    <tr id="gitUsername">
      <th><label for="username">User name:</label></th>
      <td><props:textProperty name="username"/>
        <div class="smallNote" style="margin: 0">Username must be specified if there is no username in the clone URL.
          The user name specified here overrides username from URL.
        </div>
      </td>
    </tr>
    <tr id="gitKnownHosts">
      <th>Ignore Known Hosts Database:</th>
      <td><props:checkboxProperty name="ignoreKnownHosts"/></td>
    </tr>
    <tr id="gitPasswordRow">
      <th><label for="secure:password">Password:</label></th>
      <td><props:passwordProperty name="secure:password"/></td>
    </tr>
    <tr id="gitPrivateKeyRow">
      <th><label for="privateKeyPath">Private Key Path: <l:star/></label></th>
      <td><props:textProperty name="privateKeyPath" className="longField"/>
        <div class="smallNote" style="margin: 0;">Specify path to the private key
          on the TeamCity server host.
        </div>
      </td>
    </tr>
    <tr id="gitPassphraseRow">
      <th><label for="secure:passphrase">Passphrase:</label></th>
      <td><props:passwordProperty name="secure:passphrase"/></td>
    </tr>
  </l:settingsGroup>
  <l:settingsGroup title="Agent Settings">
    <tr>
      <td colspan="2">Agent-specific settings that are used in case of agent checkout.<bs:help file="Git+(JetBrains)" anchor="agentSettings"/></td>
    </tr>
    <tr>
      <th><label for="agentGitPath">Path to git: </label></th>
      <td><props:textProperty name="agentGitPath" className="longField"/>
        <div class="smallNote" style="margin: 0;">Provide path to a git executable
        to be used on agent. If the path is not specified, TeamCity will use
        the location set up in ${gitPathEnv} environment  variable.
        </div>
      </td>
    </tr>
    <tr>
      <th><label for="agentCleanPolicy">Clean Policy:</label></th>
      <td><props:selectProperty name="agentCleanPolicy">
        <props:option value="ON_BRANCH_CHANGE">On Branch Change</props:option>
        <props:option value="ALWAYS">Always</props:option>
        <props:option value="NEVER">Never</props:option>
      </props:selectProperty>
        <div class="smallNote" style="margin: 0">This option specifies when "git clean" command is run on agent.
        </div>
      </td>
    </tr>
    <tr>
      <th><label for="agentCleanFilesPolicy">Clean Files Policy:</label></th>
      <td><props:selectProperty name="agentCleanFilesPolicy">
        <props:option value="ALL_UNTRACKED">All untracked files</props:option>
        <props:option value="IGNORED_ONLY">All ignored untracked files</props:option>
        <props:option value="NON_IGNORED_ONLY">All non-ignored untracked files</props:option>
      </props:selectProperty>
        <div class="smallNote" style="margin: 0">This option specifies which files will be removed when "git
          clean" command is run on agent.
        </div>
      </td>
    </tr>
  </l:settingsGroup>
</table>
<script type="text/javascript">
  window.gitSelectAuthentication = function() {
    var c = $('authMethod');
    switch (c.value) {
      case 'PRIVATE_KEY_DEFAULT':
        BS.Util.hide('gitPasswordRow', 'gitPrivateKeyRow', 'gitPassphraseRow');
        BS.Util.show('gitUsername', 'gitKnownHosts');
        BS.Util.show('sshPrivateKeyNote', 'defaultPrivateKeyNote');
        break;
      case 'PRIVATE_KEY_FILE':
        BS.Util.hide('gitPasswordRow', 'gitKnownHosts');
        BS.Util.show('gitUsername', 'gitPrivateKeyRow', 'gitPassphraseRow');
        BS.Util.hide('defaultPrivateKeyNote');
        BS.Util.show('sshPrivateKeyNote');
        break;
      case 'PASSWORD':
        BS.Util.show('gitUsername', 'gitPasswordRow');
        BS.Util.hide('gitPrivateKeyRow', 'gitPassphraseRow', 'gitKnownHosts');
        BS.Util.hide('sshPrivateKeyNote', 'defaultPrivateKeyNote');
        break;
      case 'ANONYMOUS':
        BS.Util.hide('gitUsername', 'gitPasswordRow', 'gitPrivateKeyRow', 'gitPassphraseRow', 'gitKnownHosts');
        BS.Util.hide('sshPrivateKeyNote', 'defaultPrivateKeyNote');
        break;
      default:
        alert('Unknown value: ' + c.value);
        break;
    }
    BS.VisibilityHandlers.updateVisibility($('vcsRootProperties'));
  }
  gitSelectAuthentication();
  if ($('url').value == "") {
    $('submoduleCheckout').selectedIndex = 1;
  }
</script>
