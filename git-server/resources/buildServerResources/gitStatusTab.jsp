<%@include file="/include.jsp" %>
<%@ taglib prefix="admin" tagdir="/WEB-INF/tags/admin" %>
<bs:linkScript>
  /js/bs/bs.js
  /js/bs/testConnection.js
  /js/bs/pluginProperties.js
</bs:linkScript>
<style type="text/css">
  .runnerFormTable .longField {
    margin-bottom: 6px;
  }
</style>
<c:set var="controllerUrl"><c:url value="/admin/diagnostic/nativeGitStatus.html"/></c:set>
<form id="nativeGitStatusForm" method="post" onsubmit="return BS.NativeGitStatusForm.submit()" style="margin-top: 0.5em;">
  <table class="runnerFormTable" style="width: 100%;">
    <tr class="groupingTitle">
      <td colspan="2">Native Git Operations</td>
    </tr>
    <jsp:useBean id="isGitExecError" type="java.lang.Boolean" scope="request"/>
    <c:choose>
      <c:when test="${isGitExecError}">
        <jsp:useBean id="gitExecError" type="jetbrains.buildServer.vcs.VcsException" scope="request"/>
      </c:when>
      <c:otherwise>
        <jsp:useBean id="gitExec" type="jetbrains.buildServer.buildTriggers.vcs.git.command.GitExec" scope="request"/>
        <tr>
          <th><label for="gitExecPath">Path to git executable:</label></th>
          <td><div><c:out value="${gitExec.path}"/></div></td>
        </tr>
        <tr>
          <th><label for="gitExecVersion">Git version:</label></th>
          <td><div id="gitExecVersion"><c:out value="${gitExec.version.toString()}"/></div></td>
        </tr>
      </c:otherwise>
    </c:choose>
    <jsp:useBean id="nativeGitOperationsEnabled" type="java.lang.Boolean" scope="request"/>
    <jsp:useBean id="nativeGitOperationsSupported" type="java.lang.Boolean" scope="request"/>
    <c:if test="${nativeGitOperationsEnabled || isGitExecError}">
      <c:choose>
        <c:when test="${isGitExecError}"><c:set var="warn"><c:out value="${gitExecError.message}"/></c:set></c:when>
        <c:when test="${nativeGitOperationsSupported}"><c:set var="warn"/></c:when>
        <c:otherwise>
          <c:set var="warn">
            Git executable
            <c:out value="${gitExec.path}"/> version <strong><c:out value="${gitExec.version.toString()}"/></strong> is not supported for running native commands on TeamCity
            server-side.<br/>To enable running native commands on server please install the latest git version.
          </c:set>
        </c:otherwise>
      </c:choose>
      <c:if test="${not empty warn}">
        <tr class="noBorder"><td colspan="2"><forms:attentionComment>${warn}</forms:attentionComment></td></tr>
      </c:if>
    </c:if>
    <tr>
      <th><label for="switchNativeGitLabel">Native git operations:</label></th>
      <td><div>
          <input class="btn" type="button" id="switchNativeGit" name="switchNativeGit" value="${nativeGitOperationsEnabled ? "Disable" : "Enable"}" onclick="BS.NativeGitStatusForm.switchNativeGit();" style="float: right;"/>
          <span id="switchNativeGitLabel">${nativeGitOperationsEnabled ? "enabled" : "disabled"}</span>
        </div>
      </td>
    </tr>
    <c:if test="${!isGitExecError && nativeGitOperationsSupported}">
      <tr>
        <th><label for="vcsRootIds">Native Git Test Connection:</label></th>
        <td>
          <jsp:useBean id="projectsWithGitRoots" type="java.util.List" scope="request"/>
          <span style="float: right;">
            <forms:saving className="progressRingInline" id="saving"/>
           <input class="btn" type="button" id="nativeGitTestConnection" name="nativeGitTestConnection" value="Test Connection" onclick="BS.NativeGitStatusForm.testConnection();"/>
          </span>

          <bs:projectsFilter name="testConnectionProject" id="testConnectionProject" className="longField" defaultOption="true" projectBeans="${projectsWithGitRoots}" onchange="BS.NativeGitStatusForm.refreshProjectVcsRoots();"/>
          <span id="error_testConnectionProject" class="error"></span>
          <%@include file="vcsRootsContainer.jsp" %>
          <span id="error_testConnectionVcsRoots" class="error"></span>
        </td>
      </tr>
    </c:if>
  </table>
</form>
<bs:dialog dialogId="testConnectionDialog" dialogClass="vcsRootTestConnectionDialog" title="Test Connection" closeCommand="BS.TestConnectionDialog.close();"
           closeAttrs="showdiscardchangesmessage='false'">
  <div id="testConnectionStatus"></div>
  <div id="testConnectionDetails" class="mono"></div>
</bs:dialog>
<script type="application/javascript">
  BS.NativeGitStatusForm = OO.extend(BS.AbstractWebForm, {
    formElement: function() {
      return $('nativeGitStatusForm');
    },

    testConnection: function() {
      var that = this;
      BS.FormSaver.save(this, '${controllerUrl}', OO.extend(BS.SimpleListener, {
        onBeginSave: function(form) {
          that.clearErrors();
          that.setSaving(true);
          that.disable();
        },

        onCompleteSave: function (form, responseXML, err, responseText) {
          that.enable();
          that.setSaving(false);
          if (responseXML == null) {
            if (responseText.trim().startsWith('<div class="testConnectionError">')) {
              BS.TestConnectionDialog.show(false, responseText, $('nativeGitTestConnection'), true);
            }
            return;
          }
          var wereErrors = BS.XMLResponse.processErrors(responseXML, {}, BS.PluginPropertiesForm.propertiesErrorsHandler);
          if (wereErrors) {
            BS.ErrorsAwareListener.onCompleteSave(form, responseXML, false);
          } else {
            BS.TestConnectionDialog.show(true, '', $('nativeGitTestConnection'));
          }
        },

        // onFailedTestConnectionError: function(elem) {
        //   var text = "";
        //   if (elem.firstChild) {
        //     text = elem.firstChild.nodeValue;
        //   }
        //   alert(text);
        //   BS.TestConnectionDialog.show(false, text, $('nativeGitTestConnection'));
        // },

      }));
      return false;
    },

    switchNativeGit: function() {
      var that = this;
      BS.FormSaver.save(this, '${controllerUrl}?switch', OO.extend(BS.SimpleListener, {
        onBeginSave: function(form) {
          form.setSaving(true);
          form.disable();
        },

        onCompleteSave: function (form, responseXML, err, responseText) {
          var enabled = "true" == responseXML.documentElement.getElementsByTagName("nativeGitOperationsEnabled").item(0).textContent;
          $j("#switchNativeGit").val(enabled ? "Disable" : "Enable");
          $j("#switchNativeGitLabel").text(enabled ? "enabled" : "disabled");
          form.setSaving(false);
          that.enable();
        }
      }));
      return false;
    },

    refreshProjectVcsRoots: function() {
      $('testConnectionVcsRoots').disable();
      var selected = $('testConnectionProject').getValue();
      if (selected) {
        $('testConnectionVcsRootsContainer').refresh("saving", "selectedProject=" + selected, function () {
          $('testConnectionVcsRoots').enable();
        });
      }
    }
  });
  $j(document).ready(function() {
    if ($('testConnectionProject').getValue()) {
      $('testConnectionVcsRoots').enable();
    } else {
      $('testConnectionVcsRoots').disable();
    }
  });
</script>
