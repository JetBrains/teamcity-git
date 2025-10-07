<%@ include file="/include-internal.jsp" %>

<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<jsp:useBean id="healthStatusItem" type="jetbrains.buildServer.serverSide.healthStatus.HealthStatusItem" scope="request"/>
<jsp:useBean id="healthStatusReportUrl" type="java.lang.String" scope="request"/>

<c:set var="vcsRoot" value="${healthStatusItem.additionalData['vcsRoot']}"/>
<c:set var="url" value="${healthStatusItem.additionalData['url']}"/>
<c:set var="buildType" value="${healthStatusItem.additionalData['buildType']}"/>

<div>
  The VCS root <admin:vcsRootName vcsRoot="${vcsRoot}" editingScope="" cameFromUrl="${healthStatusReportUrl}"/> uses a local file URL,
  <c:if test="${not empty buildType}">
    in the scope of <admin:editBuildTypeLinkFull buildType="${buildType}"/>,
  </c:if>
  which is considered insecure and may be unsupported in future TeamCity versions.<br/>
  URL: <code><c:out value="${url}"/></code><br/>
  Please update the VCS root to use a network-accessible URL (for example, SSH or HTTPS).
</div>
