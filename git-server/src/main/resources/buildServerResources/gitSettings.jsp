

<%@ page import="java.io.File" %>
<%@ page import="jetbrains.buildServer.buildTriggers.vcs.git.Constants" %>
<%@ page import="jetbrains.buildServer.buildTriggers.vcs.git.PluginConfigImpl" %>
<%@ page import="jetbrains.buildServer.serverSide.TeamCityProperties" %>
<%@ page import="jetbrains.buildServer.util.StringUtil" %>
<%@include file="/include.jsp" %>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="admin" tagdir="/WEB-INF/tags/admin" %>
<%@ taglib prefix="oauth" tagdir="/WEB-INF/tags/oauth" %>

<jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean"/>
<jsp:useBean id="project" scope="request" type="jetbrains.buildServer.serverSide.SProject"/> <%-- may be null, should be deleted with TW-79195 --%>
<c:set var="gitPathEnv" value="<%= Constants.TEAMCITY_AGENT_GIT_PATH %>"/>
<c:set var="teamcitySshKeysEnabled" value="<%= PluginConfigImpl.isTeamcitySshKeysEnabled() %>"/>
<c:set var="showKnownHostsDbOption" value="<%= PluginConfigImpl.showKnownHostsDbOption() %>"/>
<c:set var="showCustomClonePath" value="<%= TeamCityProperties.getBoolean(Constants.CUSTOM_CLONE_PATH_ENABLED) &&
                                            (TeamCityProperties.getBoolean(Constants.SHOW_CUSTOM_CLONE_PATH)
                                            || !StringUtil.isEmpty(propertiesBean.getProperties().get(Constants.PATH))) %>"/>
<c:set var="placeholderNonPersonalToken" value="common usage"/>
<%--@elvariable id="parentProject" type="jetbrains.buildServer.serverSide.SProject"--%>
<c:set var="parentReadOnly" value="${not empty parentProject and parentProject.readOnly}"/>
<c:set var="readOnly" value="${vcsPropertiesBean.readOnly}"/>
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
      <td><props:textProperty name="url" className="longField" disabled="${parentReadOnly}"/>
        <c:if test="${not parentReadOnly}">
          <jsp:include page="/admin/repositoryControls.html?projectId=${parentProject.externalId}&vcsType=git"/>
        </c:if>
        <div class="smallNote" style="margin: 0;">Used for fetching data from the repository.</div>
        <div id="fetchUrlCompatNote" class="smallNote error" style="margin: 0; display: none;"></div>
        <span class="error" id="error_url"></span></td>
    </tr>
    <tr class="advancedSetting">
      <th><label for="push_url">Push URL:</label></th>
      <td><props:textProperty name="push_url" className="longField" disabled="${parentReadOnly}"/>
        <div class="smallNote" style="margin: 0;">Used for pushing tags to the remote repository.
          If blank, the fetch url is used.
        </div>
        <div id="pushUrlCompatNote" class="smallNote error" style="margin: 0; display: none;"></div>
        <span class="error" id="error_push_url"></span>
      </td>
    </tr>
    <tr>
      <th><label for="branch">Default branch: <l:star/></label></th>
      <td>
        <props:textProperty name="branch" className="longField" disabled="${parentReadOnly}"/>
        <div class="smallNote" style="margin: 0">The main branch or tag to be monitored</div>
        <span class="error" id="error_branch"></span>
      </td>
    </tr>
    <bs:branchSpecTableRow advancedSetting="${false}"/>
    <tr class="advancedSetting">
      <th><label for="reportTagRevisions">Use tags as branches:</label></th>
      <td>
        <props:checkboxProperty name="reportTagRevisions" disabled="${parentReadOnly}"/>
        <label for="reportTagRevisions">Enable to use tags in the branch specification</label>
      </td>
    </tr>
    <tr class="advancedSetting">
      <th><label for="usernameStyle">Username style:</label></th>
      <td><props:selectProperty name="usernameStyle" enableFilter="true" className="mediumField" disabled="${parentReadOnly}">
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
      <td><props:selectProperty name="submoduleCheckout" enableFilter="true" className="mediumField" disabled="${parentReadOnly}">
        <props:option value="IGNORE">Ignore</props:option>
        <props:option value="CHECKOUT">Checkout</props:option>
        <c:set var="nonRecursiveSubmodulesEnabled" value="<%= TeamCityProperties.getBooleanOrTrue(Constants.NON_RECURSIVE_SUBMODULES_ENABLE) %>"/>
        <c:if test="${nonRecursiveSubmodulesEnabled or propertiesBean.properties['submoduleCheckout'].equals('NON_RECURSIVE_CHECKOUT')}">
          <props:option value="NON_RECURSIVE_CHECKOUT">Non-recursive checkout</props:option>
        </c:if>
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
        <props:selectProperty name="authMethod" onchange="gitSelectAuthentication(true)" enableFilter="true" className="mediumField" disabled="${parentReadOnly}">
          <props:option value="ANONYMOUS">Anonymous</props:option>
          <props:option value="PASSWORD">Password / personal access token</props:option>
          <props:option value="ACCESS_TOKEN">Refreshable access token</props:option>
          <optgroup label="Private Key">
            <c:if test="${teamcitySshKeysEnabled}">
              <props:option value="TEAMCITY_SSH_KEY">Uploaded Key</props:option>
            </c:if>
            <props:option value="PRIVATE_KEY_DEFAULT">Default Private Key</props:option>
            <props:option value="PRIVATE_KEY_FILE">Custom Private Key</props:option>
          </optgroup>
        </props:selectProperty>
        <div id="defaultPrivateKeyNote" class="smallNote auth defaultKey" style="margin: 0">Uses mapping specified in the default location on the server or the agent if that file exists (server&apos;s file location is &quot;<c:out value="${userHome}"/>&quot;).  <%-- This exposes user under whom the server runs, so absolute path should probbaly only be present for sys admins --%>
        </div>
        <div id="authMethodCompatNote" class="smallNote" style="margin: 0; display: none;"></div>
        <span class="error" id="error_authMethod"></span>
        <div id="uploadedPrivateKeyNote" class="smallNote auth uploadedKey" style="margin: 0">
          Keys uploaded via UI in
          <c:choose>
            <c:when test="${not empty vcsPropertiesBean.belongsToProject}">
              <c:url var="sshKeysUrl" value="/admin/editProject.html?projectId=${vcsPropertiesBean.belongsToProject.externalId}&tab=ssh-manager"/>
              <a href="${sshKeysUrl}" target="_blank" rel="noreferrer">Project Settings > SSH Keys</a>.
            </c:when>
            <c:otherwise>
              Project Settings > SSH Keys.
            </c:otherwise>
          </c:choose>
          <bs:help urlPrefix="https://www.jetbrains.com/help/teamcity/" file="ssh-keys-management.html"/>
        </div>
        <div id="customKeyNote" class="smallNote auth customKey" style="margin: 0">
          Custom keys work only with server-side checkout. <bs:help urlPrefix="https://www.jetbrains.com/help/teamcity/" file="ssh-keys-management.html#SSH+Key+Usage"/>
        </div>
      </td>
    </tr>
    <tr id="gitUsername" class="auth defaultKey customKey password uploadedKey access_token">
      <th><label for="username">Username:</label></th>
      <td><props:textProperty name="username" className="longField" disabled="${parentReadOnly}"/>
        <div class="smallNote" style="margin: 0">
          Specify the username if there is no username in the clone URL. The username specified here overrides the username from the URL.
        </div>
        <props:hiddenProperty name="oauthProviderId" />
        <props:hiddenProperty name="tokenType" />
        <props:hiddenProperty name="tokenId" />
      </td>
    </tr>
    <tr id="gitConnectionNameRow" class="auth access_token">
      <th>Token:</th>
      <td>
        <div id="tokenIssuedInfo">

          <c:set var="canObtainTokens" value="${(not empty vcsPropertiesBean.belongsToProject) and
                              (not empty vcsPropertiesBean.connection) and
                              (vcsPropertiesBean.connection.acquiringTokenSupported) and
                              (not empty vcsPropertiesBean.originalVcsRoot) and
                              afn:canEditVcsRoot(vcsPropertiesBean.originalVcsRoot) and
                              not parentReadOnly}"/>

          <c:set var="canObtainTokensForNewRoot" value="${empty vcsPropertiesBean.originalVcsRoot}"/>

          <oauth:includeTokenControls>
            <jsp:attribute name="beforeInclude">
              <script type="text/javascript">
                BS.TokenControlParams = {
                  projectId: '${parentProject.externalId}',
                  tokenCallback: function (it) {
                    setAcquiredToken(it);
                  },
                  tokenIntent: 'REPO_FULL',
                  readOnly: ${readOnly or parentReadOnly},
                  tokenIdElement: $('tokenId'),
                  noGenerateButton: !${canObtainTokens || canObtainTokensForNewRoot}
                };
              </script>
            </jsp:attribute>

            <jsp:attribute name="ifDidInclude">
              <oauth:tokenObtainer shiftX="0" shiftY="-80"/>
            </jsp:attribute>

            <jsp:attribute name="ifDidNotInclude">
              <%@include file="_fallbackTokenInfo.jspf"%>
            </jsp:attribute>
          </oauth:includeTokenControls>
        </div>
        <span class="error" id="error_tokenId"></span>
      </td>
    </tr>
    <tr id="gitPasswordRow" class="auth password">
      <th><label for="secure:password">Password / access token:</label></th>
      <td><props:passwordProperty name="secure:password" className="longField" disabled="${parentReadOnly}"/></td>
    </tr>
    <tr id="gitPrivateKeyRow" class="auth customKey">
      <th><label for="privateKeyPath">Private key path: <l:star/></label></th>
      <td><props:textProperty name="privateKeyPath" className="longField" disabled="${parentReadOnly}"/>
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
      <td><props:passwordProperty name="secure:passphrase" className="longField" disabled="${parentReadOnly}"/></td>
    </tr>
    <c:choose>
      <c:when test="${showKnownHostsDbOption or not vcsPropertiesBean.propertiesBean.properties['ignoreKnownHosts']}">
        <tr id="gitKnownHosts" class="advancedSetting">
          <div class="auth defaultKey customKey uploadedKey">
            <th><label for="ignoreKnownHosts">Ignore known hosts database:</label></th>
            <td><props:checkboxProperty name="ignoreKnownHosts" disabled="${parentReadOnly}"/>
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
        <props:checkboxProperty name="serverSideAutoCrlf" disabled="${parentReadOnly}"/>
      </td>
    </tr>
    <c:if test="${showCustomClonePath}">
      <tr class="advancedSetting">
        <th><label for="path">Custom clone directory on server:<bs:help file="Git" anchor="customCloneDir"/></label></th>
        <td><props:textProperty name="path" className="longField" disabled="${parentReadOnly}"/>
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
      <td><props:textProperty name="agentGitPath" className="longField" disabled="${parentReadOnly}"/>
        <div class="smallNote" style="margin: 0;">
          The path to a git executable on the agent. If blank, the location set up in ${gitPathEnv} environment variable is used.
        </div>
      </td>
    </tr>
    <tr class="advancedSetting">
      <th><label for="useAlternates">Checkout policy:</label></th>
      <td>
        <c:set var="checkoutPolicyProp" value="${vcsPropertiesBean.propertiesBean.properties['useAlternates']}"/>
        <c:set var="checkoutPolicy" value="${checkoutPolicyProp == null ? null : fn:toUpperCase(checkoutPolicyProp)}"/>

        <props:selectProperty name="useAlternates" enableFilter="true" className="mediumField" onchange="updateCheckoutTypeNote()" disabled="${parentReadOnly}">
          <props:option id="autoCheckoutType" value="AUTO"  selected="${'AUTO' eq checkoutPolicy}">Auto</props:option>
          <props:option id="useMirrorsCheckoutType" value="USE_MIRRORS" selected="${'TRUE' eq checkoutPolicy || 'USE_MIRRORS' eq checkoutPolicy}">Use mirrors</props:option>
          <props:option id="noMirrorsCheckoutType" value="NO_MIRRORS" selected="${empty checkoutPolicy || 'FALSE' eq checkoutPolicy || 'NO_MIRRORS' eq checkoutPolicy}">Do not use mirrors</props:option>
          <props:option id="shallowCloneCheckoutType" value="SHALLOW_CLONE" selected="${'SHALLOW_CLONE' eq checkoutPolicy}">Shallow clone</props:option>
        </props:selectProperty>
        <div id="autoNote" class="smallNote checkoutTypeNote" style="margin: 0; display: none;">Uses shallow clone for short-lived agents and mirrors for regular long-lived agents.</div>
        <div id="useMirrorsNote" class="smallNote checkoutTypeNote" style="margin: 0; display: none;">Creates repository mirror on the agent machine and shares it between different builds with the same fetch URL. Most optimal approach for large repositories and long-lived agents.</div>
        <div id="noMirrorsNote" class="smallNote checkoutTypeNote" style="margin: 0; display: none;">Performs checkout right into the checkout directory without creating a mirror. Less optimal in terms of disk usage than mirrors.</div>
        <div id="shallowCloneNote" class="smallNote checkoutTypeNote"  style="margin: 0; display: none;">Uses git shallow clone to checkout build revision (--depth 1). Ideal for short-lived agents.</div>
      </td>
    </tr>
    <tr class="advancedSetting">
      <th><label for="agentCleanPolicy">Clean policy:</label></th>
      <td><props:selectProperty name="agentCleanPolicy" enableFilter="true" className="mediumField" disabled="${parentReadOnly}">
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
      <td><props:selectProperty name="agentCleanFilesPolicy" enableFilter="true" className="mediumField" disabled="${parentReadOnly}">
        <props:option value="ALL_UNTRACKED">All untracked files</props:option>
        <props:option value="IGNORED_ONLY">All ignored untracked files</props:option>
        <props:option value="NON_IGNORED_ONLY">All non-ignored untracked files</props:option>
      </props:selectProperty>
        <div class="smallNote" style="margin: 0">This option specifies which files will be removed when "git
          clean" command is run on agent.
        </div>
      </td>
    </tr>
    <%-- workaround for TW-65850--%>
    <props:hiddenProperty name="sshSendEnvRequestToken"/>
  </l:settingsGroup>
</table>
<script type="text/javascript">
  var Git = {
    compatibleAuthMethods: {//protocol -> compatible auth methods
      'git' : ['ANONYMOUS'],
      'http' : ['ANONYMOUS', 'PASSWORD', 'ACCESS_TOKEN'],
      'https' : ['ANONYMOUS', 'PASSWORD', 'ACCESS_TOKEN'],
      'ssh' : ['PRIVATE_KEY_DEFAULT', 'PRIVATE_KEY_FILE', "TEAMCITY_SSH_KEY"]
    },

    detachedAuthOptions: new Set(),

    getProtocol: function($element) {
      var url = $element.val();
      if (url) {
        if (url.startsWith('git://')) {
          return 'git';
        } else if (url.startsWith('http://')) {
          return 'http';
        } else if (url.startsWith('https://')) {
          return 'https';
        } else if (url.startsWith('ssh://')) {
          return 'ssh'
        } else {
          var index1 = url.indexOf('@');
          var index2 = url.indexOf(':');
          if (index1 > 0 && index2 > index1 + 1 && index2 < url.length - 1) { // user@server:project.git
            return 'ssh';
          }
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
        if (that.isHiddenIfNotSelected(method)) {
          if (method != selected) {
            that.detachedAuthOptions.add($j(this).detach());
          } else {
            that.attachAllAuthOptions();
          }
        }
      });
    },

    attachAllAuthOptions: function() {
      if (this.detachedAuthOptions.size > 0) {
        var select = $j('#authMethod');
        this.detachedAuthOptions.forEach(function(option) {
          select.append(option);
        });
        this.detachedAuthOptions.clear();
      }
    },

    isHiddenIfNotSelected: function(method) {
      if (BS.TokenControlParams) {
        // if token controls are installed, it's safe to allow token authentication from the get go
        return false;
      }
      return method == "ACCESS_TOKEN";
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
      $j('#error_url').text("");
      $j('#error_push_url').text("");
      $j('#error_authMethod').text("");

      var selectedAuthMethod = $('authMethod').value;
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
          var compatibleMethods = fetchCompatMethods.filter(fm => pushCompatMethods.includes(fm));
          $j('#authMethodCompatNote').text("Authentication methods " +
                                            Object.keys(authTypesForPrint).filter(t => !compatibleMethods.includes(t)).map(x => authTypesForPrint[x]).join(', ') +
                                            " are incompatible with " + limitingProtocols + " (set in Fetch URL/Push URL). These method are disabled.")
                                     .removeClass('error')
                                     .show();
        } else {
          //all methods are compatible, hide note
          $j('#authMethodCompatNote').hide();
        }
      }

      //refresh ufd
      $j("#authMethod").ufd("changeOptions");

      const fetchUrl = $j('#url').val();
      if (fetchUrl && fetchUrl.length > 0) {
        $j('.acquireNewTokenBtn').show();
      } else {
        $j('.acquireNewTokenBtn').hide();
      }

      if (selectedAuthMethod !== 'ACCESS_TOKEN') {
        $('tokenId').value = null;
      }
    }
  };

  calculateTokenIdFragment = function(tokenId) {
    if (tokenId) {
      var fragmentStart = tokenId.lastIndexOf(":") + 1;
      return tokenId.substring(fragmentStart, fragmentStart + 7);
    }
    return "";
  };

  $j('#url').keyup(function() {Git.applyAuthConstraints();});
  $j('#push_url').keyup(function() {Git.applyAuthConstraints();});
  if ($('issuedTokenId')) {
    $('issuedTokenId').title = "tokenId: " + calculateTokenIdFragment("${vcsPropertiesBean.tokenId}");
  }

  var authTypesForPrint = {
    ANONYMOUS : 'Anonymous',
    PASSWORD : 'Password / access token',
    TEAMCITY_SSH_KEY : 'Uploaded Key',
    PRIVATE_KEY_DEFAULT : 'Default Private Key',
    PRIVATE_KEY_FILE : 'Custom Private Key',
  };

  var authTypes = {
    PRIVATE_KEY_DEFAULT : 'defaultKey',
    PRIVATE_KEY_FILE : 'customKey',
    PASSWORD : 'password',
    ACCESS_TOKEN: 'access_token',
    ANONYMOUS : 'anonymous',
    TEAMCITY_SSH_KEY : 'uploadedKey'
  };


  gitSelectAuthentication = function(resetHiddenFields) {
    BS.Util.toggleDependentElements($('authMethod').value, 'auth', resetHiddenFields, authTypes);
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

  updateCheckoutTypeNote = function() {
    $j('.checkoutTypeNote').hide();

    var selectedId = $j('#useAlternates option:selected').attr('id');
    var noteId = selectedId.replace('CheckoutType', 'Note');
    $j('#' + noteId).show();
  };

  setAcquiredToken = function(it) {
    const idElement = $('tokenId');
    const tokenChanged = idElement && it.tokenId !== idElement.value;

    gitSelectAuthentication(true);

    if (!BS.TokenControlParams) {
      setAcquiredTokenDisplay(it, tokenChanged);
    }

    if (tokenChanged) {
      BS.VcsSettingsForm.setModified(true);
      BS.jQueryDropdown($('authMethod')).ufd("changeOptions");
      if (it.oauthLogin) {
        $('username').value = it.oauthLogin;
      }

      if (it.oauthProviderId) {
        $('oauthProviderId').value = it.oauthProviderId;
      }

      if (it.tokenType) {
        $('tokenType').value = it.tokenType;
      }

      idElement.value = it.tokenId;
    }

    if (it.tokenId) {
      $('error_tokenId').hide();
    }
  };

  setAcquiredTokenDisplay = function (it, tokenChanged) {
    $('tokenIssuedInfo').show(0);
    $('tokenUnavailable').hide(0);
    $('error_issuedToken').hide(0);
    $j('error_issuedToken').html('');
    $('token_additional_info').hide(0);
    $j('token_additional_info').html('');

    if (it.hasOwnProperty("warning")) {
      $j('#error_issuedToken').show(0);
      $j('#error_issuedToken').html("Warning: " + it["warning"]);
    }

    if (!tokenChanged) {
      $('token_additional_info').show();
      $('token_additional_info').innerHTML = "New token wasn't issued because the existing token is valid";
    } else {
      $('issuedForTitle').innerHTML = "Currently configured access token was issued ";
      if (it["teamcityUsername"] != null) {
        if (it["teamcityUsername"] === '${placeholderNonPersonalToken}') {
          $('issuedTokenUserName').innerHTML = '';
        } else {
          var teamcityName = it["teamcityName"] ? it["teamcityName"].escapeHTML() : null;
          $('issuedTokenUserName').innerHTML = it["teamcityUsername"].escapeHTML() + (teamcityName ? " (" + teamcityName + ")" : "");
        }
      } else {
        $('issuedTokenUserName').innerHTML = "undefined user";
      }
      $('issuedTokenId').title = "tokenId: " + calculateTokenIdFragment(it["tokenId"]);
      if (it.connectionDisplayName) {
        $('connectionDisplayName').innerHTML = it.connectionDisplayName.escapeHTML();
      }
    }
  };

  window.getRepositoryUrl = function () {
    return $('url').value;
  };

  illustrateUsernameStyle();

  $j("#usernameStyle").change(illustrateUsernameStyle);

  $j(document).ready(function() {
    if (BS.Repositories != null) {
      BS.Repositories.installControls($('url'), function (repoInfo, cre) {
        $('url').value = repoInfo.repositoryUrl;
        if (cre != null) {
          $('authMethod').value = 'PASSWORD';
          if (cre.permanentToken) {
            $('username').value = cre.oauthLogin;
            $('secure:password').value = '**************';
            $('oauthProviderId').value = cre.oauthProviderId;
          } else if (cre.tokenType == 'refreshable') {
            Git.attachAllAuthOptions();
            $('authMethod').value = 'ACCESS_TOKEN';
            setAcquiredToken(cre);
          } else if (cre.tokenType) {
            Git.attachAllAuthOptions();
            $('authMethod').value = 'ACCESS_TOKEN';
          }
          gitSelectAuthentication(true);
        }
      });
    } else {
      $j('.listRepositoriesControls').hide();
    }
    updateCheckoutTypeNote();
  });
</script>