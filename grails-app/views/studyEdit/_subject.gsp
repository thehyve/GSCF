	<div class="studyEdit subject">
		<g:if test="${flash.error}">
			<div class="errormessage">
				${flash.error.toString().encodeAsHTML()}
			</div>
		</g:if>
		<g:if test="${flash.message}">
			<div class="message">
				${flash.message.toString().encodeAsHTML()}
			</div>
		</g:if>	
		
		<p>
			Please note that all required fields have to be filled, before the subjects can be added. You can enter specific information
			for different subjects after adding them
		</p>

		<g:hasErrors bean="${entity}">
			<div class="info">
				<g:renderErrors bean="${entity}" />
			</div>
		</g:hasErrors>  
		 
		<g:form action="${actionName}" name="subjectDetails">
			<g:hiddenField name="_action" />
			<g:if test="${entity?.parent?.id}">
				<g:hiddenField name="parentId" value="${entity?.parent?.id}" />
			</g:if>

			<div class="element"> 
				<div class="description">Count </div>
				<div class="input"><input type="text" name="count" value="${number}" /></div>
				<div class="helpContent">The number of subjects to be added</div>
			</div>
			
			<af:templateElement name="template" description="Template"
				value="${entity?.template}" entity="${dbnp.studycapturing.Subject}"
				addDummy="true" onChange="if(\$( this ).val() != '') { \$( '#subjectDetails' ).submit(); }">
				Choose the type of subject you would like to create. Depending on the chosen template specific fields can be filled out. 
			</af:templateElement>
		
			<g:if test="${entity}">
				<g:if test="${entity.template?.description}">
					<div class="element">
						<div class="templatedescription">
							${entity.template?.description?.encodeAsHTML()}
						</div>
					</div>
				</g:if>
				<af:templateElements entity="${entity}" />
			</g:if>
		
		</g:form>
	
	</div>
