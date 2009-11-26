<%@ page import="java.io.File" %>
<%@include file="/include.jsp" %>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%--
  ~ Copyright 2000-2009 JetBrains s.r.o.
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
<table class="runnerFormTable">
  <c:set var="userHome"
         value='<%=new File(System.getProperty("user.home"), ".ssh"+File.separator+"config").getAbsolutePath() %>'/>
  <l:settingsGroup title="General Settings">
    <tr>
      <th><label for="url">Clone URL: <l:star/></label></th>
      <td><props:textProperty name="url" className="longField"/>
        <span class="error" id="error_url"></span></td>
    </tr>
    <tr>
      <th><label for="branch">Branch name: </label></th>
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
        <props:option value="EMAIL">Email (jsmith@example.org)</props:option>
        <props:option value="NAME">Name (John Smith)</props:option>
        <props:option value="FULL">Full (John Smith &lt;jsmith@example.org&gt;)</props:option>
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
  </l:settingsGroup>
  <l:settingsGroup title="Authentication settings">
    <tr>
      <td colspan="2">Authorization settings can be required if the repository is password protected.</td>
    </tr>
    <tr>
      <th><label for="authMethod">Authentication Method:</label></th>
      <td><props:selectProperty name="authMethod" onchange="gitSelectAuthentication()">
        <props:option value="ANONYMOUS">Anonymous</props:option>
        <props:option value="PRIVATE_KEY_DEFAULT">Default Private Key</props:option>
        <props:option value="PASSWORD">Password</props:option>
        <props:option value="PRIVATE_KEY_FILE">Private Key</props:option>
      </props:selectProperty>
        <div id="sshPrivateKeyNote" class="smallNote" style="margin: 0">Valid only for SSH protocol.</div>
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
      <th>Known Hosts Database:</th>
      <td><props:checkboxProperty name="ignoreKnownHosts"/>
        <label for="ignoreKnownHosts">Do not check</label></td>
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
  }
  gitSelectAuthentication();
  if ($('url').value == "") {
    $('submoduleCheckout').selectedIndex = 1;
  }
</script>
