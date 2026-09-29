<%@ include file="/include-internal.jsp" %>

<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<jsp:useBean id="healthStatusItem" type="jetbrains.buildServer.serverSide.healthStatus.HealthStatusItem" scope="request"/>
<jsp:useBean id="healthStatusReportUrl" type="java.lang.String" scope="request"/>

<c:set var="vcsRoot" value="${healthStatusItem.additionalData['vcsRoot']}"/>
<c:set var="url" value="${healthStatusItem.additionalData['url']}"/>
<c:set var="urlLabel" value="${healthStatusItem.additionalData['urlLabel']}"/>
<c:set var="transport" value="${healthStatusItem.additionalData['transport']}"/>
<c:set var="buildType" value="${healthStatusItem.additionalData['buildType']}"/>

<div>
  The VCS root <admin:vcsRootName vcsRoot="${vcsRoot}" editingScope="" cameFromUrl="${healthStatusReportUrl}"/> uses
  <c:choose>
    <c:when test="${not empty transport}">a non-standard '<c:out value="${transport}"/>' transport</c:when>
    <c:otherwise>an unrecognized URL format</c:otherwise>
  </c:choose>
  in its ${urlLabel} URL,
  <c:if test="${not empty buildType}">
    in the scope of <admin:editBuildTypeLinkFull buildType="${buildType}"/>,
  </c:if>
  which is not allowed for security reasons.<br/>
  URL: <code><c:out value="${url}"/></code>
</div>
