<%@include file="/include.jsp" %>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean"/>
<table class="runnerFormTable">

  <l:settingsGroup title="General Settings">
    <tr>
      <th><label for="repositoryPath">Clone URL: <l:star/></label></th>
      <td><props:textProperty name="url" className="longField"/>
        <span class="error" id="error_repositoryURL"></span></td>
    </tr>
    <tr>
      <th><label for="branchName">Branch name: </label></th>
      <td><props:textProperty name="branch"/></td>
    </tr>
    <tr>
      <th><label for="serverClonePath">Clone repository to: </label></th>
      <td><props:textProperty name="path" className="longField"/>
        <div class="smallNote" style="margin: 0;">Provide path to a parent directory on TeamCity server where a
          cloned repository should be created. Leave blank to use default path.
        </div>
      </td>
    </tr>
  </l:settingsGroup>
  <l:settingsGroup title="Authorization settings">
    <tr>
      <td colspan="2">Authorization settings can be required if the repository is password protected.</td>
    </tr>
    <tr>
      <th><label for="username">User name:</label></th>
      <td><props:textProperty name="username"/></td>
    </tr>
    <tr>
      <th><label for="secure:password">Password:</label></th>
      <td><props:passwordProperty name="secure:password"/></td>
    </tr>
  </l:settingsGroup>

</table>
