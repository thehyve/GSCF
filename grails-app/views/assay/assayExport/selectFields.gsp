<%--
  Created by IntelliJ IDEA.
  User: siemensikkema
  Date: 2/3/11
  Time: 1:29 PM
--%>

<%@ page contentType="text/html;charset=UTF-8" %>
<html>
<head>
	<g:if test="${GALAXY_URL}">
		<meta name="layout" content="galaxy"/>
	</g:if>
	<g:else>
		<meta name="layout" content="main"/>
	</g:else>

	<title>Select assay fields</title>
	<r:require modules="helptooltips" />

	<style type="text/css">
	.category {
		margin-left: 5px;
	}

	.field {
		margin-left: 20px;
	}

	.element .helpIcon {
		margin-top: 0;
	}
	</style>
</head>

<body>
<div>

	<h1>Select the columns that you want to be included in the resulting file</h1>

	<g:if test="${errorMessage}">
		<div class="errormessage">${errorMessage}</div>
	</g:if>

	In this step you can make a selection from the available fields stored in the database related to the samples, including measurement data from a module (if available).

	<g:form name="fieldSelectForm" action="assayExport">

		<g:set var="catNum" value="${0}"/>
		<g:each in="${fieldMap}" var="entry">

			<assayExporter:categorySelector category="${entry.key}" name="cat_${catNum}" value="${true}"/>

			<assayExporter:fieldSelectors ref="cat_${catNum}" fields="${entry.value}"/>

			<g:set var="catNum" value="${catNum + 1}"/>

		</g:each>

		<assayExporter:categorySelector category="Measurements" name="cat_${catNum}"
										value="${measurementTokens as Boolean}"/>
		<g:select name="measurementToken" id="measurementToken" from="${measurementTokens}" value="${measurementTokens}"
				  class="field" multiple="true"/>
		<br/><br/>

		<g:if test="${GALAXY_URL}">
			<g:submitButton name="submitToGalaxy" value="Submit to Galaxy"/>
		</g:if>
		<g:else>
			<h1>Export measurement metadata</h1>
			<g:radioGroup name="exportMetadata"
						  labels="['yes', 'no']"
						  values="[1,0]" value="1">
				<p>${it.radio} ${it.label}</p>
			</g:radioGroup>
		
			<h1>Select type of resulting file</h1>
			<g:radioGroup name="exportFileType"
						  labels="['Tab delimited (.txt)', 'Comma Separated: USA/UK (.csv)', 'Semicolon Separated: European (.csv)']"
						  values="[1,2,3]" value="1">
				<p>${it.radio} ${it.label}</p>
			</g:radioGroup>
			<g:submitButton name="submit" value="Submit"/>
		</g:else>



	</g:form>

</div>
</body>
</html>