<%@ page import="dbnp.query.Operator" %>
<html>
<head>
	<meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
	<meta name="layout" content="main"/>
	<title>Query database</title>
	<r:require module="advancedQuery" />
	<script type="text/javascript">
		// Make a list of fields to search in
		var queryableFields = [
			<g:set var="j" value="${0}" />
			<g:each in="${searchableFields}" var="entity">
				<g:each in="${entity.value}" var="field">
					<g:if test="${j > 0}">,</g:if>
					{
						label: "${(
							entity.key.toString() + '.' + field.toString() + ' ' +
							entity.key.toString() + ' ' + field.toString() + ' ' + 
							(field == '*' ? 'any field' : '') + ' ' +
							(field == '*' && entity.key == '*' ? 'any field in any object' : '')
							).encodeAsJavaScript()}",
						show: "${
							(field == '*' ? 
								( entity.key == '*' ? '[Any field in any object]' : '[Any field in ' + entity.key.toString() + ']' ) : 
								(field?.size() > 1 ? 
									field[0].toUpperCase() + field[1..-1] : 
									field)
							).encodeAsJavaScript()}",
						value: "${entity.key.toString().encodeAsJavaScript()}.${field.toString().encodeAsJavaScript()}",
						entity: "${entity.key.toString().encodeAsJavaScript()}"
					}
					<g:set var="j" value="1" />
				</g:each>
			</g:each>
		];
	
		<g:if test="${criteria && criteria.size() > 0}">
			// Show given criteria
			$(function() {
				<g:each in="${criteria}" var="criterion">
					<g:if test="${criterion.operator == Operator.insearch}">
							/* ${previousSearches?.getAt( criterion.value )?.url} */
							showCriterium( 
								"${criterion.entityField().encodeAsJavaScript()}", 
								{ 
									'id': ${criterion.value}, 
									'description': "<g:if test="${previousSearches?.getAt( criterion.value )}">${previousSearches[ criterion.value ].toString().encodeAsJavaScript()}</g:if><g:else>Search ${criterion.value}</g:else>", 
									'url': "<g:if test="${previousSearches?.getAt( criterion.value )?.url}">${previousSearches[ criterion.value ].url.encodeAsJavaScript()}</g:if><g:else><g:createLink controller='advancedQuery' action='show' id="${criterion.value}" /></g:else>" 
								}, 
								"${criterion.operator.toString().encodeAsJavaScript()}"
							);
					</g:if>
					<g:else>
						showCriterium("${criterion.entityField().encodeAsJavaScript()}", "${criterion.value.toString().encodeAsJavaScript()}", "${criterion.operator.toString().encodeAsJavaScript()}");
					</g:else>
				</g:each>
							
				// Show or hide the 'search mode' box (AND or OR)
				toggleSearchMode()

				// Enable or disable the search button
				toggleSearchButton()
			});
		</g:if>
	</script>
</head>
<body>

<h1>Search database</h1>

<g:render template="/common/flashmessages" />

<div id="searchForm">
	<g:form action="search" method="get">

		<h3><span class="nummer">1</span>Select criteria</h3>
		<p class="explanation">
			N.B. Comparing numerical values is done without taking into
			account the units. E.g. a weight of 1 kg equals 1 grams.
		</p>
		<ul id="criteria">
			<li class="titlerow">
				<span class="entityfield">
					Field
				</span>
				<span class="operator">
					Operator
				</span>
				<span class="value">
					Value
				</span>
			</li>
			<li class="newCriterion">
				<span class="entityfield">
					<select name="criteria.0.entityfield" id="queryFieldSelect" onChange="updateAutocomplete()">
						<option value=""></option>
						<g:each in="${searchableFields}" var="entity">
							<optgroup label="${entity.key}">
								<g:each in="${entity.value}" var="field">
									<option value="${entity.key}.${field}">
										<g:if test="${field?.size() > 1}">
											${field[0].toUpperCase() + field[1..-1]}
										</g:if>
										<g:else>
											${field}
										</g:else>
									</option>
								</g:each>
							</optgroup>
						</g:each>
					</select>
				</span>
				<span class="operator">
					<select id="operator" name="criteria.0.operator" onChange="updateAutocomplete();">
						<option value="equals">Equals</option>
						<option value="contains">Contains</option>
						<option value="&gt;=">&gt;=</option>
						<option value="&gt;">&gt;</option>
						<option value="&lt;">&lt;</option>
						<option value="&lt;=">&lt;=</option>
					</select>
				</span>
				<span class="value">
					<input class='text' type="text" id="value" name="criteria.0.value" />
				</span>
				<span class="addButton">
					<a class="disabled" href="#" onClick="addCriterion(); return false;">
						<img src="${fam.icon( name: 'add' )}" border="0">
					</a>
				</span>
			</li>
		</ul>
		
		<div id="searchMode">
			<h3><span class="nummer">1b</span>Search mode</h3>
			<p>
				Choose how to combine the given criteria:<br />
				<g:select from="${searchModes}" name="operator" />
			</p>
		</div>
		
		<h3><span class="nummer">2</span>Output type</h3>
		<p>
			Choose the type of output:<br />
			<g:select from="${entitiesToSearchFor}" optionKey="key" optionValue="value" name="entity" />
		</p>

		<h3><span class="nummer">3</span>Run query</h3>
		<p>
			<input type="submit" disabled="disabled" value="Search" class="submitcriteria" />
		</p>
	</g:form>
	
	<br clear="all" />
</div>
<g:if test="${session.queries?.size()}">
<p class="options">
	<g:link class="listPrevious" action="list">Previous searches</g:link>
</p>
</g:if>
<br  clear="all" />
</body>
</html>
