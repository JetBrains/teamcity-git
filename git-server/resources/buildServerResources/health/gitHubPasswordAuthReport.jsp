<%@ page import="jetbrains.buildServer.web.openapi.healthStatus.HealthStatusItemDisplayMode" %>
<%@ include file="/include-internal.jsp" %>

<jsp:useBean id="healthStatusItem" type="jetbrains.buildServer.serverSide.healthStatus.HealthStatusItem" scope="request"/>
<jsp:useBean id="showMode" type="jetbrains.buildServer.web.openapi.healthStatus.HealthStatusItemDisplayMode" scope="request"/>
<jsp:useBean id="healthStatusReportUrl" type="java.lang.String" scope="request"/>
<jsp:useBean id="isPasswordContainsReference" type="java.lang.Boolean" scope="request"/>

<c:set var="vcsRoot" value="${healthStatusItem.additionalData['vcsRoot']}"/>
<c:set var="project" value="${vcsRoot.project}"/>
<c:set var="inplaceMode" value="<%=HealthStatusItemDisplayMode.IN_PLACE%>"/>
<c:set var="globalMode" value="<%=HealthStatusItemDisplayMode.GLOBAL%>"/>
<c:set var="projectLink"><admin:editProjectLink projectId="${project.externalId}" withoutLink="true"/></c:set>
<c:set var="returnUrl" value="${showMode eq inplaceMode ? projectLink : healthStatusReportUrl}"/>

<div>
  <admin:vcsRootName vcsRoot="${vcsRoot}" editingScope="" cameFromUrl="${returnUrl}"/>
  <c:choose>
    <c:when test="${isPasswordContainsReference}">
      <b>"Password / access token"</b> setting contains parameter reference which has been recently resolved to a password. Password authentication with <b>github.com</b> has been <b>deprecated</b> and this usage will soon stop working
    </c:when>
    <c:otherwise> is using <b>deprecated</b> password authentication with <b>github.com</b> and will soon stop working</c:otherwise>
  </c:choose>
  &nbsp;<a href="https://github.blog/2020-12-15-token-authentication-requirements-for-git-operations"><bs:helpIcon/></a>
</div>
<div style="${showMode eq globalMode ? 'margin: 1em 0' : ''}">Please consider switching to either personal access token or to SSH private key authentication.</div>
<c:if test="${isPasswordContainsReference and afn:permissionGrantedForProject(project, 'EDIT_PROJECT') and afn:permissionGrantedForProject(project, 'VIEW_BUILD_CONFIGURATION_SETTINGS')}">
  <div>
    <admin:editProjectLink projectId="${project.externalId}" withoutLink="false" addToUrl="&tab=usagesReport&vcsRootId=${vcsRoot.externalId}">View VSC root usages</admin:editProjectLink>
  </div>
</c:if>
 <c:if test="${afn:canEditVcsRoot(vcsRoot) and not project.readOnly}">
  <div>
    <admin:editVcsRootLink vcsRoot="${vcsRoot}" editingScope="" cameFromUrl="${returnUrl}">Edit VCS root</admin:editVcsRootLink>
  </div>
</c:if>
