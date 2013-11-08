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

<%@ page import="jetbrains.buildServer.buildTriggers.vcs.git.Constants" %>
<%@ page import="jetbrains.buildServer.buildTriggers.vcs.git.PluginConfigImpl" %>
<%@ page import="java.io.File" %>
<%@include file="/include.jsp" %>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="admin" tagdir="/WEB-INF/tags/admin" %>

<jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean"/>
<c:set var="gitPathEnv" value="<%= Constants.TEAMCITY_AGENT_GIT_PATH %>"/>
<c:set var="teamcitySshKeysEnabled" value="<%= PluginConfigImpl.isTeamcitySshKeysEnabled() %>"/>
<style>
.gitUsernameStyleHighlight {
  color: rgb(97, 94, 192);
}
</style>
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
      <th><label for="push_url">Push URL:</label></th>
      <td><props:textProperty name="push_url" className="longField"/>
        <div class="smallNote" style="margin: 0;">It is used for pushing tags to the remote repository.
          If blank, the fetch url is used.
        </div>
        <span class="error" id="error_push_url"></span>
      </td>
    </tr>
    <tr>
      <th><label for="branch">Default branch: <l:star/></label></th>
      <td>
        <props:textProperty name="branch"/>
        <div class="smallNote" style="margin: 0">Branch to be used if no branch from Branch Specification is set</div>
        <span class="error" id="error_branch"></span>
      </td>
    </tr>
    <bs:branchSpecTableRow/>
    <tr class="advancedSetting">
      <th><label for="reportTagRevisions">Use tags as branches:</label></th>
      <td>
        <props:checkboxProperty name="reportTagRevisions"/>
        <div class="smallNote" style="margin: 0">If enabled tags can be used in branch specification</div>
      </td>
    </tr>
    <tr class="advancedSetting">
      <th><label for="usernameStyle">Username style:</label></th>
      <td><props:selectProperty name="usernameStyle" enableFilter="true">
        <props:option value="USERID">UserId</props:option>
        <props:option value="NAME">Author Name</props:option>
        <props:option value="FULL">Author Name and Email</props:option>
        <props:option value="EMAIL">Author Email</props:option>
      </props:selectProperty>
        <div class="smallNote" style="margin: 0;">
          Defines a way TeamCity binds VCS changes to the user. With
          selected style and the following content of ~/.gitconfig:
          <div style="font-weight: bold">[user]</div>
          <div style="margin-left: 15px">name = <span id="gitConfigUserName">Joe Coder</span></div>
	      <div style="margin-left: 15px">email = <span id="gitConfigUserId">joe.coder</span><span id="gitConfigEmail">@acme.com</span></div>
          you should enter <span id="usernameToEnter" class="gitUsernameStyleHighlight"></span> in
          Version Control Username Settings in your profile.
          <br/>
          Changing username style will affect only newly collected
          changes. Old changes will continue to be stored with the style
          that was active at the time of collecting changes.
        </div>
      </td>
    </tr>
    <tr class="advancedSetting">
      <th><label for="submoduleCheckout">Submodules:</label></th>
      <td><props:selectProperty name="submoduleCheckout" enableFilter="true">
        <props:option value="IGNORE">Ignore</props:option>
        <props:option value="CHECKOUT">Checkout</props:option>
      </props:selectProperty>
        <div class="smallNote" style="margin: 0">If the option "Checkout" is selected, the submodules are
          treated as part of the source tree.
        </div>
      </td>
    </tr>
    <tr class="advancedSetting">
      <th><label for="userForTags">Username for tags/merge:</label></th>
      <td><props:textProperty name="userForTags"/>
        <div class="smallNote" style="margin: 0">Format: User Name &lt;email&gt;</div>
      </td>
    </tr>
  </l:settingsGroup>
  <l:settingsGroup title="Authentication Settings">
    <tr>
      <th><label for="authMethod">Authentication method:</label></th>
      <td>
        <props:selectProperty name="authMethod" onchange="gitSelectAuthentication()" enableFilter="true">
          <props:option value="ANONYMOUS">Anonymous</props:option>
          <props:option value="PRIVATE_KEY_DEFAULT">Default Private Key</props:option>
          <props:option value="PASSWORD">Password</props:option>
          <props:option value="PRIVATE_KEY_FILE">Private Key</props:option>
          <c:if test="${teamcitySshKeysEnabled}">
            <props:option value="TEAMCITY_SSH_KEY">TeamCity SSH Key</props:option>
          </c:if>
        </props:selectProperty>
        <div id="sshPrivateKeyNote" class="smallNote" style="margin: 0">Valid only for SSH protocol and
          applicable to both fetch and push urls.
        </div>
        <div id="defaultPrivateKeyNote" class="smallNote" style="margin: 0">Uses mapping specified in the file
          ${userHome} if that file exists.
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
      <th><label for="ignoreKnownHosts">Ignore known hosts database:</label></th>
      <td><props:checkboxProperty name="ignoreKnownHosts"/></td>
    </tr>
    <tr id="gitPasswordRow">
      <th><label for="secure:password">Password:</label></th>
      <td><props:passwordProperty name="secure:password"/></td>
    </tr>
    <tr id="gitPrivateKeyRow">
      <th><label for="privateKeyPath">Private key path: <l:star/></label></th>
      <td><props:textProperty name="privateKeyPath" className="longField"/>
        <div class="smallNote" style="margin: 0;">Specify path to the private key
          on the TeamCity server host.
        </div>
        <span class="error" id="error_privateKeyPath"></span>
      </td>
    </tr>
    <c:if test="${not empty vcsPropertiesBean.belongsToProject}">
      <c:set var="projectId" value="${vcsPropertiesBean.belongsToProject.externalId}" scope="request"/>
    </c:if>
    <tr id="gitTeamCityKeyRow">
      <th><label for="privateKeyPath">TeamCity SSH Key: <l:star/></label></th>
      <td>
        <admin:sshKeys chooserName="teamcitySshKeyId" projectId="${projectId}"/>
        <span class="error" id="error_teamcitySshKeyId"></span>
      </td>
    </tr>
    <tr id="gitPassphraseRow">
      <th><label for="secure:passphrase">Passphrase:</label></th>
      <td><props:passwordProperty name="secure:passphrase"/></td>
    </tr>
  </l:settingsGroup>
  <l:settingsGroup title="Server Settings" className="advancedSetting">
    <tr class="advancedSetting">
      <td colspan="2">Settings that are used in case of server-side checkout.</td>
    </tr>
    <tr class="advancedSetting">
      <th>
        <label for="serverSideAutoCrlf">Convert line-endings to CRLF:<bs:help file="Git+(JetBrains)" anchor="serverAutoCRLF"/></label>
      </th>
      <td>
        <props:checkboxProperty name="serverSideAutoCrlf"/>
      </td>
    </tr>
    <tr class="advancedSetting">
      <th><label for="path">Custom clone directory on server:<bs:help file="Git+(JetBrains)" anchor="customCloneDir"/></label></th>
      <td><props:textProperty name="path" className="longField"/>
        <div class="smallNote" style="margin: 0;">
          A directory on TeamCity server where a bare cloned repository should be created. Leave blank to use default path.
        </div>
      </td>
    </tr>
  </l:settingsGroup>
  <l:settingsGroup title="Agent Settings" className="advancedSetting">
    <tr class="advancedSetting">
      <td colspan="2">Agent-specific settings that are used in case of agent checkout.<bs:help file="Git+(JetBrains)" anchor="agentSettings"/></td>
    </tr>
    <tr class="advancedSetting">
      <th><label for="agentGitPath">Path to Git: </label></th>
      <td><props:textProperty name="agentGitPath" className="longField"/>
        <div class="smallNote" style="margin: 0;">Provide path to a git executable
        to be used on agent. If the path is not specified, TeamCity will use
        the location set up in ${gitPathEnv} environment  variable.
        </div>
      </td>
    </tr>
    <tr class="advancedSetting">
      <th><label for="agentCleanPolicy">Clean policy:</label></th>
      <td><props:selectProperty name="agentCleanPolicy" enableFilter="true">
        <props:option value="ON_BRANCH_CHANGE">On Branch Change</props:option>
        <props:option value="ALWAYS">Always</props:option>
        <props:option value="NEVER">Never</props:option>
      </props:selectProperty>
        <div class="smallNote" style="margin: 0">This option specifies when "git clean" command is run on agent.
        </div>
      </td>
    </tr>
    <tr class="advancedSetting">
      <th><label for="agentCleanFilesPolicy">Clean files policy:</label></th>
      <td><props:selectProperty name="agentCleanFilesPolicy" enableFilter="true">
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
      case 'TEAMCITY_SSH_KEY':
        BS.Util.hide('gitPasswordRow', 'gitPrivateKeyRow', 'gitPassphraseRow');
        BS.Util.show('gitUsername', 'gitKnownHosts');
        BS.Util.show('gitTeamCityKeyRow');
        break;
      case 'PRIVATE_KEY_DEFAULT':
        BS.Util.hide('gitPasswordRow', 'gitPrivateKeyRow', 'gitPassphraseRow', 'gitTeamCityKeyRow');
        BS.Util.show('gitUsername', 'gitKnownHosts');
        BS.Util.show('sshPrivateKeyNote', 'defaultPrivateKeyNote');
        break;
      case 'PRIVATE_KEY_FILE':
        BS.Util.hide('gitPasswordRow', 'gitKnownHosts', 'gitTeamCityKeyRow');
        BS.Util.show('gitUsername', 'gitPrivateKeyRow', 'gitPassphraseRow');
        BS.Util.hide('defaultPrivateKeyNote');
        BS.Util.show('sshPrivateKeyNote');
        break;
      case 'PASSWORD':
        BS.Util.show('gitUsername', 'gitPasswordRow', 'gitTeamCityKeyRow');
        BS.Util.hide('gitPrivateKeyRow', 'gitPassphraseRow', 'gitKnownHosts');
        BS.Util.hide('sshPrivateKeyNote', 'defaultPrivateKeyNote');
        break;
      case 'ANONYMOUS':
        BS.Util.hide('gitUsername', 'gitPasswordRow', 'gitPrivateKeyRow', 'gitPassphraseRow', 'gitKnownHosts', 'gitTeamCityKeyRow');
        BS.Util.hide('sshPrivateKeyNote', 'defaultPrivateKeyNote');
        break;
      default:
        alert('Unknown value: ' + c.value);
        break;
    }
    BS.VisibilityHandlers.updateVisibility($('vcsRootProperties'));
  };
  gitSelectAuthentication();
  if ($('url').value == "") {
    $('submoduleCheckout').selectedIndex = 1;
  }

  illustrateUsernameStyle = function() {
    var style = $j("#usernameStyle").val();
    $j("#gitConfigUserName").removeClass("gitUsernameStyleHighlight");
    $j("#gitConfigUserId").removeClass("gitUsernameStyleHighlight");
    $j("#gitConfigEmail").removeClass("gitUsernameStyleHighlight");
    if (style === "USERID") {
      $j("#gitConfigUserId").addClass("gitUsernameStyleHighlight");
      $j("#usernameToEnter").text("joe.coder");
    } else if (style === "NAME") {
      $j("#gitConfigUserName").addClass("gitUsernameStyleHighlight");
      $j("#usernameToEnter").text("Joe Coder");
    } else if (style === "FULL") {
      $j("#gitConfigUserName").addClass("gitUsernameStyleHighlight");
      $j("#gitConfigUserId").addClass("gitUsernameStyleHighlight");
      $j("#gitConfigEmail").addClass("gitUsernameStyleHighlight");
      $j("#usernameToEnter").text("Joe Coder <joe.coder@acme.com>");
    } else if (style === "EMAIL") {
      $j("#gitConfigUserId").addClass("gitUsernameStyleHighlight");
      $j("#gitConfigEmail").addClass("gitUsernameStyleHighlight");
      $j("#usernameToEnter").text("joe.coder@acme.com");
    }
  };

  illustrateUsernameStyle();

  $j("#usernameStyle").change(illustrateUsernameStyle);
</script>
