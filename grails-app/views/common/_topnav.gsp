
    <!-- TOPNAV //-->
    <ul class="topnav">
     <li><g:link url="/${meta(name: 'app.name')}/">Home</g:link></li>
     <li>
      <a href="#">Studies</a>
      <ul class="subnav">
        <li><a href="#">View Studies</a></li>
        <li><g:link controller="wizard" action="index">Create Study</g:link></li>
      </ul>
     </li>
     <li><g:link controller="load" action="index">Loading data</g:link></li>
     <li><g:link controller="query" action="index">Query database</g:link></li>
<n:isLoggedIn>
     <li><g:link controller="study" action="list">My studies</g:link></li>
</n:isLoggedIn>
     <li>
	  <a href="#">Scaffolded controllers</a>
	  <ul class="subnav"><g:each var="c" in="${grailsApplication.controllerClasses}">
	   <li><g:link controller="${c.logicalPropertyName}">${c.fullName}</g:link></li></g:each>
	  </ul>
     </li>
<n:isAdministrator>
     <li>
	  <a href="#">User administation</a>
	  <ul class="subnav">
	    <li><g:link controller="admins" action="index" class="icon icon_user_suit">Manage Administrators</g:link></li>
	    <li><g:link controller="user" action="list" class="icon icon_user">List Users</g:link></li>
	    <li><g:link controller="user" action="create" class="icon icon_user_add">Create User</g:link></li>
	    <li><g:link controller="role" action="list" class="icon icon_cog">List Roles</g:link></li>
	    <li><g:link controller="role" action="create" class="icon icon_cog_add">Create Role</g:link></li>
	    <li><g:link controller="group" action="list" class="icon icon_group">List Groups</g:link></li>
	    <li><g:link controller="group" action="create" class="icon icon_group_add">Create Group</g:link></li>
	    <li><g:link controller="auth" action="logout" class="icon icon_cross">Sign out</g:link></li>
	  </ul>
     </li>
</n:isAdministrator>
    </ul>
    <!-- /TOPNAV //-->