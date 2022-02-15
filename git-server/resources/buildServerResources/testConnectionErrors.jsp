<%@include file="/include.jsp" %>
<%@ taglib prefix="admin" tagdir="/WEB-INF/tags/admin" %>
<jsp:useBean id="testConnectionProject" type="jetbrains.buildServer.serverSide.SProject" scope="request"/>
<jsp:useBean id="testConnectionErrors" type="java.util.Map" scope="request"/>
<jsp:useBean id="testConnectionTimestamp" type="java.lang.String" scope="request"/>
<jsp:useBean id="pageUrl" scope="request" type="java.lang.String"/>
<div class="testConnectionErrors">
  <table class="runnerFormTable testConnectionErrorsTable fixedWidth" style="overflow: hidden">
    <tr class="groupingTitle">
      <td colspan="2">Native Git Test Connection Errors for <bs:projectLink project="${testConnectionProject}"/> project on ${testConnectionTimestamp}</td>
    </tr>
    <c:forEach items="${testConnectionErrors}" var="item">
      <c:forEach items="${item.value}" var="error">
        <tr>
          <th>Vcs Root:</th><td><admin:vcsRootName vcsRoot="${item.key}" editingScope="none" cameFromUrl="${pageUrl}"/><br/></td>
        </tr>
        <tr>
          <th>Affected build configurations:</th>
          <td>
            <c:forEach items="${error.affectedBuildTypes}" var="bt">
              <bs:buildTypeLink buildType="${bt}"/><br/>
            </c:forEach>
          </td>
        </tr>
        <tr><td colspan="2" class="testConnectionErrorMessageRow"><pre class="errorMessage"><c:out value="${error.cause.message}"/></pre></td></tr>
      </c:forEach>
    </c:forEach>
  </table>
</div>