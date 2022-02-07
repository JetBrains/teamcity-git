<%@include file="/include.jsp" %>
<%@ taglib prefix="admin" tagdir="/WEB-INF/tags/admin" %>
<jsp:useBean id="projectGitRoots" type="java.util.List" scope="request"/>
<c:set var="controllerUrl"><c:url value="/admin/diagnostic/nativeGitStatus.html"/></c:set>
<bs:refreshable containerId="testConnectionVcsRootsContainer" pageUrl="${controllerUrl}">
  <admin:vcsRootChooser chooserName="testConnectionVcsRoots" headerOption="-- Choose VCS roots to run Test Connection --" attachableRoots="${projectGitRoots}" onchange="return true;"/>
</bs:refreshable>