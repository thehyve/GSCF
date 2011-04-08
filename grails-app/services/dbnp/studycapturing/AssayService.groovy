/**
 * AssayService Service
 *
 * @author  s.h.sikkema@gmail.com
 * @since	20101216
 * @package	dbnp.studycapturing
 *
 * Revision information:
 * $Rev$
 * $Author$
 * $Date$
 */
package dbnp.studycapturing

import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.hssf.usermodel.HSSFWorkbook

class AssayService {

    boolean transactional = false
    def authenticationService
    def moduleCommunicationService

    /**
     * Collects the assay field names per category in a map as well as the
     * module's measurements.
     *
     * @param assay the assay for which to collect the fields
     * @return a map of categories as keys and field names or measurements as
     *  values
     */
    def collectAssayTemplateFields(assay) throws Exception {

        def getUsedTemplateFields = { templateEntities ->

            // gather all unique and non null template fields that haves values
            templateEntities*.giveFields().flatten().unique().findAll{ field ->

                field && templateEntities.any { it.fieldExists(field.name) && it.getFieldValue(field.name) }

            }.collect{[name: it.name, comment: it.comment]}

        }

        // check whether module is reachable
        if (!moduleCommunicationService.isModuleReachable(assay.module.url)) {

            throw new Exception('Module is not reachable')

        }

        def samples = assay.samples

        [   'Subject Data' :            getUsedTemplateFields( samples*."parentSubject".unique() ),
            'Sampling Event Data' :     getUsedTemplateFields( samples*."parentEvent".unique() ),
            'Sample Data' :             getUsedTemplateFields( samples ),
            'Event Group' :             [[name: 'name', comment: 'Name of Event Group']],
            'Module Measurement Data':  requestModuleMeasurementNames(assay)
        ]

    }

    /**
     * Gathers all assay related data, including measurements from the module,
     * into 1 hash map containing: Subject Data, Sampling Event Data, Sample
     * Data, and module specific measurement data.
     * Data from each of the 4 hash map entries are themselves hash maps
     * representing a descriptive header (field name) as key and the data as
     * value.
     *
     * @param assay the assay to collect data for
     * @fieldMap map with categories as keys and fields as values
     * @measurementTokens selection of measurementTokens
     * @return The assay data structure as described above.
     */
    def collectAssayData(assay, fieldMap, measurementTokens) throws Exception {

        def collectFieldValuesForTemplateEntities = { templateFieldNames, templateEntities ->

            // return a hash map with for each field name all values from the
            // template entity list
            templateFieldNames.inject([:]) { map, fieldName ->

                map + [(fieldName): templateEntities.collect {

                    it?.fieldExists(fieldName) ? it.getFieldValue(fieldName) : ''

                }]

            }

        }

        def getFieldValues = { templateEntities, fieldNames, propertyName = '' ->

            def returnValue

            // if no property name is given, simply collect the fields and
            // values of the template entities themselves
            if (propertyName == '') {

                returnValue = collectFieldValuesForTemplateEntities(fieldNames, templateEntities)

            } else {

                // if a property name is given, we'll have to do a bit more work
                // to ensure efficiency. The reason for this is that for a list
                // of template entities, the properties referred to by
                // propertyName can include duplicates. For example, for 10
                // samples, there may be less than 10 parent subjects. Maybe
                // there's only 1 parent subject. We don't want to collect field
                // values for this single subject 10 times ...
                def fieldValues

                // we'll get the unique list of properties to make sure we're
                // not getting the field values for identical template entity
                // properties more then once.
                def uniqueProperties = templateEntities*."$propertyName".unique()

                fieldValues = collectFieldValuesForTemplateEntities(fieldNames, uniqueProperties)

                // prepare a lookup hashMap to be able to map an entities'
                // property (e.g. a sample's parent subject) to an index value
                // from the field values list
                int i = 0
                def propertyToFieldValueIndexMap = uniqueProperties.inject([:]) { map, item -> map + [(item):i++]}

                // prepare the return value so that it has an entry for field
                // name. This will be the column name (second header line).
                returnValue = fieldNames.inject([:]) { map, item -> map + [(item):[]] }

                // finally, fill map the unique field values to the (possibly
                // not unique) template entity properties. In our example with
                // 1 unique parent subject, this means copying that subject's
                // field values to all 10 samples.
                templateEntities.each{ te ->

                    fieldNames.each{

                        returnValue[it] << fieldValues[it][propertyToFieldValueIndexMap[te[propertyName]]]

                    }

                }

            }

            returnValue

        }

        // check whether module is reachable
        if (!moduleCommunicationService.isModuleReachable(assay.module.url)) {

            throw new Exception('Module is not reachable')

        }

        // Find samples and sort by name
		def samples = assay.samples.toList().sort { it.name }

        def eventFieldMap = [:]

        // check whether event group data was requested
        if (fieldMap['Event Group']) {

            def names = samples*.parentEventGroup*.name.flatten()

            // only set name field when there's actual data
            if (!names.every {!it}) eventFieldMap['name'] = names

        }

        [   'Subject Data' :            getFieldValues(samples, fieldMap['Subject Data']*.name, 'parentSubject'),
            'Sampling Event Data' :     getFieldValues(samples, fieldMap['Sampling Event Data']*.name, 'parentEvent'),
            'Sample Data' :             getFieldValues(samples, fieldMap['Sample Data']*.name),
            'Event Group' :             eventFieldMap,
            'Module Measurement Data':  measurementTokens*.name ? requestModuleMeasurements(assay, measurementTokens, samples) : [:]
        ]
    }

    /**
     * Retrieves measurement names from the module through a rest call
     *
     * @param consumer the url of the module
     * @param path path of the rest call to the module
     * @return
     */
    def requestModuleMeasurementNames(assay) {

        def moduleUrl = assay.module.url

        def path = moduleUrl + "/rest/getMeasurements/query?assayToken=$assay.assayUUID"

        def jsonArray = moduleCommunicationService.callModuleRestMethodJSON(moduleUrl, path)

        jsonArray*.toString()

    }

    /**
     * Retrieves module measurement data through a rest call to the module
     *
     * @param assay		Assay for which the module measurements should be retrieved
     * @param fields	List with the names of the fields to be retrieved. Format: [ [ name: 'measurementName1' ], [ name: 'measurementName2' ] ]
     * @param samples	Samples for which the module 
     * @return
     */
    def requestModuleMeasurements(assay, fields, samples) {

        def moduleUrl = assay.module.url

        def tokenString = ''

        fields.each{
            tokenString+="&measurementToken=${it.name.encodeAsURL()}"
        }
        
        def path = moduleUrl + "/rest/getMeasurementData/query?assayToken=$assay.assayUUID" + tokenString
        
        def (sampleTokens, measurementTokens, moduleData) = moduleCommunicationService.callModuleRestMethodJSON(moduleUrl, path)

        if (!sampleTokens?.size()) return []

		// Convert the three different maps into a map like: 
		//
		// [ "measurement 1": [ value1, value2, value3 ],
		//   "measurement 2": [ value4, value5, value6 ] ]
		//
		// The returned values should be in the same order as the given samples-list
		def map = [:]
		def numSampleTokens = sampleTokens.size();
		
		measurementTokens.eachWithIndex { measurementToken, measurementIndex ->
			def measurements = [];
			samples.each { sample ->

				// Do measurements for this sample exist? If not, a null value is returned
				// for this sample. Otherwise, the measurement is looked up in the list with
				// measurements, based on the sample token
				if( sampleTokens.collect{ it.toString() }.contains( sample.giveUUID() ) ) {
					def tokenIndex = sampleTokens.indexOf( sample.giveUUID() );
					def valueIndex = measurementIndex * numSampleTokens + tokenIndex;
					
					// If the module data is in the wrong format, show an error in the log file
					// and return a null value for this measurement.
					if( valueIndex >= moduleData.size() ) {
						log.error "Module measurements given by module " + assay.module.name + " are not in the right format: " + measurementTokens?.size() + " measurements, " + sampleTokens?.size() + " samples, " + moduleData?.size() + " values"
						measurements << null
					}  else {
						measurements << moduleData[ valueIndex ].toString();
					}
				} else {
					measurements << null
				}
			}
			map[ measurementToken.toString() ] = measurements
		}

		return map;
    }

    /**
     * Converts column
     * @param columnData multidimensional map containing column data.
     * On the top level, the data must be grouped by category. Each key is the
     * category title and the values are maps representing the columns. Each
     * column also has a title (its key) and a list of values. Columns must be
     * equally sized.
     *
     * For example, consider the following map:
     * [Category1:
     *      [Column1: [1,2,3], Column2: [4,5,6]],
     *  Category2:
     *      [Column3: [7,8,9], Column4: [10,11,12], Column5: [13,14,15]]]
     *
     * which will be written as:
     *
     * | Category1  |           | Category2 |           |           |
     * | Column1    | Column2   | Column3   | Column4   | Column5   |
     * | 1          | 4         | 7         | 10        | 13        |
     * | 2          | 5         | 8         | 11        | 14        |
     * | 3          | 6         | 9         | 12        | 15        |
     *
     * @return row wise data
     */
    def convertColumnToRowStructure(columnData) {

            // check if all columns have the dimensionality 2
            if (columnData.every { it.value.every { it.value instanceof ArrayList } }) {

                def headers = [[],[]]

                columnData.each { category ->

                    if (category.value.size()) {

                        // put category keys into first row separated by null values
                        // wherever there are > 1 columns per category
                        headers[0] += [category.key] + [null] * (category.value.size() - 1)

                        // put non-category column headers into 2nd row
                        headers[1] += category.value.collect{it.key}

                    }

                }

                def d = []

                // add all column wise data into 'd'
                columnData.each { it.value.each { d << it.value } }

                // transpose d into row wise data and combine with header rows
                headers + d.transpose()
            }

        }

    /**
     * Export column wise data in Excel format to a stream.
     *
     * @param columnData Multidimensional map containing column data
     * @param outputStream Stream to write to
     * @param useOfficeOpenXML Flag to specify xlsx (standard) or xls output
     * @return
     */
    def exportColumnWiseDataToExcelFile(columnData, outputStream, useOfficeOpenXML = true) {

        // transform data into row based structure for easy writing
        def rows = convertColumnToRowStructure(columnData)

        if (rows) {

            exportRowWiseDataToExcelFile(rows, outputStream, useOfficeOpenXML)

        } else {

            throw new Exception('Wrong column data format.')

        }

    }

    /**
     * Export row wise data in Excel format to a stream
     *
     * @param rowData List of lists containing for each row all cell values
     * @param outputStream Stream to write to
     * @param useOfficeOpenXML Flag to specify xlsx (standard) or xls output
     * @return
     */
    def exportRowWiseDataToExcelFile(rowData, outputStream, useOfficeOpenXML = true) {

        Workbook wb = useOfficeOpenXML ? new XSSFWorkbook() : new HSSFWorkbook()
        Sheet sheet = wb.createSheet()

        // create all rows
        rowData.size().times { sheet.createRow it }

        sheet.eachWithIndex { Row row, ri ->

            // create appropriate number of cells for this row
            rowData[ri].size().times { row.createCell it }

            row.eachWithIndex { Cell cell, ci ->

                // Numbers and values of type boolean, String, and Date can be
                // written as is, other types need converting to String
                def value = rowData[ri][ci]

                value = (value instanceof Number | value?.class in [boolean.class, String.class, Date.class]) ? value : value?.toString()

                // write the value (or an empty String if null) to the cell
                cell.setCellValue(value ?: '')

            }

        }

        wb.write(outputStream)
        outputStream.close()

    }

}