<%@include file="/include.jsp" %>
<c:set var="ref"><c:url value="/admin/admin.html?item=diagnostics&tab=gitStatus"/></c:set>
Starting from version &quot;2022.02 Cloud&quot; TeamCity supports <a href="https://git-scm.com/">native git</a> (versions 2.29+) to communicate with remote repositories on server-side.<br/>
Instead of using <a href="https://www.eclipse.org/jgit">JGit library</a> with <a href="https://github.com/mwiede/jsch">JSch</a> as SSH provider it can now directly run git commands with system ssh client.</br>
For performance reasons it's recommended to switch to the native implementation - please use <a href="${ref}">diagnostics</a> page to check native git status.