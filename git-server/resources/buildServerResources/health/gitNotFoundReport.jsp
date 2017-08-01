<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>
<jsp:useBean id="healthStatusItem" type="jetbrains.buildServer.serverSide.healthStatus.HealthStatusItem" scope="request"/>
<c:set var="path" value="${healthStatusItem.additionalData['path']}"/>
<c:set var="error" value="${healthStatusItem.additionalData['error']}"/>
<div>
  Cannot run git garbage collection using git at path '<c:out value="${path}"/>': <c:out value="${error.message}"/>.
  <br/>
  Please install a git client on the TeamCity server machine and specify a path to it in the <b>teamcity.server.git.executable.path</b>
  <bs:helpLink file="Configuring+TeamCity+Server+Startup+Properties" anchor="TeamCityinternalproperties">internal property</bs:helpLink>
  <bs:helpLink file="Git" anchor="Git_gc"><bs:helpIcon/></bs:helpLink>.
</div>