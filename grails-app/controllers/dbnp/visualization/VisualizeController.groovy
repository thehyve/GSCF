/**
 * Visualize Controller
 *
 * This controller enables the user to visualize his data
 *
 * @author robert@thehyve.nl
 * @since 20110825
 * @package dbnp.visualization
 *
 * Revision information:
 * $Rev$
 * $Author$
 * $Date$
 */
package dbnp.visualization

import dbnp.studycapturing.*;
import grails.converters.JSON

import org.dbnp.gdt.*

class VisualizeController {
    def authenticationService
    def moduleCommunicationService
    def infoMessage = []
    def offlineModules = []
    def infoMessageOfflineModules = []
    final int CATEGORICALDATA = 0
    final int NUMERICALDATA = 1
    final int RELTIME = 2
    final int DATE = 3

    /**
     * Shows the visualization screen
     */
    def index = {
        [studies: Study.giveReadableStudies(authenticationService.getLoggedInUser())]
    }

    def getStudies = {
        def studies = Study.giveReadableStudies(authenticationService.getLoggedInUser());
        return sendResults(studies)
    }

    /**
     * Based on the study id contained in the parameters given by the user, a list of 'fields' is returned. This list can be used to select what data should be visualized
     * @return List containing fields
     * @see parseGetDataParams
     * @see getFields
     */
    def getFields = {
        def input_object
        def studies

        try {
            input_object = parseGetDataParams();
        } catch (Exception e) {
            log.error("VisualizationController: getFields: " + e)
            return returnError(400, "An error occured while retrieving the user input.")
        }

        // Check to see if we have enough information
        if (input_object == null || input_object?.studyIds == null) {
            setInfoMessage("Please select a study.")
            return sendInfoMessage()
        } else {
            studies = input_object.studyIds[0]
        }

        def fields = [];

        /*
         Gather fields related to this study from GSCF.
         This requires:
         - a study.
         - a category variable, e.g. "events".
         - a type variable, either "domainfields" or "templatefields".
         */
        // TODO: Handle multiple studies
        def study = Study.get(studies)

        if (study != null) {
            log.trace( "Start retrieving fields from GSCF")
            fields += getFields(study, "subjects", "domainfields")
            fields += getFields(study, "subjects", "templatefields")
            /*fields += getFields(study, "events", "domainfields")
            fields += getFields(study, "events", "templatefields")*/
            fields += getFields(study, "samplingEvents", "domainfields")
            fields += getFields(study, "samplingEvents", "templatefields")
            fields += getFields(study, "assays", "domainfields")
            fields += getFields(study, "assays", "templatefields")
            fields += getFields(study, "samples", "domainfields")
            fields += getFields(study, "samples", "templatefields")

            // Also make sure the user can select eventGroup to visualize
            fields += formatGSCFFields("domainfields", [name: "name"], "GSCF", "eventGroups");

            /*
                Gather fields related to this study from modules.
                This will use the getMeasurements RESTful service. That service returns measurement types, AKA features.
                It does not actually return measurements (the getMeasurementData call does).
                The getFields method (or rather, the getMeasurements service) requires one or more assays and will return all measurement
                types related to these assays.
                So, the required variables for such a call are:
                  - a source variable, which can be obtained from AssayModule.list() (use the 'name' field)
                  - an assay, which can be obtained with study.getAssays()
                 */
            study.getAssays().each { assay ->
                log.trace( "Retrieving fields from module for assay " + assay )
                def list = []
                if (!offlineModules.contains(assay.module.id)) {
                    list = getFields(assay.module.toString(), assay)
                    if (list != null) {
                        if (list.size() != 0) {
                            fields += list
                        }
                    }
                }
            }
            offlineModules = []

            // Make sure any informational messages regarding offline modules are submitted to the client
            setInfoMessageOfflineModules()

            // TODO: Maybe we should add study's own fields
        } else {
            log.error("VisualizationController: getFields: The requested study could not be found. Id: " + studies)
            return returnError(404, "The requested study could not be found.")
        }

        log.trace( "Sort fields" )
        fields.sort { a, b ->
            def sourceEquality = a.source.toString().toLowerCase().compareTo(b.source.toString().toLowerCase())
            if (sourceEquality == 0) {
                def categoryEquality = a.category.toString().toLowerCase().compareTo(b.category.toString().toLowerCase())
                if (categoryEquality == 0) {
                    a.name.toString().toLowerCase().compareTo(b.name.toString().toLowerCase())
                } else return categoryEquality
            } else return sourceEquality
        }

        log.trace "Return results to the user"
        return sendResults(['studyIds': studies, 'fields': fields])
    }

    /**
     * Based on the field ids contained in the parameters given by the user, a list of possible visualization types is returned. This list can be used to select how data should be visualized.
     * @return List containing the possible visualization types, with each element containing
     *          - a unique id
     *          - a unique name
     *         For example: ["id": "barchart", "name": "Barchart"]
     * @see parseGetDataParams
     * @see determineFieldType
     * @see determineVisualizationTypes
     */
    def getVisualizationTypes = {
        def inputData = parseGetDataParams();

        if (inputData.columnIds == null || inputData.columnIds == [] || inputData.columnIds[0] == null || inputData.columnIds[0] == "") {
            setInfoMessage("Please select a data source for the x-axis.")
            return sendInfoMessage()
        }
        if (inputData.rowIds == null || inputData.rowIds == [] || inputData.rowIds[0] == null || inputData.rowIds[0] == "") {
            setInfoMessage("Please select a data source for the y-axis.")
            return sendInfoMessage()
        }

        // TODO: handle the case of multiple fields on an axis
        // Determine data types
        // TODO: When multiple fields are to be retrieved from a module, retrieve them all at once
        log.trace "Determining fieldTypes: " + inputData.rowIds[0] + " / " + inputData.columnIds[0] + " / " + inputData.groupIds[0]
        def fieldTypes = determineFieldTypes(inputData.studyIds[0], [
            'row': inputData.rowIds[0],
            'column': inputData.columnIds[0],
            'group': inputData.groupIds[0]
        ])

        // Determine possible visualization- and aggregationtypes
        def visualizationTypes = determineVisualizationTypes(fieldTypes['row'], fieldTypes['column'])
        def aggregationTypes = determineAggregationTypes(fieldTypes['row'], fieldTypes['column'], fieldTypes['group'])

        log.trace "visualization types: " + visualizationTypes + ", determined this based on " + fieldTypes['row'] + " and " + fieldTypes['column']
        log.trace "aggregation   types: " + aggregationTypes + ", determined this based on " + fieldTypes['row'] + " and " + fieldTypes['column'] + " and " + fieldTypes['group']

        def fieldData = ['x': parseFieldId(inputData.columnIds[0]), 'y': parseFieldId(inputData.rowIds[0])];

        return sendResults([
                'types': visualizationTypes,
                'aggregations': aggregationTypes,

                // TODO: Remove these ids when the view has been updated. Use xaxis.id and yaxis.id instead
                'rowIds': inputData.rowIds[0],
                'columnIds': inputData.columnIds[0],

                'xaxis': [
                        'id': fieldData.x.fieldId,
                        'name': fieldData.x.name,
                        'unit': fieldData.x.unit,
                        'type': dataTypeString(fieldTypes['column'])
                ],
                'yaxis': [
                        'id': fieldData.y.fieldId,
                        'name': fieldData.y.name,
                        'unit': fieldData.y.unit,
                        'type': dataTypeString(fieldTypes['row'])
                ],

        ])
    }

    /**
     * Gather fields related to this study from modules.
     This will use the getMeasurements RESTful service. That service returns measurement types, AKA features.
     getMeasurements does not actually return measurements (the getMeasurementData call does).
     * @param source The id of the module that is the source of the requested fields, as can be obtained from AssayModule.list() (use the 'id' field)
     * @param assay The assay that the source module and the requested fields belong to
     * @return A list of map objects, containing the following:
     *           - a key 'id' with a value formatted by the createFieldId function
     *           - a key 'source' with a value equal to the input parameter 'source'
     *           - a key 'category' with a value equal to the 'name' field of the input paramater 'assay'
     *           - a key 'name' with a value equal to the name of the field in question, as determined by the source value
     */
    protected def getFields(source, assay) {
        def fields = []
        def callUrl = ""

        // Making a different call for each assay
        def urlVars = "assayToken=" + assay.UUID
        try {
            callUrl = "" + assay.module.baseUrl + "/rest/getMeasurementMetaData/query?" + urlVars
            def json = moduleCommunicationService.callModuleRestMethodJSON(assay.module.baseUrl, callUrl);

            def collection = []
            json.each { jason ->
                collection.add(jason)
            }
            // Formatting the data
            collection.each { field ->
                // For getting this field from this assay
                fields << ["id": createFieldId(id: field.name, name: field.name, source: "" + assay.id, type: "" + assay.name, unit: (field.unit ?: "")), "source": source, "category": "" + assay.name, "name": field.name + (field.unit ? " (" + field.unit + ")" : "")]
            }
            
            // Also add the 'all fields for assay x' option
            fields << [
                "id": createFieldId(id: "*", name: "*", source: "" + assay.id, type: "" + assay.name, unit: ""), 
                "source": source, 
                "category": "" + assay.name, "name": "[All " + collection.size() + " features]"
            ]
        } catch (Exception e) {
            //returnError(404, "An error occured while trying to collect field data from a module. Most likely, this module is offline.")
            offlineModules.add(assay.module.id)
            infoMessageOfflineModules.add(assay.module.name)
            log.error( "An error occurred while retrieving fields from " + assay.module + " for assay " + assay, e)
        }

        return fields
    }

    /**
     * Gather fields related to this study from GSCF.
     * @param study The study that is the source of the requested fields
     * @param category The domain that a field (a property in this case) belongs to, e.g. "subjects", "samplingEvents"
     * @param type A string that indicates the type of field, either "domainfields" or "templatefields".
     * @return A list of map objects, formatted by the formatGSCFFields function
     */
	protected def getFields(study, category, type) {
        // Collecting the data from it's source
        def collection = []
        def fields = []
        def source = "GSCF"

        if (type == "domainfields")
            // All templated GSCF domain classes implement giveDomainFields via a domainFields property
            collection = domainObjectCallback(category)?.domainFields;
        else
            collection = templateObjectCallback(category, study)?.fields

        collection?.unique()

        // Formatting the data
        fields += formatGSCFFields(type, collection, source, category)

        return fields
    }

    /**
     * Formats the data contained in the input parameter 'collectionOfFields' for use as so-called fields, that will be used by the user interface to allow the user to select data from GSCF for visualization
     * If 'collectionOfFields' is indeed a collection, this same function is called for each item in the collection. 
     * If 'collectionOfFields' is in fact a single object, it will be formatted and added to the return value.
     * @param type A string that indicates the type of field, either "domainfields" or "templatefields".
     * @param collectionOfFields A collection of fields, which could also contain only one item
     * @param source Likely to be "GSCF"
     * @param category The domain that a field (a property in this case) belongs to, e.g. "subjects", "samplingEvents"
     * @return A list containing list objects, containing the following:
     *           - a key 'id' with a value formatted by the createFieldId function
     *           - a key 'source' with a value equal to the input parameter 'source'
     *           - a key 'category' with a value equal to the input parameter 'category'
     *           - a key 'name' with a value equal to the name of the field in question, as determined by the source value
     */
	protected def formatGSCFFields(type, collectionOfFields, source, category) {

        if (collectionOfFields == null || collectionOfFields == []) {
            return []
        }
        def fields = []
        if (collectionOfFields instanceof Collection) {
            // Apparently this field is actually a list of fields.
            // We will call ourselves again with the list's elements as input.
            // These list elements will themselves go through this check again, effectively flattening the original input
            for (int i = 0; i < collectionOfFields.size(); i++) {
                fields += formatGSCFFields(type, collectionOfFields[i], source, category)
            }
            return fields
        } else {
            // This is a single field. Format it and return the result.
            if (type == "domainfields") {
                fields << ["id": createFieldId(id: collectionOfFields.name, name: collectionOfFields.name, source: source, type: category, unit: (collectionOfFields.unit ?: "")), "source": source, "category": category, "name": collectionOfFields.name + (collectionOfFields.unit ? " (" + collectionOfFields.unit + ")" : "")]
            }
            if (type == "templatefields") {
                fields << ["id": createFieldId(id: collectionOfFields.id.toString(), name: collectionOfFields.name, source: source, type: category, unit: (collectionOfFields.unit ?: "")), "source": source, "category": category, "name": collectionOfFields.name + (collectionOfFields.unit ? " (" + collectionOfFields.unit + ")" : "")]
            }
            return fields
        }
    }

    /**
     * Retrieves data for the visualization itself.
     * Returns, based on the field ids contained in the parameters given by the user, a map containing the actual data and instructions on how the data should be visualized.
     * @return A map containing containing (at least, in the case of a barchart) the following:
     *           - a key 'type' containing the type of chart that will be visualized
     *           - a key 'xaxis' containing the title and unit that should be displayed for the x-axis
     *           - a key 'yaxis' containing the title and unit that should be displayed for the y-axis*
     *           - a key 'series' containing a list, that contains one or more maps, which contain the following:
     *                - a key 'name', containing, for example, a feature name or field name
     *                - a key 'y', containing a list of y-values
     *                - a key 'error', containing a list of, for example, standard deviation or standard error of the mean values, each having the same index as the 'y'-values they are associated with
     */
    def getData = {
        // Extract parameters
        // TODO: handle erroneous input data
        def inputData = parseGetDataParams();

        if (inputData.columnIds == null || inputData.rowIds == null) {
            infoMessage = "Please select data sources for the y- and x-axes."
            return sendInfoMessage()
        }

        // TODO: handle the case that we have multiple studies
        def studyId = inputData.studyIds[0];
        def study = Study.get(studyId as Integer);

        // Find out what samples are involved
        def samples = study.samples

        // If the user is requesting data that concerns only subjects, then make sure those subjects appear only once
        if (parseFieldId(inputData.columnIds[0]).type == 'subjects' && parseFieldId(inputData.rowIds[0]).type == 'subjects') {
            samples.unique { it.parentSubject }
        }

        // Retrieve the data for both axes for all samples
        // TODO: handle the case of multiple fields on an axis
        def fields = ["x": inputData.columnIds[0], "y": inputData.rowIds[0], "group": inputData.groupIds[0]];
        def fieldInfo = parseFieldIds(fields)
        
        determineFieldTypes(studyId, fieldInfo).each { k, v ->
            fieldInfo[k].fieldType = v
        }

        // If the groupAxis is numerical, we should ignore it, unless it is for grouping on all features or a table is asked for
        if (fieldInfo.group && fieldInfo.group.fieldType == NUMERICALDATA && fieldInfo.group.name != '*' && inputData.visualizationType != "table") {
            fields.group = null;
            fieldInfo.group = null;
        }

        // Enable charting of barcharts with numerical data on both axes
        if (fieldInfo.x.fieldType == NUMERICALDATA && fieldInfo.y.fieldType == NUMERICALDATA) {
            if (inputData.visualizationType == "horizontal_barchart") {
                fieldInfo.y.fieldType = CATEGORICALDATA;
            } else if (inputData.visualizationType == "barchart") {
                fieldInfo.x.fieldType = CATEGORICALDATA;
            }
        }

        log.trace "Fields: "
        fieldInfo.each { log.trace it }

        // Fetch all data from the system. data will be in the format:
        // 		[ "x": [ 3, 6, null, 10 ], "y": [ "male", "male", "female", "female" ], "group": [ "US", "NL", "NL", "NL" ]
        //	If a field is not given, the data will be NULL
        def data = getAllFieldData(study, samples, fields);

        log.trace "All Data: "
        data.each { log.trace it }

        // Aggregate the data based on the requested aggregation
        def aggregatedData = aggregateData(data, fieldInfo, inputData.aggregation);

        log.trace "Aggregated Data: "
        aggregatedData.each { log.trace it }

        // No convert the aggregated data into a format we can use
        def returnData = formatData(inputData.visualizationType, aggregatedData, fieldInfo);

        log.trace "Returndata: "
        returnData.each { log.trace it }

        // Make sure no changes are written to the database
        study.discard()
        samples*.discard()

        return sendResults(returnData)
    }

    /**
     * Parses the parameters given by the user into a proper list
     * @return Map with 4 keys:
     * 		studyIds: 	list with studyIds selected
     * 		rowIds:		list with fieldIds selected for the rows
     * 		columnIds:	list with fieldIds selected for the columns
     * 		visualizationType:	String with the type of visualization required
     * @see getFields
     * @see getVisualizationTypes
     */
    def parseGetDataParams() {
        def studyIds = params.list('study');
        def rowIds = params.list('rows');
        def columnIds = params.list('columns');
        def groupIds = params.list('groups');
        def visualizationType = params.get('types');
        def aggregation = params.get('aggregation');

        return ["studyIds": studyIds, "rowIds": rowIds, "columnIds": columnIds, "groupIds": groupIds, "visualizationType": visualizationType, "aggregation": aggregation];
    }

    /**
     * Retrieve the field data for the selected fields
     * @param study Study for which the data should be retrieved
     * @param samples Samples for which the data should be retrieved
     * @param fields Map with key-value pairs determining the name and fieldId to retrieve data for. Example:
     * 						[ "x": "field-id-1", "y": "field-id-3", "group": "field-id-6" ]
     * @return A map with the same keys as the input fields. The values in the map are lists of values of the
     * 					selected field for all samples. If a value could not be retrieved for a sample, null is returned. Example:
     * 						[ "numValues": 4, "x": [ 3, 6, null, 10 ], "y": [ "male", "male", "female", "female" ], "group": [ "US", "NL", "NL", "NL" ] ]
     */
    def getAllFieldData(study, samples, fields) {
        def fieldData = [:]
        def numValues = 0;
        def numSamples = samples.size()
        
        // Flag to keep track of whether the '*' is chosen
        // for one or more of the fields. In that case, the other
        // values should be repeated to make sure that there are
        // enough items 
        def multipleFieldsMultiplier = 1
        
        // Parse the field IDs
        def parsedFields = parseFieldIds(fields)
        
        // Group the field IDs per source
        // Only use the fields that we could parse.
        def groupedParsedFields = parsedFields.values().findAll().groupBy { it.source }

        groupedParsedFields.each { source, sourceFields ->
            if(source == "GSCF") {
                // Determine field type for each field independently
                sourceFields.each {
                    fieldData[it.key] = getFieldData(study, samples, it)
                }
            } else {
                // Data is in a module. Retrieve all data at once
                def moduleData = getModuleData(study, samples, source, sourceFields*.name)
                
                sourceFields.each {
                    if( it.name == '*' ) {
                        // Concatenate the values and feature names for all features
                        // If this field is chosen for X or Y, return the values
                        // If this field is chosen on the Group axis, return the feature names
                        def multipleFieldValues = []
                        
                        if( it.key == 'group' ) {
                            moduleData.each  { fieldName, fieldValues ->
                                multipleFieldValues += ( [fieldName] * numSamples )
                            }
                        } else {
                            moduleData.each  { fieldName, fieldValues ->
                                multipleFieldValues += fieldValues
                            }
                        }
                        
                        fieldData[it.key] = multipleFieldValues
                        
                        // Make sure to multiply all 'single' values later on 
                        multipleFieldsMultiplier = moduleData.size() 
                    } else {
                        fieldData[it.key] = moduleData[it.name]
                    }
                }
            }
        }
        
        // Handle the special case that for one or more axes the '*' is selected
        if( multipleFieldsMultiplier > 1 ) {
            fieldData.each { key, data ->
                if( parsedFields[key].name != '*' ) {
                    fieldData[key] = data * multipleFieldsMultiplier
                }
            }
        }
        
        // Compute the number of values
        fieldData.numValues = fieldData.values()*.size().max()

        fieldData
    }

    /**
     * Retrieve the field data for the selected field
     * @param study Study for which the data should be retrieved
     * @param samples Samples for which the data should be retrieved
     * @param fieldId ID of the field to return data for
     * @return A list of values of the selected field for all samples. If a value
     * 					could not be retrieved for a sample, null is returned. Examples:
     * 						[ 3, 6, null, 10 ] or [ "male", "male", "female", "female" ]
     */
    def getFieldData(study, samples, fieldId) {
        if (!fieldId)
            return null

        // Parse the fieldId as given by the user
        def parsedField = parseFieldId(fieldId);

        def data = []

        if (parsedField.source == "GSCF") {
            // Retrieve data from GSCF itself
            def closure = valueCallback(parsedField.type)

            if (closure) {
                samples.each { sample ->
                    // Retrieve the value for the selected field for this sample
                    def value = closure(sample, parsedField.name);

                    log.trace "  Retrieving " + parsedField.name + " for sample " + sample.UUID + ": " + value
                    
                    data << value;
                }
            } else {
                // TODO: Handle error properly
                // Closure could not be retrieved, probably because the type is incorrect
                data = samples.collect { return null }
                log.error("VisualizationController: getFieldData: Requested wrong field type: " + parsedField.type + ". Parsed field: " + parsedField)
            }
        } else {
            // Data must be retrieved from a module
            data = getModuleData(study, samples, parsedField.source, parsedField.name);
        }

        return data
    }

    /**
     * Retrieve data for a given field from a data module
     * @param study Study to retrieve data for
     * @param samples Samples to retrieve data for
     * @param source_module Name of the module to retrieve data from
     * @param fieldName Name of the measurement type to retrieve (i.e. measurementToken)
     * @return A list of values of the selected field for all samples. If a value
     * 						could not be retrieved for a sample, null is returned. Examples:
     * 							[ 3, 6, null, 10 ] or [ "male", "male", "female", "female" ]
     */
    protected def getModuleData(def study, def samples, def assay_id, String fieldName) {
        def data = getModuleData(study, samples, assay_id, [fieldName])
        return data[fieldName]
    }

    /**
     * Retrieve data for a given field from a data module
     * @param study Study to retrieve data for
     * @param samples Samples to retrieve data for
     * @param source_module Name of the module to retrieve data from
     * @param fieldName Name of the measurement type to retrieve (i.e. measurementToken)
     * @return A list of values of the selected field for all samples. If a value
     *                                          could not be retrieved for a sample, null is returned. Examples:
     *                                                  [ 3, 6, null, 10 ] or [ "male", "male", "female", "female" ]
     */
    protected def getModuleData(def study, def samples, def assay_id, List fieldNames) {
        def data = [:]

        // TODO: Handle values that should be retrieved from multiple assays
        def assay = Assay.get(assay_id);

        if (assay) {
            // Request for a particular assay and one or more features
            def urlVars = "verbose=true&assayToken=" + assay.UUID
            
            // However, if the fieldNames contains '*', data for all features 
            // should be retrieved
            if( !fieldNames.contains('*')) 
                urlVars += "&" + fieldNames.collect { "measurementToken=" + it.encodeAsURL() }.join("&")

            def callUrl
            try {
                callUrl = assay.module.baseUrl + "/rest/getMeasurementData"
                log.trace "Retrieving data from assay " + assay
                log.trace "  URL arguments: " + urlVars
                def json = moduleCommunicationService.callModuleMethod(assay.module.baseUrl, callUrl, urlVars, "POST");
                
                log.trace "Parsing data from assay " + assay
                
                if (json) {
                    // We could have multiple features, so we will created a map
                    // of not-null values, grouped by feature
                    def valueMap = json.findAll { !it.value.equals(null) }.groupBy { it.measurementToken }
                    
                    // Now return a list of measurements for all samples  
                    valueMap.each { measurementToken, measurements ->
                        // Group by sampleToken as well
                        def grouped = measurements.groupBy { it.sampleToken }
                        
                        // Now we can convert the map of values into a list of measurement values
                        // for the samples we need data for
                        data[measurementToken] = samples.collect { 
                            def value = grouped[it.UUID]?.value?.get(0)
                            
                            log.trace "  Retrieving MODULE " + measurementToken + " for sample " + it.UUID + ": " + value
                            
                            value 
                        }
                        
                    }

                    log.trace "Finished converting all data"
                } else {
                    // TODO: handle error
                    // Returns an empty list with as many elements as there are samples
                    fieldNames.each { 
                        data[it] = samples.collect { return null }
                    }
                }

            } catch (Exception e) {
                log.error("VisualizationController: getFields: " + e.getMessage(), e)
                //return returnError(404, "An error occured while trying to collect data from a module. Most likely, this module is offline.")
                return returnError(404, "Unfortunately, " + assay.module.name + " could not be reached. As a result, we cannot at this time visualize data contained in this module.")
            }
        } else {
            // TODO: Handle error correctly
            // Returns an empty list with as many elements as there are samples
            fieldNames.each { 
                data[it] = samples.collect { return null }
            }
        }
        
        return data
    }

    
    /**
     * Aggregates the data based on the requested aggregation on the categorical fields
     * @param data Map with data for each dimension as retrieved using getAllFieldData. For example:
     * 							[ "x": [ 3, 6, 8, 10 ], "y": [ "male", "male", "female", "female" ], "group": [ "US", "NL", "NL", "NL" ] ]
     * @param fieldInfo Map with field information for each dimension. For example:
     * 							[ "x": [ id: "abc", "type": NUMERICALDATA ], "y": [ "id": "def", "type": CATEGORICALDATA ] ]
     * @param aggregation Kind of aggregation requested
     * @return Data that is aggregated on the categorical fields
     * 							[ "x": [ 3, 6, null, 9 ], "y": [ "male", "male", "female", "female" ], "group": [ "US", "NL", "US", "NL" ] ]
     *
     */
    def aggregateData(data, fieldInfo, aggregation) {
        // Filter out null values
        data = filterNullValues(data)

        // If no aggregation is requested, we just return the original object (but filtering out null values)
        if (aggregation == "none") {
            return sortNonAggregatedData(data, ['x', 'group']);
        }

        // Determine the categorical fields
        def dimensions = ["categorical": [], "numerical": []];
        fieldInfo.each {
            // If fieldInfo value is NULL, the field is not requested
            if (it && it.value) {
                if ([CATEGORICALDATA, RELTIME, DATE].contains(it.value.fieldType)) {
                    dimensions.categorical << it.key
                } else {
                    dimensions.numerical << it.key
                }
            }
        }

        // Compose a map with aggregated data
        def aggregatedData = [:];
        fieldInfo.each { aggregatedData[it.key] = [] }

        // Loop through all categorical fields and aggregate the values for each combination
        if (dimensions.categorical.size() > 0) {
            return aggregate(data, dimensions.categorical, dimensions.numerical, aggregation, fieldInfo);
        } else {
            // No categorical dimensions. Just compute the aggregation for all values together
            def returnData = ["count": [data.numValues]];

            // Now compute the correct aggregation for each numerical dimension.
            dimensions.numerical.each { numericalDimension ->
                def currentData = data[numericalDimension];
                returnData[numericalDimension] = [computeAggregation(aggregation, currentData).value];
            }

            return returnData;
        }
    }

    /**
     * Sort the data that has not been aggregated by the given columns
     * @param data
     * @param sortBy
     * @return
     */
    protected def sortNonAggregatedData(data, sortBy) {
        if (!sortBy)
            return data;

        // First combine the lists within the data
        def combined = [];
        for (int i = 0; i < data.numValues; i++) {
            def element = [:]
            data.each {
                if (it.value instanceof Collection && i < it.value.size()) {
                    element[it.key] = it.value[i];
                }
            }

            combined << element;
        }

        // Now sort the combined element with a comparator
        def comparator = { a, b ->
            for (column in sortBy) {
                if (a[column] != null) {
                    if (a[column] != b[column]) {
                        if (a[column] instanceof Number)
                            return a[column] <=> b[column];
                        else
                            return a[column].toString() <=> b[column].toString();
                    }
                }
            }

            return 0;
        }

        combined.sort(comparator);

        // Put the elements back again. First empty the original lists
        data.each {
            if (it.value instanceof Collection) {
                it.value = [];
            }
        }

        combined.each { element ->
            element.each { key, value ->
                data[key] << value;
            }
        }

        return data;
    }

    /**
     * Filter null values from the different lists of data. The associated values in other lists will also be removed
     * @param data
     * @return Filtered data.
     *
     * [ 'x': [0, 2, 4], 'y': [1,null,2] ] will result in [ 'x': [0, 4], 'y': [1, 2] ]
     * [ 'x': [0, 2, 4], 'y': [1,null,2], 'z': [4, null, null] ] will result in [ 'x': [0], 'y': [1], 'z': [4] ]
     */
    protected def filterNullValues(data) {
        def filteredData = [:];

        // Create a copy of the data object
        data.each {
            filteredData[it.key] = it.value;
        }

        // Loop through all values and return all null-values (and the same indices on other elements
        int num = filteredData.numValues;
        filteredData.keySet().each { fieldName ->
            // If values are found, do filtering. If not, skip this one
            if (filteredData[fieldName] != null && filteredData[fieldName] instanceof Collection) {
                // Find all non-null values in this list
                def indices = filteredData[fieldName].findIndexValues { it != null };

                // Remove the non-null values from each data object
                filteredData.each { key, value ->
                    if (value && value instanceof Collection)
                        filteredData[key] = value[indices];
                }

                // Store the number of values
                num = indices.size();
            }
        }

        filteredData.numValues = num;

        return filteredData
    }

    /**
     * Aggregates the given data on the categorical dimensions.
     * @param data Initial data
     * @param categoricalDimensions List of categorical dimensions to group  by
     * @param numericalDimensions List of all numerical dimensions to compute the aggregation for
     * @param aggregation Type of aggregation requested
     * @param fieldInfo Information about the fields requested by the user	(e.g. [ "x": [ "id": 1, "fieldType": CATEGORICALDATA ] ] )
     * @param criteria The criteria the current aggregation must keep (e.g. "x": "male")
     * @param returnData Initial return object with the same keys as the data object, plus 'count'
     * @return
     */
    protected def aggregate(Map data, Collection categoricalDimensions, Collection numericalDimensions, String aggregation, fieldInfo, criteria = [:], returnData = null) {
        if (!categoricalDimensions)
            return data;

        // If no returndata is given, initialize the map
        if (returnData == null) {
            returnData = ["count": []]
            data.each { returnData[it.key] = [] }
        }

        def dimension = categoricalDimensions.head();

        // Determine the unique values on the categorical axis and sort by toString method
        log.trace( "Data on dimension " + dimension + " -> " + data[dimension] )
        def unique = data[dimension].flatten()
                .unique { it == null ? "null" : it.class.name + it.toString() }
                .sort {
            // Sort categoricaldata on its string value, but others (numerical, reltime, date)
            // on its real value
            switch (fieldInfo[dimension].fieldType) {
                case CATEGORICALDATA:
                    return it.toString()
                default:
                    return it
            }
        };

        // Make sure the null category is last
        unique = unique.findAll { it != null } + unique.findAll { it == null }

        unique.each { el ->
            // Use this element to search on
            criteria[dimension] = el;

            // If the list of categoricalDimensions is empty after this dimension, do the real work
            if (categoricalDimensions.size() == 1) {
                // Search for all elements in the numericaldimensions that belong to the current group
                // The current group is defined by the criteria object

                // We start with all indices belonging to this group
                def indices = 0..data.numValues;
                criteria.each { criterion ->
                    // Find the indices of the samples that belong to this group. if a sample belongs to multiple groups (i.e. if
                    // the samples groupAxis contains multiple values, is a collection), the value should be used in all groups.
                    def currentIndices = data[criterion.key].findIndexValues { it instanceof Collection ? it.contains(criterion.value) : it == criterion.value };
                    indices = indices.intersect(currentIndices);

                    // Store the value for the criterion in the returnData object
                    returnData[criterion.key] << criterion.value;
                }

                // If no numericalDimension is asked for, no aggregation is possible. For that reason, we
                // also return counts
                returnData["count"] << indices.size();

                // Now compute the correct aggregation for each numerical dimension.
                numericalDimensions.each { numericalDimension ->
                    def currentData = data[numericalDimension][indices];
                    returnData[numericalDimension] << computeAggregation(aggregation, currentData).value;
                }

            } else {
                returnData = aggregate(data, categoricalDimensions.tail(), numericalDimensions, aggregation, fieldInfo, criteria, returnData);
            }
        }

        return returnData;
    }

    /**
     * Compute the aggregation for a list of values
     * @param aggregation
     * @param currentData
     * @return
     */
	protected def computeAggregation(String aggregation, List currentData) {
        switch (aggregation) {
            case "count":
                return computeCount(currentData);
                break;
            case "median":
                return computePercentile(currentData, 50);
                break;
            case "sum":
                return computeSum(currentData);
                break;
            case "average":
            default:
                // Default is "average"
                return computeMeanAndError(currentData);
                break;
        }
    }

    /**
     * Formats the grouped data in such a way that the clientside visualization method
     * can handle the data correctly.
     * @param groupedData Data that has been grouped using the groupFields method
     * @param fieldData Map with key-value pairs determining the name and fieldId to retrieve data for. Example:
     * 							[ "x": { "id": ... }, "y": { "id": "field-id-3" }, "group": { "id": "field-id-6" } ]
     * @param errorName Key in the output map where 'error' values (SEM) are stored. Defaults to "error" 	 *
     * @return A map like the following:
     *{
     "type": "barchart",
     "xaxis": { "title": "quarter 2011", "unit": "" },
     "yaxis": { "title": "temperature", "unit": "degrees C" },
     "series": [{
     "name": "series name",
     "y": [ 5.1, 3.1, 20.6, 15.4 ],
     "x": [ "Q1", "Q2", "Q3", "Q4" ],
     "error": [ 0.5, 0.2, 0.4, 0.5 ]
     },
     ]
     }*
     */
    protected def formatData(type, groupedData, fieldInfo, xAxis = "x", yAxis = "y", serieAxis = "group", errorName = "error") {
        // Format categorical axes by setting the names correct
        fieldInfo.each { field, info ->
            if (field && info) {
                groupedData[field] = renderFieldsHumanReadable(groupedData[field], info.fieldType)
            }
        }

        // TODO: Handle name and unit of fields correctly
        def xAxisTypeString = dataTypeString(fieldInfo[xAxis]?.fieldType)
        def yAxisTypeString = dataTypeString(fieldInfo[yAxis]?.fieldType)
        def serieAxisTypeString = dataTypeString(fieldInfo[serieAxis]?.fieldType)

        // Create a return object
        def return_data = [:]
        return_data["type"] = type
        return_data.put("xaxis", ["title": fieldInfo[xAxis]?.name, "unit": fieldInfo[xAxis]?.unit, "type": xAxisTypeString])
        return_data.put("yaxis", ["title": fieldInfo[yAxis]?.name, "unit": fieldInfo[yAxis]?.unit, "type": yAxisTypeString])
        return_data.put("groupaxis", ["title": fieldInfo[serieAxis]?.name, "unit": fieldInfo[serieAxis]?.unit, "type": serieAxisTypeString])

        if (type == "table") {
            // Determine the lists on both axes. The strange addition is done because the unique() method
            // alters the object itself, instead of only returning a unique list
            def xAxisData = ([] + groupedData[xAxis]).unique()
            def yAxisData = ([] + groupedData[yAxis]).unique()

            if (!fieldInfo[serieAxis]) {
                // If no value has been chosen on the serieAxis, we should show the counts for only one serie
                def tableData = formatTableData(groupedData, xAxisData, yAxisData, xAxis, yAxis, "count");

                return_data.put("series", [[
                        "name": "count",
                        "x": xAxisData,
                        "y": yAxisData,
                        "data": tableData
                ]])
            } else if (fieldInfo[serieAxis].fieldType == NUMERICALDATA) {
                // If no value has been chosen on the serieAxis, we should show the counts for only one serie
                def tableData = formatTableData(groupedData, xAxisData, yAxisData, xAxis, yAxis, serieAxis);

                // If a numerical field has been chosen on the serieAxis, we should show the requested aggregation
                // for only one serie
                return_data.put("series", [[
                        "name": fieldInfo[xAxis].name,
                        "x": xAxisData,
                        "y": yAxisData,
                        "data": tableData
                ]])
            } else {
                // If a categorical field has been chosen on the serieAxis, we should create a table for each serie
                // with counts as data. That table should include all data for that serie
                return_data["series"] = [];

                // The strange addition is done because the unique() method
                // alters the object itself, instead of only returning a unique list
                def uniqueSeries = ([] + groupedData[serieAxis]).unique();

                uniqueSeries.each { serie ->
                    def indices = groupedData[serieAxis].findIndexValues { it == serie }

                    // If no value has been chosen on the serieAxis, we should show the counts for only one serie
                    def tableData = formatTableData(groupedData, xAxisData, yAxisData, xAxis, yAxis, "count", indices);

                    return_data["series"] << [
                            "name": serie,
                            "x": xAxisData,
                            "y": yAxisData,
                            "data": tableData,
                    ]
                }
            }

        } else if (type == "boxplot") {
            return_data["series"] = [];
            HashMap dataMap = new HashMap();

            if (fieldInfo[serieAxis] && fieldInfo[serieAxis].fieldType == NUMERICALDATA) {
                // No numerical series field is allowed in a chart.
                throw new Exception("No numerical series field is allowed here.");
            }
            
            def boxplotData = computeBoxplotData(groupedData[xAxis], groupedData[yAxis], groupedData[serieAxis])
            
            boxplotData.each { serie, serieData ->
                return_data["series"] << [
                    name: serie,
                    x: serieData.keySet(),
                    y: serieData.values()
                ]
            }
            
        } else {
            // For a horizontal barchart, the two axes should be swapped
            if (type == "horizontal_barchart") {
                def tmp = xAxis
                xAxis = yAxis
                yAxis = tmp
            }

            if (!fieldInfo[serieAxis]) {
                // If no series field has defined, we return all data in one serie
                return_data.put("series", [[
                        "name": "count",
                        "x": groupedData[xAxis],
                        "y": groupedData[yAxis],
                ]])
            } else if (fieldInfo[serieAxis].fieldType == NUMERICALDATA) {
                // No numerical series field is allowed in a chart.
                throw new Exception("No numerical series field is allowed here.");
            } else {
                // If a categorical field has been chosen on the serieAxis, we should create a group for each serie
                // with the correct values, belonging to that serie.
                return_data["series"] = [];

                // The unique method alters the original object, so we
                // create a new object
                def uniqueSeries = ([] + groupedData[serieAxis]).unique();

                uniqueSeries.each { serie ->
                    def indices = groupedData[serieAxis].findIndexValues { it == serie }
                    return_data["series"] << [
                            "name": serie,
                            "x": groupedData[xAxis][indices],
                            "y": groupedData[yAxis][indices]
                    ]
                }
            }
        }

        return return_data;
    }

    /**
     * Compute boxplot data
     */
    protected def computeBoxplotData( xAxisData, yAxisData, groupAxisData = null ) {
        def groupedData = [:]
        def boxplotData = [:]
        
        // Group the values on the y-axis
        yAxisData.eachWithIndex { value, idx ->
            def key = xAxisData[idx]
            def groupKey = groupAxisData ? groupAxisData[idx] : "boxplot"
            
            if( !groupedData[groupKey] )
                groupedData[groupKey] = [:]
            
            if( !groupedData[groupKey][key] )
                groupedData[groupKey][key] = []

            groupedData[groupKey][key] << value
        }
        
        groupedData.each { serie, groupedValues->
            boxplotData[serie] = [:]
            groupedValues.each { key, values ->
                def objInfos = computePercentile(values, 50);
                double dblMEDIAN = objInfos.get("value");
                double Q1 = computePercentile(values, 25).get("value");
                double Q3 = computePercentile(values, 75).get("value");
    
                // Calcultate 1.5* inter-quartile-distance
                double dblIQD = (Q3 - Q1) * 1.5;
    
                // Set min and max to be the whiskers, so the outliers do not change the axes
                double min = (dblMEDIAN - dblIQD)
                double max = (dblMEDIAN + dblIQD)
                
                boxplotData[serie][key] = [key, max, (dblMEDIAN + dblIQD), Q3, dblMEDIAN, Q1, (dblMEDIAN - dblIQD), min]
            }
        }
    
        return boxplotData
    }
    
    /**
     * Formats the requested data for a table
     * @param groupedData
     * @param xAxisData
     * @param yAxisData
     * @param xAxis
     * @param yAxis
     * @param dataAxis
     * @return
     */
	protected def formatTableData(groupedData, xAxisData, yAxisData, xAxis, yAxis, dataAxis, serieIndices = null) {
        def tableData = []

        xAxisData.each { x ->
            def colData = []

            def indices = groupedData[xAxis].findIndexValues { it == x }

            // If serieIndices are given, intersect the indices
            if (serieIndices != null)
                indices = indices.intersect(serieIndices);

            yAxisData.each { y ->
                def index = indices.intersect(groupedData[yAxis].findIndexValues { it == y });

                if (index.size()) {
                    colData << groupedData[dataAxis][(int) index[0]]
                }
            }
            tableData << colData;
        }

        return tableData;
    }

    /**
     * If the input variable 'data' contains dates or times according to input variable 'fieldInfo', these dates and times are converted to a human-readable version.
     * @param data The list of items that needs to be checked/converted
     * @param axisType As determined by determineFieldType
     * @return The input variable 'data', with it's date and time elements converted.
     * @see determineFieldType
     */
	protected def renderFieldsHumanReadable(data, axisType) {
        switch (axisType) {
            case RELTIME:
                return renderTimesHumanReadable(data)
            case DATE:
                return renderDatesHumanReadable(data)
            case CATEGORICALDATA:
                return data.collect { it.toString() }
            case NUMERICALDATA:
            default:
                return data;
        }
    }

    /**
     * Takes a one-dimensional list, returns the list with the appropriate items converted to a human readable string
     * @param data
     * @return
     */
	protected def renderTimesHumanReadable(data) {
        def tmpTimeContainer = []
        data.each {
            if (it instanceof Number) {
                try {
                    tmpTimeContainer << new RelTime(it).toPrettyString()
                } catch (IllegalArgumentException e) {
                    tmpTimeContainer << it
                }
            } else {
                tmpTimeContainer << it // To handle items such as 'unknown'
            }
        }
        return tmpTimeContainer
    }

    /**
     * Takes a one-dimensional list, returns the list with the appropriate items converted to a human readable string
     * @param data
     * @return
     */
	protected def renderDatesHumanReadable(data) {
        def tmpDateContainer = []
        data.each {
            if (it instanceof Number) {
                try {
                    tmpDateContainer << new java.util.Date((Long) it).toString()
                } catch (IllegalArgumentException e) {
                    tmpDateContainer << it
                }
            } else {
                tmpDateContainer << it // To handle items such as 'unknown'
            }
        }
        return tmpDateContainer
    }
    /**
     * Returns a closure for the given entitytype that determines the value for a criterion
     * on the given object. The closure receives two parameters: the sample and a field.
     *
     * For example:
     * 		How can one retrieve the value for subject.name, given a sample? This can be done by
     * 		returning the field values sample.parentSubject:
     *{ sample, field -> return getFieldValue( sample.parentSubject, field ) }* @return Closure that retrieves the value for a field and the given field
     */
    protected Closure valueCallback(String entity) {
        switch (entity) {
            case "Study":
            case "studies":
                return { sample, field -> return getFieldValue(sample.parent, field) }
            case "Subject":
            case "subjects":
                return { sample, field -> return getFieldValue(sample.parentSubject, field); }
            case "Sample":
            case "samples":
                return { sample, field -> return getFieldValue(sample, field) }
            case "Event":
            case "events":
                return { sample, field ->
                    if (!sample || !sample.parentSubjectEventGroup || !sample.parentSubjectEventGroup.eventGroup || !sample.parentSubjectEventGroup.eventGroup.eventInstances )
                        return null
                       
                    def events = sample.parentSubjectEventGroup.eventGroup.eventInstances*.event.findAll()
                    return events?.collect { getFieldValue(it, field) };
                }
            case "EventGroup":
            case "eventGroups":
                return { sample, field ->
                    if (!sample || !sample.parentSubjectEventGroup || !sample.parentSubjectEventGroup.eventGroup)
                        return null

                    // For eventgroups only the name is supported
                    if (field == "name")
                        return sample.parentSubjectEventGroup.eventGroup.name
                    else
                        return null
                }

            case "SamplingEvent":
            case "samplingEvents":
                return { sample, field -> return getFieldValue(sample?.parentEvent?.event, field); }
            case "Assay":
            case "assays":
                return { sample, field ->
                    def sampleAssays = Assay.findByParent(sample.parent).findAll { it.samples?.contains(sample) };
                    if (sampleAssays && sampleAssays.size() > 0)
                        return sampleAssays.collect { getFieldValue(it, field) }
                    else
                        return null
                }
        }
    }

    /**
     * Returns the domain object that should be used with the given entity string
     *
     * For example:
     * 		What object should be consulted if the user asks for "studies"
     * 		Response: Study
     * @return Domain object that should be used with the given entity string
     */
    protected def domainObjectCallback(String entity) {
        switch (entity) {
            case "Study":
            case "studies":
                return Study
            case "Subject":
            case "subjects":
                return Subject
            case "Sample":
            case "samples":
                return Sample
            case "Event":
            case "events":
                return Event
            case "SamplingEvent":
            case "samplingEvents":
                return SamplingEvent
            case "Assay":
            case "assays":
                return Assay
            case "EventGroup":
            case "eventGroups":
                return EventGroup

        }
    }

    /**
     * Returns the templates for objects within the given study that should be used with the given entity string
     *
     * For example:
     * 		What object should be consulted if the user asks for "samples"
     * 		Response: all templates used for samples in the given study
     * @return List of templates that should be used with the given entity string
     */
    protected def templateObjectCallback(String entity, Study study) {
        switch (entity) {
            case "Study":
            case "studies":
                return study?.template
            case "Subject":
            case "subjects":
                return study?.giveSubjectTemplates()
            case "Sample":
            case "samples":
                return study?.giveSampleTemplates()
            case "Event":
            case "events":
                return study?.giveEventTemplates()
            case "SamplingEvent":
            case "samplingEvents":
                return study?.giveSamplingEventTemplates()
            case "Assay":
            case "assays":
                return study?.giveAllAssayTemplates()
        }
    }

    /**
     * Computes the mean value and Standard Error of the mean (SEM) for the given values
     * @param values List of values to compute the mean and SEM for. Strings and null
     * 					values are ignored
     * @return Map with two keys: 'value' and 'error'
     */
    protected Map computeMeanAndError(values) {
        // TODO: Handle the case that one of the values is a list. In that case,
        // all values should be taken into account.
        def mean = computeMean(values);
        def error = computeSEM(values, mean);

        return [
                "value": mean,
                "error": error
        ]
    }

    /**
     * Computes the mean of the given values. Values that can not be parsed to a number
     * are ignored. If no values are given, null is returned.
     * @param values List of values to compute the mean for
     * @return Arithmetic mean of the values
     */
    protected def computeMean(List values) {
        def sumOfValues = 0;
        def sizeOfValues = 0;
        values.each { value ->
            def num = getNumericValue(value);
            if (num != null) {
                sumOfValues += num;
                sizeOfValues++
            }
        }

        if (sizeOfValues > 0)
            return sumOfValues / sizeOfValues;
        else
            return null;
    }

    /**
     * Computes the standard error of mean of the given values.
     * Values that can not be parsed to a number are ignored.
     * If no values are given, null is returned.
     * @param values List of values to compute the standard deviation for
     * @param mean Mean of the list (if already computed). If not given, the mean
     * 					will be computed using the computeMean method
     * @return Standard error of the mean of the values or 0 if no values can be used.
     */
    protected def computeSEM(List values, def mean = null) {
        if (mean == null)
            mean = computeMean(values)

        def sumOfDifferences = 0;
        def sizeOfValues = 0;
        values.each { value ->
            def num = getNumericValue(value);
            if (num != null) {
                sumOfDifferences += Math.pow(num - mean, 2);
                sizeOfValues++
            }
        }

        if (sizeOfValues > 0) {
            def std = Math.sqrt(sumOfDifferences / sizeOfValues);
            return std / Math.sqrt(sizeOfValues);
        } else {
            return null;
        }
    }

    /**
     * Computes value of a percentile of the given values. Values that can not be parsed to a number
     * are ignored. If no values are given, null is returned.
     * @param values List of values to compute the percentile for
     * @param Percentile Integer that indicates which percentile to calculae
     *                   Example: Percentile=50 calculates the median,
     *                            Percentile=25 calculates Q1
     *                            Percentile=75 calculates Q3
     * @return The value at the Percentile of the values
     */
    protected def computePercentile(List values, int Percentile) {
        def listOfValues = [];
        values.each { value ->
            def num = getNumericValue(value);
            if (num != null) {
                listOfValues << num;
            }
        }

        listOfValues.sort();

        def listSize = listOfValues.size() - 1;

        def objReturn = null;
        def objMin = null;
        def objMax = null;

        def dblFactor = Percentile / 100;

        if (listSize >= 0) {
            def intPointer = (int) Math.abs(listSize * dblFactor);
            if (intPointer == listSize * dblFactor) {
                // If we exactly end up at an item, take this item
                objReturn = listOfValues.get(intPointer);
            } else {
                // If we don't exactly end up at an item, take the mean of the 2 adjecent values
                objReturn = (listOfValues.get(intPointer) + listOfValues.get(intPointer + 1)) / 2;
            }

            objMin = listOfValues.get(0);
            objMax = listOfValues.get(listSize);
        }

        return ["value": objReturn, "min": objMin, "max": objMax];
    }

    /**
     * Computes the count of the given values. Values that can not be parsed to a number
     * are ignored. If no values are given, null is returned.
     * @param values List of values to compute the count for
     * @return Count of the values
     */
    protected def computeCount(List values) {
        def sumOfValues = 0;
        def sizeOfValues = 0;
        values.each { value ->
            def num = getNumericValue(value);
            if (num != null) {
                sumOfValues += num;
                sizeOfValues++
            }
        }

        if (sizeOfValues > 0)
            return ["value": sizeOfValues];
        else
            return ["value": null];
    }

    /**
     * Computes the sum of the given values. Values that can not be parsed to a number
     * are ignored. If no values are given, null is returned.
     * @param values List of values to compute the sum for
     * @return Arithmetic sum of the values
     */
    protected def computeSum(List values) {
        def sumOfValues = 0;
        def sizeOfValues = 0;
        values.each { value ->
            def num = getNumericValue(value);
            if (num != null) {
                sumOfValues += num;
                sizeOfValues++
            }
        }

        if (sizeOfValues > 0)
            return ["value": sumOfValues];
        else
            return ["value": null];
    }

    /**
     * Return the numeric value of the given object, or null if no numeric value could be returned
     * @param value Object to return the value for
     * @return Number that represents the given value
     */
    protected Number getNumericValue(value) {
        // TODO: handle special types of values
        if (value instanceof Number) {
            return value;
        } else if (value instanceof RelTime) {
            return value.value;
        }

        return null
    }

    /**
     * Returns a field for a given templateentity
     * @param object TemplateEntity (or subclass) to retrieve data for
     * @param fieldName Name of the field to return data for.
     * @return Value of the field or null if the value could not be retrieved
     */
    protected def getFieldValue(TemplateEntity object, String fieldName) {
        if (!object || !fieldName)
            return null;

        try {
            return object.getFieldValue(fieldName);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parses a fieldId that has been created earlier by createFieldId
     * @param fieldId FieldId to parse
     * @return Map with attributes of the selected field. Keys are 'name', 'id', 'source' and 'type'
     * @see createFieldId
     */
    protected Map parseFieldId(String fieldId) {
        def attrs = [:]

        if (!fieldId)
            return null;

        def parts = fieldId.split(",", 5)

        attrs = [
                "id": new String(parts[0].decodeBase64()),
                "name": new String(parts[1].decodeBase64()),
                "source": new String(parts[2].decodeBase64()),
                "type": new String(parts[3].decodeBase64()),
                "unit": parts.length > 4 ? new String(parts[4].decodeBase64()) : null,
                "fieldId": fieldId
        ]

        return attrs
    }
    
    /**
     * Convenience method for field IDs that have already been parsed
     */
    protected Map parseFieldId(Map fieldId) {
        fieldId
    }
    
    /**
     * Parse multiple field IDs at once
     */
    protected Map parseFieldIds(Map fieldIds) {
        fieldIds.collectEntries { k, v ->
            def parsedField = parseFieldId(v)
            if( parsedField ) {
                parsedField.key = k
                [k, parsedField]
            } else {
                [:]
            }
        }
    }

    /**
     * Returns a string representation of the given fieldType, which can be sent to the userinterface
     * @param fieldType CATEGORICALDATA, DATE, RELTIME, NUMERICALDATA
     * @return String representation
     */
    protected String dataTypeString(fieldType) {
        return (fieldType == CATEGORICALDATA || fieldType == DATE || fieldType == RELTIME ? "categorical" : "numerical")
    }

    /**
     * Create a fieldId based on the given attributes
     * @param attrs Map of attributes for this field. Keys may be 'name', 'id', 'source' and 'type'
     * @return Unique field ID for these parameters
     * @see parseFieldId
     */
    protected String createFieldId(Map attrs) {
        // TODO: What if one of the attributes contains a comma?
        def name = attrs.name.toString();
        def id = (attrs.id ?: name).toString();
        def source = attrs.source.toString();
        def type = (attrs.type ?: "").toString();
        def unit = (attrs.unit ?: "").toString();

        return id.bytes.encodeBase64().toString() + "," +
                name.bytes.encodeBase64().toString() + "," +
                source.bytes.encodeBase64().toString() + "," +
                type.bytes.encodeBase64().toString() + "," +
                unit.bytes.encodeBase64().toString();
    }

    /**
     * Set the response code and an error message
     * @param code HTTP status code
     * @param msg Error message, string
     */
    protected void returnError(code, msg) {
        response.sendError(code, msg)
    }

    /**
     * Determines what type of data a field contains
     * @param studyId An id that can be used with Study.get/1 to retrieve a study from the database
     * @param fieldId The field id as returned from the client, will be used to retrieve the data required to determine the type of data a field contains
     * @param inputData Optional parameter that contains the data we are computing the type of. When including in the function call we do not need to request data from a module, should the data belong to a module
     * @return Either CATEGORICALDATA, NUMERICALDATA, DATE or RELTIME
     */
    protected int determineFieldType(studyId, fieldId, inputData = null) {
        def parsedField = parseFieldId(fieldId);
        def study = Study.get(studyId)
        def data = []

        // If the fieldId is incorrect, or the field is not asked for, return
        // CATEGORICALDATA
        if (!parsedField) {
            return CATEGORICALDATA;
        }
        try {
            if (parsedField.source == "GSCF") {
                if (parsedField.id.isNumber()) {
                    return determineCategoryFromTemplateFieldId(parsedField.id)
                } else { // Domainfield or memberclass
                    def callback = domainObjectCallback(parsedField.type)
                    // Can the field be found in the domainFields as well? If so, treat it as a template field, so that dates and times can be properly rendered in a human-readable fashion

                    if (callback.domainFields && callback?.domainFields?.name?.contains(parsedField.name.toString())) {
                        // Use the associated templateField to determine the field type
                        return determineCategoryFromTemplateField(
                                callback?.domainFields.find { it.name.equals(parsedField.name.toString()) }
                        )
                    }
                    // Apparently it is not a templatefield as well as a memberclass

                    def field = callback?.declaredFields.find { it.name == parsedField.name };
                    if (field) {
                        return determineCategoryFromClass(field.getType())
                    } else {
                        // TODO: how do we communicate this to the user? Do we allow the process to proceed?
                        log.error("The user asked for field " + parsedField.type + " - " + parsedField.name + ", but it doesn't exist.");
                    }
                }
            } else {
                if( parsedField.name == '*' ) {
                    // Treat 'all features' as numerical data
                    return parsedField.key == 'group' ? CATEGORICALDATA : NUMERICALDATA
                } else if (inputData == null) { 
                    // If we did not get data, we need to request it from the module first
                    data = getModuleData(study, study.getSamples(), parsedField.source, parsedField.name);
                    return determineCategoryFromData(data)
                } else {
                    return determineCategoryFromData(inputData)
                }
            }
        } catch (Exception e) {
            log.error("VisualizationController: determineFieldType: " + e.getMessage(), e)
            // If we cannot figure out what kind of a datatype a piece of data is, we treat it as categorical data
            return CATEGORICALDATA
        }
    }
    
    /**
     * Determines what type of data a field contains
     * @param studyId An id that can be used with Study.get/1 to retrieve a study from the database
     * @param fieldIds Map of the field ids as returned from the client, will be used to retrieve the data required to determine the type of data a field contains
     * @return Map with either CATEGORICALDATA, NUMERICALDATA, DATE or RELTIME
     */
    protected Map determineFieldTypes(studyId, fieldIds) {
        // By default, set all fields to NULL
        def fieldTypes = fieldIds.collectEntries { k, v -> [k, null] }
        def study = Study.get(studyId)
        
        // Combine fields per module
        def parsedFields = parseFieldIds(fieldIds)
        
        // Only use the fields that we could parse.
        parsedFields = parsedFields.values().findAll().groupBy { it.source }
        
        // Loop through all fields, and retrieve the data 
        // from the modules in a single call. If data from
        // GSCF is requested, just run the default determineFieldTypes method
        try { 
            parsedFields.each { source, fields ->
                if(source == "GSCF") {
                    // Determine field type for each field independently
                    fields.each {
                        fieldTypes[it.key] = determineFieldType(studyId, it.fieldId)
                    }
                } else {
                    // Data is in a module. If the name '*' is given, don't take it into account 
                    def fieldNames = fields*.name - '*'
                    def moduleData = getModuleData(study, study.getSamples(), source, fieldNames);
                    
                    fields.each {
                        // If all features from an assay are requested, treat is as numerical data
                        // However, if the feature names are requested, it is categorical
                        if( it.name == '*' )
                            fieldTypes[it.key] = it.key == 'group' ? CATEGORICALDATA : NUMERICALDATA
                        else
                            fieldTypes[it.key] = determineCategoryFromData(moduleData[it.name]) 
                    }
                }
            }
        } catch( Exception e ) {
            log.error( "Exception in getFieldTypes method", e )
        } 
        
        fieldTypes
    }

    /**
     * Determines a field category, based on the input parameter 'classObject', which is an instance of type 'class'
     * @param classObject
     * @return Either CATEGORICALDATA of NUMERICALDATA
     */
    protected int determineCategoryFromClass(classObject) {
        log.trace "Determine category from class: " + classObject
        switch (classObject) {
            case java.lang.String:
            case org.dbnp.gdt.Term:
            case org.dbnp.gdt.TemplateFieldListItem:
                return CATEGORICALDATA;
            default:
                return NUMERICALDATA;
        }
    }

    /**
     * Determines a field category based on the actual data contained in the field. The parameter 'inputObject' can be a single item with a toString() function, or a collection of such items.
     * @param inputObject Either a single item, or a collection of items
     * @return Either CATEGORICALDATA of NUMERICALDATA
     */
    protected int determineCategoryFromData(inputObject) {
        def results = []

        if (inputObject instanceof Collection) {
            // This data is more complex than a single value, so we will call ourselves again so we c
            inputObject.each {
                if (it != null)
                    results << determineCategoryFromData(it)
            }
        } else {
            // Unfortunately, the JSON null object doesn't resolve to false or equals null. For that reason, we
            // exclude those objects explicitly here.
            if (inputObject != null && inputObject?.class != org.codehaus.groovy.grails.web.json.JSONObject$Null) {
                if (inputObject.toString().isDouble()) {
                    results << NUMERICALDATA
                } else {
                    results << CATEGORICALDATA
                }
            }
        }

        results.unique()

        if (results.size() > 1) {
            // If we cannot figure out what kind of a datatype a piece of data is, we treat it as categorical data
            results[0] = CATEGORICALDATA
        } else if (results.size() == 0) {
            // If the list is empty, return the numerical type. If it is the only value, if will
            // be discarded later on. If there are more entries (e.g part of a collection)
            // the values will be regarded as numerical, if the other values are numerical
            results[0] = NUMERICALDATA
        }

        return results[0]
    }

    /**
     * Determines a field category, based on the TemplateFieldId of a Templatefield
     * @param id A database ID for a TemplateField
     * @return Either CATEGORICALDATA of NUMERICALDATA
     */
    protected int determineCategoryFromTemplateFieldId(id) {
        TemplateField tf = TemplateField.get(id)
        return determineCategoryFromTemplateField(tf)
    }

    /**
     * Determines a field category, based on the TemplateFieldType of a Templatefield
     * @param id A database ID for a TemplateField
     * @return Either CATEGORICALDATA of NUMERICALDATA
     */
    protected int determineCategoryFromTemplateField(tf) {

        if (tf.type == TemplateFieldType.DOUBLE || tf.type == TemplateFieldType.LONG) {
            log.trace "GSCF templatefield: NUMERICALDATA (" + NUMERICALDATA + ") (based on " + tf.type + ")"
            return NUMERICALDATA
        }
        if (tf.type == TemplateFieldType.DATE) {
            log.trace "GSCF templatefield: DATE (" + DATE + ") (based on " + tf.type + ")"
            return DATE
        }
        if (tf.type == TemplateFieldType.RELTIME) {
            log.trace "GSCF templatefield: RELTIME (" + RELTIME + ") (based on " + tf.type + ")"
            return RELTIME
        }
        log.trace "GSCF templatefield: CATEGORICALDATA (" + CATEGORICALDATA + ") (based on " + tf.type + ")"
        return CATEGORICALDATA
    }
    /**
     * Properly formats the object that will be returned to the client. Also adds an informational message, if that message has been set by a function. Resets the informational message to the empty String.
     * @param returnData The object containing the data
     * @return results A JSON object
     */
    protected void sendResults(returnData) {
        def results = [:]
        if (infoMessage.size() != 0) {
            results.put("infoMessage", infoMessage)
            infoMessage = []
        }
        results.put("returnData", returnData)
        render results as JSON
    }

    /**
     * Properly formats an informational message that will be returned to the client. Resets the informational message to the empty String.
     * @param returnData The object containing the data
     * @return results A JSON object
     */
    protected void sendInfoMessage() {
        def results = [:]
        results.put("infoMessage", infoMessage)
        infoMessage = []
        render results as JSON
    }

    /**
     * Adds a new message to the infoMessage
     * @param message The information that needs to be added to the infoMessage
     */
    protected void setInfoMessage(message) {
        infoMessage.add(message)
        log.trace "setInfoMessage: " + infoMessage
    }

    /**
     * Adds a message to the infoMessage that gives the client information about offline modules
     */
    protected void setInfoMessageOfflineModules() {
        infoMessageOfflineModules.unique()
        if (infoMessageOfflineModules.size() > 0) {
            String message = "Unfortunately"
            infoMessageOfflineModules.eachWithIndex { it, index ->
                if (index == (infoMessageOfflineModules.size() - 2)) {
                    message += ', the ' + it + ' and '
                } else {
                    if (index == (infoMessageOfflineModules.size() - 1)) {
                        message += ' the ' + it
                    } else {
                        message += ', the ' + it
                    }
                }
            }
            message += " could not be reached. As a result, we cannot at this time visualize data contained in "
            if (infoMessageOfflineModules.size() > 1) {
                message += "these modules."
            } else {
                message += "this module."
            }
            setInfoMessage(message)
        }
        infoMessageOfflineModules = []
    }

    /**
     * Combine several blocks of formatted data into one. These blocks have been formatted by the formatData function.
     * @param inputData Contains a list of maps, of the following format
     *          - a key 'series' containing a list, that contains one or more maps, which contain the following:
     *            - a key 'name', containing, for example, a feature name or field name
     *            - a key 'y', containing a list of y-values
     *            - a key 'error', containing a list of, for example, standard deviation or standard error of the mean values,
     */
    protected def formatCategoryData(inputData) {
        // NOTE: This function is no longer up to date with the current inputData layout.
        def series = []
        inputData.eachWithIndex { it, i ->
            series << ['name': it['yaxis']['title'], 'y': it['series']['y'][0], 'error': it['series']['error'][0]]
        }
        def ret = [:]
        ret.put('type', inputData[0]['type'])
        ret.put('x', inputData[0]['x'])
        ret.put('yaxis', ['title': 'title', 'unit': ''])
        ret.put('xaxis', inputData[0]['xaxis'])
        ret.put('series', series)
        return ret
    }

    /**
     * Given two objects of either CATEGORICALDATA or NUMERICALDATA
     * @param rowType The type of the data that has been selected for the row, either CATEGORICALDATA or NUMERICALDATA
     * @param columnType The type of the data that has been selected for the column, either CATEGORICALDATA or NUMERICALDATA
     * @return
     */
    protected def determineVisualizationTypes(rowType, columnType) {
        def types = []

        if (rowType == CATEGORICALDATA || rowType == DATE || rowType == RELTIME) {
            if (columnType == CATEGORICALDATA || columnType == DATE || columnType == RELTIME) {
                types = [["id": "table", "name": "Table"]];
            } else {    // NUMERICALDATA
                types = [["id": "horizontal_barchart", "name": "Horizontal barchart"]];
            }
        } else {    // NUMERICALDATA
            if (columnType == CATEGORICALDATA || columnType == DATE || columnType == RELTIME) {
                types = [["id": "barchart", "name": "Barchart"], ["id": "linechart", "name": "Linechart"], ["id": "boxplot", "name": "Boxplot"]];
            } else {
                // Only allow scatterplots for numerical/numerical charts
                types = [["id": "scatterplot", "name": "Scatterplot"]];
            }
        }
        return types
    }

    /**
     * Returns the types of aggregation possible for the given two objects of either CATEGORICALDATA or NUMERICALDATA
     * @param rowType The type of the data that has been selected for the row, either CATEGORICALDATA or NUMERICALDATA
     * @param columnType The type of the data that has been selected for the column, either CATEGORICALDATA or NUMERICALDATA
     * @param groupType The type of the data that has been selected for the grouping, either CATEGORICALDATA or NUMERICALDATA
     * @return
     */
    protected def determineAggregationTypes(rowType, columnType, groupType = null) {
        // A list of all aggregation types. By default, every item is possible
        def types = [
                ["id": "average", "name": "Average", "disabled": false],
                ["id": "count", "name": "Count", "disabled": false],
                ["id": "median", "name": "Median", "disabled": false],
                ["id": "none", "name": "No aggregation", "disabled": false],
                ["id": "sum", "name": "Sum", "disabled": false],
        ]

        // Normally, all aggregation types are possible, with three exceptions:
        // 		Categorical data on both axes. In that case, we don't have anything to aggregate, so we can only count
        //		Grouping on a numerical field is not possible. In that case, it is ignored
        //			Grouping on a numerical field with categorical data on both axes (table) enabled aggregation,
        //			In that case we can aggregate on the numerical field.
        if (rowType == CATEGORICALDATA || rowType == DATE || rowType == RELTIME) {
            if (columnType == CATEGORICALDATA || columnType == DATE || columnType == RELTIME) {

                if (groupType == NUMERICALDATA) {
                    // Disable 'none', since that can not be visualized
                    types.each {
                        if (it.id == "none")
                            it.disabled = true
                    }
                } else {
                    // Disable everything but 'count'
                    types.each {
                        if (it.id != "count")
                            it.disabled = true
                    }
                }
            }
        }
        
        // Numerical data on both axes only allows scatterplot. 
        if( rowType == NUMERICALDATA && columnType == NUMERICALDATA ) {
            println "   --- groupType: " + groupType + " / " + CATEGORICALDATA
            // When not grouping, disable aggregation
            if( groupType != CATEGORICALDATA ) {
                types.each {
                    if( it.id != "none" )
                        it.disabled = true
                }
            }
        }
        
        println "-------------- TYPES --------------------"
        types.each { println it }
        return types
    }
}
