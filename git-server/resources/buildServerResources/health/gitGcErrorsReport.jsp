<%@ page import="jetbrains.buildServer.web.openapi.healthStatus.HealthStatusItemDisplayMode" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="inplaceMode" value="<%=HealthStatusItemDisplayMode.IN_PLACE%>"/>
<jsp:useBean id="showMode" type="jetbrains.buildServer.web.openapi.healthStatus.HealthStatusItemDisplayMode" scope="request"/>
<c:set var="errorsBlockId" value="gitGcErrors_${showMode}"/>
<jsp:useBean id="errors" type="java.util.Map<java.lang.String, java.lang.String>" scope="request"/>

<div>
  Errors while running git garbage collection
  <c:if test="${showMode == inplaceMode}"><a href="javascript:;" onclick="$j('#${errorsBlockId}').toggle();">Show details &raquo;</a></c:if>
</div>
<div id="${errorsBlockId}" style="margin-left: 1em; display: ${showMode == inplaceMode ? 'none' : ''}">
  <c:forEach var="error" items="${errors}">
    <div>
      <b><c:out value="${error.key}"/></b> (clone dir <c:out value="${error.value.first}"/>): <c:out value="${error.value.second}"/>
    </div>
  </c:forEach>
</div>
