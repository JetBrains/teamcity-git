<%@ include file="/include-internal.jsp" %>

<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<jsp:useBean id="healthStatusItem" type="jetbrains.buildServer.serverSide.healthStatus.HealthStatusItem" scope="request"/>

<c:set var="transports" value="${healthStatusItem.additionalData['transports']}"/>
<div>
  The 'teamcity.git.additionalAllowedUrlTransports' property allows
  <c:forEach var="transport" items="${transports}" varStatus="loopStatus">${loopStatus.first ? '' : ', '}'<c:out value="${transport}"/>'</c:forEach>,
  which can run arbitrary commands on the server host. Remove it unless this is intentional.
</div>
