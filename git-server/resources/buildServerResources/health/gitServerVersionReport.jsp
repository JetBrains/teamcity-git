<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<jsp:useBean id="healthStatusItem" type="jetbrains.buildServer.serverSide.healthStatus.HealthStatusItem" scope="request"/>
<c:set var="gitExec" value="${healthStatusItem.additionalData['gitExec']}"/>

<div>
  <c:choose>
    <c:when test="${gitExec == null}">
      Running native git commands on TeamCity server-side is disabled, because no supported git executable was found.
    </c:when>
    <c:otherwise>
      Git executable <c:out value="${gitExec.path}"/> version <c:out value="${gitExec.version.toString()}"/> is not supported for running native commands on TeamCity server-side.
    </c:otherwise>
  </c:choose>
  To enable running native commands on server please install the latest git version.
</div>