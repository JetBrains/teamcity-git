<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<tr>
  <th><label for="branch">Default branch:<l:star/></label></th>
  <td>
    <props:textProperty name="branch" className="longField"/>
    <div class="smallNote" style="margin: 0">The main branch or tag to be monitored</div>
    <span class="error" id="error_branch"></span>
  </td>
</tr>
<bs:branchSpecTableRow advancedSetting="${false}"/>
