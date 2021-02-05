<%@ page import="jetbrains.buildServer.web.openapi.healthStatus.HealthStatusItemDisplayMode" %>
<%@ include file="/include-internal.jsp" %>

<jsp:useBean id="healthStatusItem" type="jetbrains.buildServer.serverSide.healthStatus.HealthStatusItem" scope="request"/>
<jsp:useBean id="showMode" type="jetbrains.buildServer.web.openapi.healthStatus.HealthStatusItemDisplayMode" scope="request"/>
<jsp:useBean id="healthStatusReportUrl" type="java.lang.String" scope="request"/>

<c:set var="vcsRoot" value="${healthStatusItem.additionalData['vcsRoot']}"/>
<c:set var="project" value="${vcsRoot.project}"/>
<c:set var="inplaceMode" value="<%=HealthStatusItemDisplayMode.IN_PLACE%>"/>
<c:set var="projectLink"><admin:editProjectLink projectId="${project.externalId}" withoutLink="true"/></c:set>
<c:set var="returnUrl" value="${showMode eq inplaceMode ? projectLink : healthStatusReportUrl}"/>

<div>
  <admin:vcsRootName vcsRoot="${vcsRoot}" editingScope="" cameFromUrl="${returnUrl}"/> is using deprecated password authentication with guthub.com and will soon stop working <a
    href="https://github.blog/2020-12-15-token-authentication-requirements-for-git-operations"><bs:helpIcon/></a>
</div>
<c:if test="${afn:canEditVcsRoot(vcsRoot) and not project.readOnly}">
  <div>
    <admin:editVcsRootLink vcsRoot="${vcsRoot}" editingScope="" cameFromUrl="${returnUrl}">Edit VCS root</admin:editVcsRootLink>
  </div>
</c:if>
