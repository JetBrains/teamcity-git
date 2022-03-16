<%@include file="/include.jsp" %>
<jsp:useBean id="testConnectionError" type="java.lang.String" scope="request"/>
<jsp:useBean id="affectedBuildTypes" type="java.util.Collection" scope="request"/>
<div class="testConnectionError">
  <pre><c:out value="${testConnectionError}"/></pre>Affected build configurations:
  <c:forEach items="${affectedBuildTypes}" var="bt">
    <br/><bs:buildTypeLink buildType="${bt}"/>
  </c:forEach>
</div>