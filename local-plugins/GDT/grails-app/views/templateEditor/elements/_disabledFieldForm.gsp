    <g:hiddenField name="id" value="${templateField?.id}" />
	<g:hiddenField name="version" value="${templateField?.version}" />
	<g:hiddenField name="is_disabled" value="1" />
	<g:if test="${is_selected}"><g:hiddenField name="renderTemplate" value="selected" /></g:if>
	<g:if test="${template}"><g:hiddenField name="templateId" value="${template.id}" /></g:if>

    <g:set var="templateFieldService" bean="templateFieldService"/>

	<label for="name">Name:</label> <g:textField disabled="disabled" name="name" value="${templateField?.name}" /><br />
	<label for="type">Type:</label> <g:textField disabled="disabled" name="type" value="${templateField?.type}" /><br />

	%{-- The disabled="disabled" tag in the textFields above will exclude them from the form, hiddenFields are set to add them--}%
	<g:hiddenField name="name" value="${templateField?.name}"/>
	<g:hiddenField name="type" value="${templateField?.type}"/>

	<g:if test="${templateField?.type.toString() == 'STRINGLIST' || templateField?.type.toString() == 'EXTENDABLESTRINGLIST'}">
    	<g:set var="usedFields" value="${templateFieldService.getUsedListEntries( templateField )}"/>
		<div class="extendablestringlist_options stringlist_options">
		  <label for="type">Used items:</label>
			<g:textArea name="usedListEntries" disabled="disabled" value="${usedFields.join( '\n' )}" />
		  <label for="type">Extra Items (every item on a new line):</label>
			<g:textArea name="listEntries" value="${(templateField.listEntries*.toString() - usedFields ).join( '\n' )}" />
		</div>
		
	</g:if>
	<g:if test="${templateField?.type.toString() == 'ONTOLOGYTERM'}">
    	<g:set var="usedOntologies" value="${templateField.getUsedOntologies()}"/>
		<div class="extra ontologyterm_options" style='display: block;'>
		  <label for="type">Used ontologies:</label>
	        <g:select size="5" from="${usedOntologies}" disabled="true" optionValue="name" optionKey="id" name="ontologies" id="used_ontologies_${templateField?.id}" /> <br />
	
		  <label for="type">
		  	Extra ontologies:<br />
		  	<a href="#" style="text-decoration: underline;" onClick="return openOntologyDialog();">Add new</a>
		  	<a href="#" style="text-decoration: underline;" onClick="return deleteOntology(${templateField?.id});">Remove</a>
		  </label>
			<g:select multiple="yes" size="5" from="${templateField.ontologies - usedOntologies}" class="ontologySelect" optionValue="name" optionKey="id" name="ontologies" id="ontologies_${templateField?.id}" /><br />
		</div>
	</g:if>	
	<label for="unit">Unit:</label> <g:textField disabled="disabled" name="unit" value="${templateField?.unit}" /><br />
	<label for="comment">Comment:</label> <g:textArea name="comment" value="${templateField?.comment}" /><br />
	<label for="required">Required:</label> <g:checkBox disabled="disabled" name="required" value="${templateField?.required}" /><br />

	<div class="templateFieldButtons">
		<input type="button" value="Save" onClick="updateTemplateField( ${templateField?.id} );">
		<input type="button" value="Close" onClick="hideTemplateFieldForm( ${templateField?.id} );  resetTemplateFieldForm( ${templateField?.id} );">
	</div>
