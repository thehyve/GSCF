<html>
<head>
	<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
	<meta name="layout" content="main" />
	<title>Study edit wizard</title>
	
	<r:require modules="studyEdit,gscf-datatables" />
</head>
<body>	
	<div class="basicTabLayout studyEdit studySubjects">
		<h1>
			<span class="truncated-title">
				Edit study [${study.code?.encodeAsHTML()}]
			</span>
			<g:render template="steps" model="[study: study, active: 'subjects']"  />
		</h1>
		
		<g:render template="/common/flashmessages" />
		
		<span class="message info"> 
			<span class="title">Define or import your subjects</span> 
			List all subjects and enter information about them. You can also import your subjects from an excel sheet.
		</span>
		
		<g:render template="/common/flash_validation_messages" />
		
		<g:form action="subjects" name="subjects">
			<g:hiddenField name="_action" />
			<g:hiddenField name="id" value="${study.id}" />
			
			<g:each in="${templates}" var="template">
				<h3>Template: ${template.name}</h3>
				<table id="subjectsTable_${template.id}" data-templateId="${template.id}" data-fieldPrefix="subject" data-formId="subjectForm" class="subjectsTable selectMulti" rel="${g.createLink(action:"dataTableEntities", id: study.id, params: [template: template.id])}">
					<thead>
						<tr>
							<g:each in="${domainFields + template.getFields()}" var="field">
								<th data-fieldname="${field.escapedName()}">${field.name}</th>
							</g:each>
						</tr>
					</thead>
					<tfoot>
						<tr><td>
							<div class="messagebar selectAll">
								You selected all items on this page. Would you like to <a href="#">select all items on other pages</a> as well?
							</div>						
							<div class="messagebar saveChanges">
								<span class="links">
									<a href="#" onClick="StudyEdit.datatables.editable.save(this); return false;">Save</a> or 
									<a href="#" onClick="StudyEdit.datatables.editable.discardChanges(this); return false;">Discard</a>
								</span>
								<span class="saving">Saving...</span>
							</div>
						</td></tr>
					</tfoot>
				</table>
				
				<div id="subjectsTable_${template.id}_prototype" style="display: none" class="editable prototype">
					<g:render template="prototypes" model="[ template: template]" />
				</div>							
			</g:each>			
			
			<p class="options">
				<g:link controller="studyEdit" action="properties" id="${study.id}" class="previous">Previous</g:link>
				<g:link controller="studyEditDesign" action="index" id="${study.id}" class="next">Next</g:link>
				
	            <a class="separator add" href="#" data-url="${g.createLink( controller: "studyEdit", action: "addSubjects", params: [ parentId: study.id ] )}" onClick="StudyEdit.subjects.add(); return false;">
	                Add
	            </a>				
	            <g:link class="import" controller="importer" action="upload" params="['initial.study': study?.id, importer: 'Subjects']">
	                Import
	            </g:link>				
	            
	            <a href="#" class="delete" onClick="StudyEdit.subjects.deleteItem(); return false;">Delete</a>
				
			</p>			
			
			<br clear="all" />
		</g:form>

		<g:form action="editSubjects" name="subjectForm"><g:hiddenField class="original" name="id" value="${study.id}" /></g:form>
		<g:form action="deleteSubjects" id="${study.id}" name="deleteSubjects"></g:form> 
		<div id="addDialog"></div>
		
		<r:script>
			$(function() {
				StudyEdit.datatables.initialize( ".subjectsTable" );
				StudyEdit.subjects.initialize();
			});
		</r:script>
	</div>
</body>
</html>
