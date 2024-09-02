<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<jsp:useBean id="healthStatusItem" type="jetbrains.buildServer.serverSide.healthStatus.HealthStatusItem" scope="request"/>
<c:set var="gitExec" value="${healthStatusItem.additionalData['gitExec']}"/>
<c:set var="reason" value="${healthStatusItem.additionalData['reason']}"/>

<div>
  <c:choose>
    <c:when test="${gitExec == null}">
      <c:out value="${reason}"/>
    </c:when>
    <c:otherwise>
      Git executable <c:out value="${gitExec.path}"/> version <c:out value="${gitExec.version.toString()}"/> is not supported for running native commands on TeamCity server-side.<br/>
      To enable running native commands on server please install the latest git version.
    </c:otherwise>
  </c:choose>
</div>