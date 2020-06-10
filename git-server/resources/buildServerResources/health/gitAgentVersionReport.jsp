<%@include file="/include-internal.jsp" %>
<%@ page import="jetbrains.buildServer.buildTriggers.vcs.git.GitVersion" %>
<%@ page import="jetbrains.buildServer.web.openapi.healthStatus.HealthStatusItemDisplayMode" %>
<c:set var="inplaceMode" value="<%=HealthStatusItemDisplayMode.IN_PLACE%>"/>
<jsp:useBean id="showMode" type="jetbrains.buildServer.web.openapi.healthStatus.HealthStatusItemDisplayMode" scope="request"/>
<jsp:useBean id="healthStatusItem" type="jetbrains.buildServer.serverSide.healthStatus.HealthStatusItem" scope="request"/>
<c:set var="gitVersionAgents" value="${healthStatusItem.additionalData['gitVersionAgents']}"/>
<c:set var="gitVersionAgentCount" value="${healthStatusItem.additionalData['gitVersionAgentCount']}"/>
<c:set var="gitVersionUnsupported" value="${healthStatusItem.additionalData['gitVersionUnsupported']}"/>

<c:choose>
  <c:when test="${gitVersionUnsupported}">
    Unsupported git executable version (less than ${GitVersion.MIN}) is installed on ${gitVersionAgentCount} build <bs:plural txt="agent" val="${gitVersionAgentCount}"/>.
  </c:when>
  <c:otherwise>
    Old git executable version (less than ${GitVersion.DEPRECATED}) is installed on ${gitVersionAgentCount} build <bs:plural txt="agent" val="${gitVersionAgentCount}"/>.
  </c:otherwise>
</c:choose>

<bs:helpLink file="Git" anchor="Gitexecutableontheagent">Update git executable on <bs:plural txt="agent" val="${gitVersionAgentCount}"/></bs:helpLink>.

<bs:agentsGroupedByPool agentsGroupedByPools="${gitVersionAgents}"
                        inplaceMode="${showMode == inplaceMode}"
                        hasSeveralPools="${healthStatusItem.additionalData['hasSeveralPools']}">
  <jsp:attribute name="agentListHeader">
    <bs:plural txt="Agent" val="${gitVersionAgentCount}"/> running old git executable:
  </jsp:attribute>
</bs:agentsGroupedByPool>