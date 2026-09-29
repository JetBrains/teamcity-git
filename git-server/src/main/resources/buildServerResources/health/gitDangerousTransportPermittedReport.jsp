<%@ include file="/include-internal.jsp" %>

<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<jsp:useBean id="healthStatusItem" type="jetbrains.buildServer.serverSide.healthStatus.HealthStatusItem" scope="request"/>

<c:set var="transports" value="${healthStatusItem.additionalData['transports']}"/>
<div>
  The 'teamcity.git.additionalAllowedUrlTransports' property currently permits:
  <ul>
    <c:forEach var="entry" items="${transports}">
      <li>'<c:out value="${entry.key}"/>' - <c:out value="${entry.value}"/></li>
    </c:forEach>
  </ul>
  Remove it unless this is intentional.
</div>
