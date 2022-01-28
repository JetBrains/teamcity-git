<%@include file="/include.jsp" %>
<form id="nativeGitStatusForm" method="post" onsubmit="return BS.NativeGitStatusForm.submit()" style="margin-top: 0.5em;">
  <table class="runnerFormTable" style="width: 100%;">
    <tr class="groupingTitle">
      <td>Native Git Operations</td>
    </tr>
    <jsp:useBean id="isGitExecError" type="java.lang.Boolean" scope="request"/>
    <c:choose>
      <c:when test="${isGitExecError}">
        <jsp:useBean id="gitExecError" type="jetbrains.buildServer.vcs.VcsException" scope="request"/>
      </c:when>
      <c:otherwise>
        <tr>
          <td>
            <jsp:useBean id="gitExec" type="jetbrains.buildServer.buildTriggers.vcs.git.command.GitExec" scope="request"/>
            <div>Path to git executable: <strong><c:out value="${gitExec.path}"/></strong></div>
            <div>Git version: <strong><c:out value="${gitExec.version.toString()}"/></strong></div>
          </td>
        </tr>
      </c:otherwise>
    </c:choose>
    <jsp:useBean id="nativeGitOperationsEnabled" type="java.lang.Boolean" scope="request"/>
    <jsp:useBean id="nativeGitOperationsSupported" type="java.lang.Boolean" scope="request"/>
    <tr>
      <td>
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
            <forms:attentionComment>${warn}</forms:attentionComment>
          </c:if>
        </c:if>

        <div>
          <input class="btn" type="button" id="switchNativeGit" name="switchNativeGit" value="${nativeGitOperationsEnabled ? "Disable" : "Enable"}" onclick="BS.NativeGitStatusForm.submit(${!nativeGitOperationsEnabled});" style="float: right;"/>
          Native git operations: <strong id="switchNativeGitLabel">${nativeGitOperationsEnabled ? "enabled" : "disabled"}</strong>
        </div>
      </td>
    </tr>
<%--    <tr id="nativeGitVcsRoots" style="${nativeGitOperationsEnabled ? 'display:none' : 'display:block'}">--%>
<%--      <td>--%>
<%--        <div>--%>
<%--        </div>--%>
<%--      </td>--%>
<%--    </tr>--%>
  </table>
</form>
<script type="application/javascript">
  BS.NativeGitStatusForm = OO.extend(BS.AbstractWebForm, {
    formElement: function() {
      return $('nativeGitStatusForm');
    },

    submit: function() {
      var that = this;
      BS.FormSaver.save(this, '<c:url value="/admin/diagnostic/nativeGitStatus.html"/>', OO.extend(BS.SimpleListener, {
        onBeginSave: function(form) {
          form.disable();
        },

        onCompleteSave: function (form, responseXML, err, responseText) {
          that.enable();
          var enabled = "true" == responseXML.documentElement.getElementsByTagName("nativeGitOperationsEnabled").item(0).textContent;
          $j("#switchNativeGit").val(enabled ? "Disable" : "Enable");
          $j("#switchNativeGitLabel").text(enabled ? "enabled" : "disabled");
          if (enabled) {
            BS.Util.hide("nativeGitVcsRoots");
          } else {
            BS.Util.show("nativeGitVcsRoots");
          }
        }
      }));
      return false;
    }
  });
</script>
