<%@ include file="/include.jsp" %>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<jsp:useBean id="propertiesBean" type="jetbrains.buildServer.controllers.BasePropertiesBean" scope="request"/>

<tr>
  <td colspan="2">
    <em>Trigger will add personal build to the queue if VCS check-in in branch is detected.</em>
  </td>
</tr>
<tr>
  <th><label for="pattern">Personal branch pattern:</label></th>
  <td><props:textProperty name="pattern" className="longField"/></td>
</tr>
