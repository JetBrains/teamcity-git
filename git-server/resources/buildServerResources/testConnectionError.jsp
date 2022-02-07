<%@include file="/include.jsp" %>
<jsp:useBean id="testConnectionError" type="java.lang.String" scope="request"/>
<jsp:useBean id="affectedBuildTypes" type="java.util.Collection" scope="request"/>
<div class="testConnectionError">
  <c:out value="${testConnectionError}"/><br/><br/>Affected build configurations:
  <c:forEach items="${affectedBuildTypes}" var="bt">
    <br/><bs:buildTypeLink buildType="${bt}"/>
  </c:forEach>
</div>