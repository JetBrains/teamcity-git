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
  .runnerFormTable .smallNote {
    max-width: none;
  }

  .testConnectionErrorsTable {
    margin-top: 1em;
  }

  .testConnectionErrorsTable th,
  .testConnectionErrorsTable td {
    border-top: none;
  }

  .testConnectionErrorsTable td.testConnectionErrorMessageRow {
    border-bottom: 1px solid #dfe5eb;
  }

  .testConnectionErrorsTable tr:last-child td.testConnectionErrorMessageRow{
    border-bottom: none;
  }

  .testConnectionErrorsTable pre.errorMessage {
    white-space: pre-wrap;
    white-space: -moz-pre-wrap;
    white-space: -pre-wrap;
    white-space: -o-pre-wrap;
    word-wrap: break-word;
    color: #a90f1a;
  }
</style>
<c:set var="controllerUrl"><c:url value="/admin/diagnostic/nativeGitStatus.html"/></c:set>
<jsp:useBean id="isMultinodeSetup" type="java.lang.Boolean" scope="request"/>
<c:set var="enableText" value="${isMultinodeSetup ? 'Enable on all nodes' : 'Enable'}"/>
<c:set var="disableText" value="${isMultinodeSetup ? 'Disable on all nodes' : 'Disable'}"/>
<form id="nativeGitStatusForm" method="post" onsubmit="return BS.NativeGitStatusForm.submit()" style="margin-top: 0.5em;">
  <table class="runnerFormTable" style="width: 100%;">
    <tr class="groupingTitle">
      <td colspan="2">Native Git</td>
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
        <button class="btn" type="submit" id="switchNativeGit" name="switchNativeGit" value="${nativeGitOperationsEnabled ? "Disable" : "Enable"}" onclick="BS.NativeGitStatusForm.switchNativeGit();" style="float: right;">${nativeGitOperationsEnabled ? disableText : enableText}</button>
          <span id="switchNativeGitLabel">${nativeGitOperationsEnabled ? "enabled" : "disabled"}</span>
        </div>
      </td>
    </tr>
  </table>
  <c:if test="${!isGitExecError && nativeGitOperationsSupported}">
    <bs:_collapsibleBlock title="Native Git Test Connection" id="testConnection" collapsedByDefault="true" saveState="true">
      <table class="runnerFormTable" style="width: 100%;">
        <tr>
          <td colspan="2">
            <div class="smallNote" style="margin-left:0">TeamCity supports official native git versions 2.29+ installed per <a
                href="https://git-scm.com/book/en/v2/Getting-Started-Installing-Git">this guide</a> with latest OpenSSH as ssh client. <bs:help file=""/><br/>
              Before enabling this feature you can run Test Connection with native git to ensure your VCS roots will continue working with native git.<br/>
              Test Connection will not show existing VCS errors, only errors which will arise after enabling native git operations. It may be time-consuming if your server has many VCS roots.
            </div>
          </td>
        </tr>
        <tr>
          <th><label for="vcsRootIds">Native Git Test Connection:</label></th>
          <td>
            <jsp:useBean id="projectsWithGitRoots" type="java.util.List" scope="request"/>
            <span style="float: right;">
              <forms:saving className="progressRingInline" id="saving"/>
              <input class="btn" type="button" id="nativeGitTestConnection" name="nativeGitTestConnection" value="Test connection" onclick="BS.NativeGitStatusForm.testConnection();"/>
            </span>

            <bs:projectsFilter name="testConnectionProject" id="testConnectionProject" className="longField" defaultOption="true" projectBeans="${projectsWithGitRoots}"
                               onchange="BS.NativeGitStatusForm.refreshProjectVcsRoots();"/>
            <span id="error_testConnectionProject" class="error"></span>
            <%@include file="vcsRootsContainer.jsp" %>
            <span id="error_testConnectionVcsRoots" class="error"></span>
          </td>
        </tr>
      </table>
      <div id="testConnectionResults"/>
    </bs:_collapsibleBlock>
  </c:if>
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
          if (responseXML == null) {
            if (responseText.trim().startsWith('<div class="testConnectionError">')) {
              that.enable();
              that.setSaving(false);
              BS.TestConnectionDialog.show(false, responseText, $('nativeGitTestConnection'), true);
            } else if (responseText.trim().startsWith('<div class="testConnectionErrors">')) {
              $j('#testConnectionResults').html(responseText);
            }
            return;
          }
          that.enable();
          that.setSaving(false);
          var wereErrors = BS.XMLResponse.processErrors(responseXML, {}, BS.PluginPropertiesForm.propertiesErrorsHandler);
          if (wereErrors) {
            BS.ErrorsAwareListener.onCompleteSave(form, responseXML, false);
          } else {
            if ('_Root' == $('testConnectionProject').getValue() && 'ALL' == $('testConnectionVcsRoots').getValue()) {
              $j('#testConnectionResults').html('');
            }
            BS.TestConnectionDialog.show(true, '', $('nativeGitTestConnection'));
          }
        },
      }));
      return false;
    },

    stopTestConnection: function (projectExternalId) {
      var that = this;
      that.disable();
      that.setSaving(true);
      BS.ajaxRequest('${controllerUrl}', {
        parameters: {
          testConnectionProject: projectExternalId,
          testConnectionVcsRoots: 'ALL',
          stopTestConnection: true
        },
        onComplete: function(transport) {
          let res = transport.responseText.trim();
          if (res.startsWith('<div class="testConnectionErrors">')) {
            $j('#testConnectionResults').html(res);
          }
          that.enable();
          that.setSaving(false);
        }
      });
      return false;
    },

    loadExistingTestConnectionResults: function (projectExternalId, fromFile) {
      var that = this;
      that.disable();
      that.setSaving(true);
      BS.ajaxRequest('${controllerUrl}', {
        parameters: {
          testConnectionProject: projectExternalId,
          testConnectionVcsRoots: 'ALL',
          loadStored: fromFile
        },
        onComplete: function(transport) {
          let res = transport.responseText.trim();
          if (res.startsWith('<div class="testConnectionErrors">')) {
            $j('#testConnectionResults').html(res);
          } else {
            that.enable();
            that.setSaving(false);
          }
        }
      });
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
          $j("#switchNativeGit").html(enabled ? "${disableText}" : "${enableText}");
          $j("#switchNativeGitLabel").text(enabled ? "enabled" : "disabled");
          form.setSaving(false);
          that.enable();
        }
      }));
      return false;
    },

    refreshProjectVcsRoots: function() {
      var selected = $('testConnectionProject').getValue();
      if (selected) {
        var that = this;
        that.setSaving(true);
        that.disable();
        $('testConnectionVcsRootsContainer').refresh("saving", "selectedProject=" + selected, function () {
          that.setSaving(false);
          that.enable();
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
    BS.NativeGitStatusForm.loadExistingTestConnectionResults('_Root', true);
  });
</script>
