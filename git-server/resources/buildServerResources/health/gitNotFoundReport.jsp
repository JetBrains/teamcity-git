<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<jsp:useBean id="healthStatusItem" type="jetbrains.buildServer.serverSide.healthStatus.HealthStatusItem" scope="request"/>
<c:set var="path" value="${healthStatusItem.additionalData['path']}"/>
<c:set var="error" value="${healthStatusItem.additionalData['error']}"/>
Error while running git at path '<c:out value="${path}"/>': <c:out value="${error.message}"/>