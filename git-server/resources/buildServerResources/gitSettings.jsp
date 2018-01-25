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
<%@ page import="jetbrains.buildServer.serverSide.TeamCityProperties" %>
<%@ page import="jetbrains.buildServer.util.StringUtil" %>
<%@ page import="java.io.File" %>
<%@include file="/include.jsp" %>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="admin" tagdir="/WEB-INF/tags/admin" %>

<jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean"/>
<c:set var="gitPathEnv" value="<%= Constants.TEAMCITY_AGENT_GIT_PATH %>"/>
<c:set var="teamcitySshKeysEnabled" value="<%= PluginConfigImpl.isTeamcitySshKeysEnabled() %>"/>
<c:set var="showKnownHostsDbOption" value="<%= PluginConfigImpl.showKnownHostsDbOption() %>"/>
<c:set var="showCustomClonePath" value="<%= TeamCityProperties.getBoolean(Constants.CUSTOM_CLONE_PATH_ENABLED) &&
                                            (TeamCityProperties.getBoolean(Constants.SHOW_CUSTOM_CLONE_PATH)
                                            || !StringUtil.isEmpty(propertiesBean.getProperties().get(Constants.PATH))) %>"/>
<style>
.gitUsernameStyleHighlight {
  color: rgb(97, 94, 192);
}
</style>
<script type="text/javascript">
  uploadedKeySelected = function(encrypted) {
    if (encrypted) {
      $j('#gitPassphraseRow').show();
    } else {
      $j('#secure\\:passphrase').val('');
      $j('#gitPassphraseRow').hide();
    }
  };
</script>
<table class="runnerFormTable">
  <c:set var="userHome"
         value='<%=new File(System.getProperty("user.home"), ".ssh"+File.separator+"config").getAbsolutePath() %>'/>
  <l:settingsGroup title="General Settings">
    <tr>
      <th><label for="url">Fetch URL: <l:star/></label></th>
      <td><props:textProperty name="url" className="longField"/>
        <jsp:include page="/admin/repositoryControls.html?projectId=${parentProject.externalId}&vcsType=git"/>
        <div class="smallNote" style="margin: 0;">It is used for fetching data from the repository.</div>
        <div id="fetchUrlCompatNote" class="smallNote error" style="margin: 0; display: none;"></div>
        <span class="error" id="error_url"></span></td>
    </tr>
    <tr>
      <th><label for="push_url">Push URL:</label></th>
      <td><props:textProperty name="push_url" className="longField"/>
        <div class="smallNote" style="margin: 0;">It is used for pushing tags to the remote repository.
          If blank, the fetch url is used.
        </div>
        <div id="pushUrlCompatNote" class="smallNote error" style="margin: 0; display: none;"></div>
        <span class="error" id="error_push_url"></span>
      </td>
    </tr>
    <tr>
      <th><label for="branch">Default branch: <l:star/></label></th>
      <td>
        <props:textProperty name="branch" className="longField"/>
        <div class="smallNote" style="margin: 0">The main branch or tag to be monitored</div>
        <span class="error" id="error_branch"></span>
      </td>
    </tr>
    <bs:branchSpecTableRow/>
    <tr class="advancedSetting">
      <th><label for="reportTagRevisions">Use tags as branches:</label></th>
      <td>
        <props:checkboxProperty name="reportTagRevisions"/>
        <label for="reportTagRevisions">Enable to use tags in the branch specification</label>
      </td>
    </tr>
    <tr class="advancedSetting">
      <th><label for="usernameStyle">Username style:</label></th>
      <td><props:selectProperty name="usernameStyle" enableFilter="true" className="mediumField">
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
      <td><props:selectProperty name="submoduleCheckout" enableFilter="true" className="mediumField">
        <props:option value="IGNORE">Ignore</props:option>
        <props:option value="CHECKOUT">Checkout</props:option>
      </props:selectProperty>
        <div class="smallNote" style="margin: 0">
          Defines whether to checkout submodules
        </div>
      </td>
    </tr>
    <tr class="advancedSetting">
      <th><label for="userForTags">Username for tags/merge:</label></th>
      <td><props:textProperty name="userForTags" className="longField"/>
        <div class="smallNote" style="margin: 0">Format: Username &lt;email&gt;</div>
      </td>
    </tr>
  </l:settingsGroup>
  <l:settingsGroup title="Authentication Settings">
    <tr>
      <th><label for="authMethod">Authentication method:</label></th>
      <td>
        <props:selectProperty name="authMethod" onchange="gitSelectAuthentication(true)" enableFilter="true" className="mediumField">
          <props:option value="ANONYMOUS">Anonymous</props:option>
          <props:option value="PASSWORD">Password</props:option>
          <optgroup label="Private Key">
            <c:if test="${teamcitySshKeysEnabled}">
              <props:option value="TEAMCITY_SSH_KEY">Uploaded Key</props:option>
            </c:if>
            <props:option value="PRIVATE_KEY_DEFAULT">Default Private Key</props:option>
            <props:option value="PRIVATE_KEY_FILE">Custom Private Key</props:option>
          </optgroup>
        </props:selectProperty>
        <div id="defaultPrivateKeyNote" class="smallNote auth defaultKey" style="margin: 0">Uses mapping specified in the file
          ${userHome} if that file exists.
        </div>
        <div id="authMethodCompatNote" class="smallNote" style="margin: 0; display: none;"></div>
      </td>
    </tr>
    <tr id="gitUsername" class="auth defaultKey customKey password uploadedKey">
      <th><label for="username">Username:</label></th>
      <td><props:textProperty name="username" className="longField"/>
        <div class="smallNote" style="margin: 0">
          Specify the username if there is no username in the clone URL. The username specified here overrides the username from the URL.
        </div>
      </td>
    </tr>
    <tr id="gitPasswordRow" class="auth password">
      <th><label for="secure:password">Password:</label></th>
      <td><props:passwordProperty name="secure:password" className="longField"/></td>
    </tr>
    <tr id="gitPrivateKeyRow" class="auth customKey">
      <th><label for="privateKeyPath">Private key path: <l:star/></label></th>
      <td><props:textProperty name="privateKeyPath" className="longField"/>
        <div class="smallNote" style="margin: 0;">
          Specify the path to the private key on the TeamCity server host.
        </div>
        <span class="error" id="error_privateKeyPath"></span>
      </td>
    </tr>
    <c:if test="${not empty vcsPropertiesBean.belongsToProject}">
      <c:set var="projectId" value="${vcsPropertiesBean.belongsToProject.externalId}" scope="request"/>
    </c:if>
    <tr id="gitTeamCityKeyRow" class="auth uploadedKey">
      <th><label for="teamcitySshKey">Uploaded Key: <l:star/></label></th>
      <td>
        <admin:sshKeys projectId="${projectId}" keySelectionCallback="uploadedKeySelected"/>
        <span class="error" id="error_teamcitySshKey"></span>
      </td>
    </tr>
    <tr id="gitPassphraseRow" class="auth customKey">
      <th><label for="secure:passphrase">Passphrase:</label></th>
      <td><props:passwordProperty name="secure:passphrase" className="longField"/></td>
    </tr>
    <c:choose>
      <c:when test="${showKnownHostsDbOption or not vcsPropertiesBean.propertiesBean.properties['ignoreKnownHosts']}">
        <tr id="gitKnownHosts" class="advancedSetting">
          <div class="auth defaultKey customKey uploadedKey">
            <th><label for="ignoreKnownHosts">Ignore known hosts database:</label></th>
            <td><props:checkboxProperty name="ignoreKnownHosts"/>
              <c:out value="${vcsPropertiesBean.propertiesBean.properties['ignoreKnownHosts']}"/>
            </td>
          </div>
        </tr>
      </c:when>
      <c:otherwise>
        <props:hiddenProperty name="ignoreKnownHosts" value="true"/>
      </c:otherwise>
    </c:choose>
  </l:settingsGroup>
  <l:settingsGroup title="Server Settings" className="advancedSetting">
    <tr class="advancedSetting">
      <td colspan="2">Settings that are used in case of server-side checkout.</td>
    </tr>
    <tr class="advancedSetting">
      <th>
        <label for="serverSideAutoCrlf">Convert line-endings to CRLF:<bs:help file="Git" anchor="serverAutoCRLF"/></label>
      </th>
      <td>
        <props:checkboxProperty name="serverSideAutoCrlf"/>
      </td>
    </tr>
    <c:if test="${showCustomClonePath}">
      <tr class="advancedSetting">
        <th><label for="path">Custom clone directory on server:<bs:help file="Git" anchor="customCloneDir"/></label></th>
        <td><props:textProperty name="path" className="longField"/>
          <div class="smallNote" style="margin: 0;">
            A directory on the TeamCity server where a bare cloned repository is to be created. Leave blank to use the default path.
          </div>
        </td>
      </tr>
    </c:if>
  </l:settingsGroup>
  <l:settingsGroup title="Agent Settings" className="advancedSetting">
    <tr class="advancedSetting">
      <td colspan="2">Agent-specific settings that are used in case of agent checkout.<bs:help file="Git" anchor="agentSettings"/></td>
    </tr>
    <tr class="advancedSetting">
      <th><label for="agentGitPath">Path to Git: </label></th>
      <td><props:textProperty name="agentGitPath" className="longField"/>
        <div class="smallNote" style="margin: 0;">
          The path to a git executable on the agent. If blank, the location set up in ${gitPathEnv} environment variable is used.
        </div>
      </td>
    </tr>
    <tr class="advancedSetting">
      <th><label for="agentCleanPolicy">Clean policy:</label></th>
      <td><props:selectProperty name="agentCleanPolicy" enableFilter="true" className="mediumField">
        <props:option value="ON_BRANCH_CHANGE">On Branch Change</props:option>
        <props:option value="ALWAYS">Always</props:option>
        <props:option value="NEVER">Never</props:option>
      </props:selectProperty>
        <div class="smallNote" style="margin: 0">
          This option specifies when the "git clean" command is run on the agent.
        </div>
      </td>
    </tr>
    <tr class="advancedSetting">
      <th><label for="agentCleanFilesPolicy">Clean files policy:</label></th>
      <td><props:selectProperty name="agentCleanFilesPolicy" enableFilter="true" className="mediumField">
        <props:option value="ALL_UNTRACKED">All untracked files</props:option>
        <props:option value="IGNORED_ONLY">All ignored untracked files</props:option>
        <props:option value="NON_IGNORED_ONLY">All non-ignored untracked files</props:option>
      </props:selectProperty>
        <div class="smallNote" style="margin: 0">This option specifies which files will be removed when "git
          clean" command is run on agent.
        </div>
      </td>
    </tr>
    <tr class="advancedSetting">
      <th><label for="useAlternates">Use mirrors:</label></th>
      <td>
        <props:checkboxProperty name="useAlternates"/>
        <div class="smallNote" style="margin: 0" >
          When this option is enabled, TeamCity creates a separate clone of the repository on each agent
          and uses it in the checkout directory via git alternates.
        </div>
      </td>
    </tr>
  </l:settingsGroup>
</table>
<script type="text/javascript">
  var Git = {
    compatibleAuthMethods: {//protocol -> compatible auth methods
      'git' : ['ANONYMOUS'],
      'http' : ['ANONYMOUS', 'PASSWORD'],
      'https' : ['ANONYMOUS', 'PASSWORD']
    },


    getProtocol: function($element) {
      var url = $element.val();
      if (url) {
        if (url.startsWith('git://')) {
          return 'git';
        } else if (url.startsWith('http://')) {
          return 'http';
        } else if (url.startsWith('https://')) {
          return 'https';
        } else {
          return 'other';
        }
      } else {
        return null;
      }
    },


    getAuthMethodName: function(authMethod) {
      return $j("#authMethod option[value=" + authMethod + "]").text();
    },


    getCompatibleMethods: function(proto) {
      if (!proto) {
        return this.getAllAuthMethods();
      } else {
        var compat = this.compatibleAuthMethods[proto];
        if (compat) {
          return compat;
        } else {
          //no constraints for given protocol
          return this.getAllAuthMethods();
        }
      }
    },


    getAllAuthMethods: function() {
      var result = [];
      $j('#authMethod option').each(function() {
        result.push($j(this).val());
      });
      return result;
    },


    contains: function (arr, elem) {
      return arr.indexOf(elem) != -1;
    },


    disableIncompatible: function (fetchCompatible, pushCompatible, selected) {
      var that = this;
      $j('#authMethod option').each(function() {
        var method = $j(this).val();
        if (method == selected || that.contains(fetchCompatible, method) && that.contains(pushCompatible, method)) {
          $j(this).attr("disabled", false);
        } else {
          $j(this).attr("disabled", "disabled");
        }
      });
    },


    getLimitingProtocols: function (fetchProto, fetchCompatMethods, pushProto, pushCompatMethods) {
      var allMethods = this.getAllAuthMethods();
      var limitingProtocols = [];
      if (fetchCompatMethods.length < allMethods.length) {
        limitingProtocols.push("'" + fetchProto + "'");
      }
      if (pushCompatMethods.length < allMethods.length && pushProto != fetchProto) {
        limitingProtocols.push("'" + pushProto + "'");
      }
      var size = limitingProtocols.length;
      if (size == 0) {
        return null;
      } else {
        return (size == 1 ? "the " : "") + limitingProtocols.join(" and ") + " protocol" + (size > 1 ? "s" : "");
      }
    },


    applyAuthConstraints: function () {
      var selectedAuthMethod = $j('#authMethod').val();
      var authMethodName = this.getAuthMethodName(selectedAuthMethod);
      var fetchProto = this.getProtocol($j('#url'));
      var pushProto = this.getProtocol($j('#push_url'));

      var fetchCompatMethods = this.getCompatibleMethods(fetchProto);
      var pushCompatMethods = this.getCompatibleMethods(pushProto);
      var fetchCompatible = true;
      var pushCompatible = true;
      if (this.contains(fetchCompatMethods, selectedAuthMethod)) {
        $j('#fetchUrlCompatNote').hide();
      } else {
        fetchCompatible = false;
        $j('#fetchUrlCompatNote')
            .text("The '" + authMethodName + "' authentication method is incompatible with the '" + fetchProto + "' protocol")
            .show();
      }

      if (this.contains(pushCompatMethods, selectedAuthMethod)) {
        $j('#pushUrlCompatNote').hide();
      } else {
        pushCompatible = false;
        $j('#pushUrlCompatNote')
            .text("The '" + authMethodName + "' authentication method is incompatible with the '" + pushProto + "' protocol")
            .show();
      }

      this.disableIncompatible(fetchCompatMethods, pushCompatMethods, selectedAuthMethod);

      if (!fetchCompatible || !pushCompatible) {
        //incompatible method selected, show error
        var protocol = !fetchCompatible ? fetchProto : pushProto;
        var usage = !fetchCompatible ? "Fetch URL" : "Push URL";
        $j('#authMethodCompatNote')
            .text("Selected authentication method is incompatible with the '" + protocol + "' protocol (" + usage + ")")
            .addClass('error')
            .show();
      } else {
        var limitingProtocols = this.getLimitingProtocols(fetchProto, fetchCompatMethods, pushProto, pushCompatMethods);
        if (limitingProtocols) {
          //there are incompatible methods, show note
          $j('#authMethodCompatNote').text("Authentication methods incompatible with " + limitingProtocols + " are disabled")
              .removeClass('error')
              .show();
        } else {
          //all methods are compatible, hide note
          $j('#authMethodCompatNote').hide();
        }
      }

      //refresh ufd
      $j("#authMethod").ufd("changeOptions");
    }
  };

  $j('#url').keyup(function() {Git.applyAuthConstraints();});
  $j('#push_url').keyup(function() {Git.applyAuthConstraints();});

  gitSelectAuthentication = function(resetHiddenFields) {
    BS.Util.toggleDependentElements($('authMethod').value, 'auth', resetHiddenFields, {
      PRIVATE_KEY_DEFAULT : 'defaultKey',
      PRIVATE_KEY_FILE : 'customKey',
      PASSWORD : 'password',
      ANONYMOUS : 'anonymous',
      TEAMCITY_SSH_KEY : 'uploadedKey'
    });
    Git.applyAuthConstraints();
    BS.VisibilityHandlers.updateVisibility($('vcsRootProperties'));
  };
  gitSelectAuthentication(false);

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

  $j(document).ready(function() {
    if (BS.Repositories != null) {
      BS.Repositories.installControls($('url'), function (repoInfo, cre) {
        $('url').value = repoInfo.repositoryUrl;
        if (cre != null) {
          $('authMethod').value = 'PASSWORD';
          BS.jQueryDropdown($('authMethod')).ufd("changeOptions");
          $('username').value = cre.oauthLogin;
          gitSelectAuthentication(true);
        }
      });
    } else {
      $j('.listRepositoriesControls').hide();
    }
  });
</script>
