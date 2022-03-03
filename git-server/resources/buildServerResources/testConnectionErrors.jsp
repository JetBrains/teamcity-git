<%@include file="/include.jsp" %>
<jsp:useBean id="testConnectionProject" type="jetbrains.buildServer.serverSide.SProject" scope="request"/>
<jsp:useBean id="testConnectionErrors" type="java.util.Map" scope="request"/>
<jsp:useBean id="testConnectionTimestamp" type="java.lang.String" scope="request"/>
<jsp:useBean id="testConnectionInProgress" type="java.lang.Boolean" scope="request"/>
<jsp:useBean id="testConnectionStopped" type="java.lang.Boolean" scope="request"/>
<jsp:useBean id="testConnectionStatus" type="java.lang.String" scope="request"/>
<jsp:useBean id="pageUrl" scope="request" type="java.lang.String"/>
<div class="testConnectionErrors">
  <table class="runnerFormTable testConnectionErrorsTable fixedWidth" style="overflow: hidden">
    <tr class="groupingTitle">
      <td colspan="2">Native Git Test Connection Errors for <bs:projectLink project="${testConnectionProject}"/> project started on ${testConnectionTimestamp}<span style="float: right; padding-right: 1em; cursor: pointer;"><c:if test="${testConnectionInProgress}"><a onclick="return BS.NativeGitStatusForm.stopTestConnection('${testConnectionProject.externalId}')">Stop</a></c:if></span></td>
    </tr>
    <tr><td colspan="2">
      <c:if test="${empty testConnectionStatus}">
        <c:if test="${testConnectionInProgress}"><forms:saving style="display:block" className="progressRingInline"/></c:if>
      </c:if>
      <c:if test="${not empty testConnectionStatus}">
        <span style="font-style: italic; color: #737577;">${testConnectionStatus}</span>
      </c:if>
    </td></tr>
    <c:forEach items="${testConnectionErrors}" var="item">
      <c:forEach items="${item.value}" var="error">
        <tr>
          <c:set value="${item.key}" var="vcsRoot"/>
          <c:url value="/admin/editVcsRoot.html?init=1&action=editVcsRoot&vcsRootId=${vcsRoot.externalId}" var="editVcsLink"/>
          <c:set var="rootName"><em>(${fn:replace(vcsRoot.vcsName, 'jetbrains.', '')})</em>  <c:out value="${vcsRoot.name}"/></c:set>
          <th>Vcs Root:</th><td><a href="${editVcsLink}">${rootName}</a><br/></td>
        </tr>
        <tr>
          <th>Affected build configurations:</th>
          <td>
            <c:forEach items="${error.affectedBuildTypes}" var="bt">
              <c:url value="/viewType.html?buildTypeId=${bt.externalId}" var="url"/>
              <a href="${url}" class="buildTypeName" rel="noreferrer"><c:out value="${bt.name}"/></a><br/>
            </c:forEach>
          </td>
        </tr>
        <tr><td colspan="2" class="testConnectionErrorMessageRow"><pre class="errorMessage"><c:out value="${error.message}"/></pre></td></tr>
      </c:forEach>
    </c:forEach>
  </table>
</div>
<script type="text/javascript">
  <c:choose>
    <c:when test="${testConnectionInProgress}">
        setTimeout(function() { BS.NativeGitStatusForm.loadExistingTestConnectionResults('${testConnectionProject.externalId}', false); }, 2000);
    </c:when>
    <c:otherwise>
      BS.NativeGitStatusForm.enable();
      BS.NativeGitStatusForm.setSaving(false);
  </c:otherwise>
  </c:choose>
</script>
