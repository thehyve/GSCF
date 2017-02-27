	<div class="studyEdit event">
		<h1>
			<g:if test="${entity?.id}">
				Edit sample type [${entity.name.encodeAsHTML()}]
			</g:if>
			<g:else>
				New sample type
			</g:else>
		</h1>
		
		<g:render template="/common/flashmessages" />
		
		<g:render template="/common/flash_validation_messages" />
		 
		<g:form action="${actionName}" name="samplingEventDetails">
			<g:hiddenField name="_action" />
			<g:if test="${entity?.id}">
				<g:hiddenField name="id" value="${entity?.id}" />
			</g:if>
			<g:if test="${entity?.parent?.id}">
				<g:hiddenField name="parentId" value="${entity?.parent?.id}" />
			</g:if>
			
			<af:templateElement name="template" description="Template"
				value="${entity?.template}" entity="${dbnp.studycapturing.SamplingEvent}"
				addDummy="true" onChange="if(\$( this ).val() != '') { \$( '#samplingEventDetails' ).submit(); }">
				Choose the type of sample type you would like to create.
				Depending on the chosen template specific fields can be filled out. If none of the templates contain all the necessary fields, a new template can be defined (based on other templates).
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

