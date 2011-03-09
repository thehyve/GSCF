/**
 * SimpleWizardController Controler
 *
 * Description of my controller
 *
 * @author  your email (+name?)
 * @since	2010mmdd
 * @package	???
 *
 * Revision information:
 * $Rev$
 * $Author$
 * $Date$
 */
package dbnp.studycapturing

import org.apache.poi.ss.usermodel.DataFormatter
import org.dbnp.gdt.*
import grails.plugins.springsecurity.Secured
import dbnp.authentication.SecUser
import dbnp.importer.ImportCell
import dbnp.importer.ImportRecord
import dbnp.importer.MappingColumn

@Secured(['IS_AUTHENTICATED_REMEMBERED'])
class SimpleWizardController extends StudyWizardController {
	def authenticationService
	def fileService
	def importerService
	def gdtService = new GdtService()

	/**
	 * index closure
	 */
	def index = {
		if( params.id )
			redirect( action: "simpleWizard", id: params.id );
		else
			redirect( action: "simpleWizard" );
	}

	def simpleWizardFlow = {
		entry {
			action{
				flow.study = getStudyFromRequest( params )
				if (!flow.study) retrievalError()
			}
			on("retrievalError").to "handleError"
			on("success").to "study"
		}

		study {
			on("next") {
				handleStudy( flow.study, params )
				if( !validateObject( flow.study ) )
					error()
			}.to "decisionState"
			on("refresh") { handleStudy( flow.study, params ); }.to "study"
			on( "success" ) { handleStudy( flow.study, params ) }.to "study"
		}

		decisionState {
			action {
				// Create data in the flow
				flow.templates = [
							'Sample': Template.findAllByEntity( Sample.class ),
							'Subject': Template.findAllByEntity( Subject.class ),
							'Event': Template.findAllByEntity( Event.class ),
							'SamplingEvent': Template.findAllByEntity( SamplingEvent.class )
				];
				flow.encodedEntity = [
							'Sample': gdtService.encryptEntity( Sample.class.name ),
							'Subject': gdtService.encryptEntity( Subject.class.name ),
							'Event': gdtService.encryptEntity( Event.class.name ),
							'SamplingEvent': gdtService.encryptEntity( SamplingEvent.class.name )
						]

				if (flow.study.samples)
					checkStudySimplicity(flow.study) ? existingSamples() : complexStudy()
				else
					samples()
			}
			on ("existingSamples").to "startExistingSamples"
			on ("complexStudy").to "complexStudy"
			on ("samples").to "samples"
		}
		
		startExistingSamples {
			action {
				def records = importerService.getRecords( flow.study );
				flow.records = records
				flow.templateCombinations = records.templateCombination.unique()
				success();
			}
			on( "success" ).to "existingSamples"
		}

		existingSamples {
			on("next") {
				handleExistingSamples( flow.study, params, flow ) ? success() : error()
			}.to "startAssays"
			on("previous").to "study"
			on("update") {
				handleExistingSamples( flow.study, params, flow ) ? success() : error()
			}.to "samples"

			on("skip").to "startAssays"
		}

		complexStudy {
			on("save").to "save"
			on("previous").to "study"
		}

		samples {
			on("next") {
				if( !handleSamples( flow.study, params, flow ) )
					return error();
				
				// Add domain fields for all entities
				flow.domainFields = [:]
				
				flow.templates.each { 
					if( it.value ) {
						flow.domainFields[ it.key ] = it.value[0].entity.giveDomainFields();
					}
				}
				
				println flow.sampleForm.template
			}.to "columns"
			on("refresh") {
				def filename = params.get( 'importfile' );
		
				// Handle 'existing*' in front of the filename. This is put in front to make a distinction between
				// an already uploaded file test.txt (maybe moved to some other directory) and a newly uploaded file test.txt
				// still being in the temporary directory.
				// This import step doesn't have to make that distinction, since all files remain in the temporary directory.
				if( filename == 'existing*' )
					filename = '';
				else if( filename[0..8] == 'existing*' )
					filename = filename[9..-1]
				
				// Refresh the templates, since the template editor has been opened
				flow.templates = [
						'Sample': Template.findAllByEntity( Sample.class ),
						'Subject': Template.findAllByEntity( Subject.class ),
						'Event': Template.findAllByEntity( Event.class ),
						'SamplingEvent': Template.findAllByEntity( SamplingEvent.class )
				];
										
				flow.sampleForm = [ importFile: filename ]
			}.to "samples"
			on("previous").to "returnFromSamples"
			on("study").to "study"
			on("skip").to "startAssays"
		}

		returnFromSamples {
			action {
				flow.study.samples ? existingSamples() : study();
			}
			on( "existingSamples" ).to "startExistingSamples"
			on( "study" ).to "study"
		}
		
		columns {
			on( "next" ) {
				handleColumns( flow.study, params, flow ) ? success() : error()
			}.to "checkImportedEntities"
			on( "previous" ).to "samples" 
		}
		
		checkImportedEntities {
			action {
				// Only continue to the next page if the information entered is correct
				if( flow.imported.numInvalidEntities > 0 ) {
					missingFields();
				} else {
					// The import of the excel file has finished. Now delete the excelfile
					if( flow.excel.filename )
						fileService.delete( flow.excel.filename );
	
					flow.sampleForm = null
	
					assays();
				}
			}
			on( "missingFields" ).to "missingFields"
			on( "assays" ).to "startAssays" 
		}
		
		missingFields {
			on( "next" ) {
				if( !handleMissingFields( flow.study, params, flow ) ) {
					return error();
				}
				
				// The import of the excel file has finished. Now delete the excelfile
				if( flow.excel.filename )
					fileService.delete( flow.excel.filename );

				flow.sampleForm = null
				
				success();
			}.to "startAssays"
			on( "previous" ) {
				// The user goes back to the previous page, so the already imported entities
				// (of which some gave an error) should be removed again.
				// Add all samples
				flow.imported.data.each { record ->
					record.each { entity ->
						if( entity ) {
							switch( entity.class ) {
								case Sample:	flow.study.removeFromSamples( entity ); break;
								case Subject:	flow.study.removeFromSubjects( entity ); break;
								case Event:		flow.study.removeFromEvents( entity ); break;
								case SamplingEvent:	flow.study.removeFromSamplingEvents( entity ); break;
							}
						}
					}
				}
				
				success();
			}.to "columns"
		}
		
		startAssays {
			action {
				if( !flow.assay ) 
					flow.assay = new Assay( parent: flow.study );
					
				success();
			}
			on( "success" ).to "assays"
		}
		
		assays {
			on( "next" ) { 
				handleAssays( flow.assay, params, flow );
				if( !validateObject( flow.assay ) )
					error();
			 }.to "overview"
			on( "skip" ) {
				// In case the user has created an assay before he clicked 'skip', it should only be kept if it
				// existed before this step
				if( flow.assay != null && !flow.assay.id ) {
					flow.remove( "assay" )
				}

			 }.to "overview"
			on( "previous" ).to "returnFromAssays"
			on("refresh") { handleAssays( flow.assay, params, flow ); success() }.to "assays"
		}

		returnFromAssays {
			action {
				flow.study.samples ? existingSamples() : samples();
			}
			on( "existingSamples" ).to "existingSamples"
			on( "samples" ).to "samples"
		}
		
		overview { 
			on( "save" ).to "saveStudy" 
			on( "previous" ).to "startAssays"
		}
		
		saveStudy {
			action {
				if( flow.assay && !flow.study.assays?.contains( flow.assay ) ) {
					flow.study.addToAssays( flow.assay );
				}
				
				if( flow.study.save( flush: true ) ) {
					// Make sure all samples are attached to all assays
					flow.study.assays.each { assay ->
						def l = []+ assay.samples;
						l.each { sample ->
							if( sample )
								assay.removeFromSamples( sample );
						}
						assay.samples?.clear();
		
						flow.study.samples.each { sample ->
							assay.addToSamples( sample )
						}
					}
			
					flash.message = "Your study is succesfully saved.";
					
					finish();
				} else {
					flash.error = "An error occurred while saving your study: <br />"
					flow.study.getErrors().each { flash.error += it.toString() + "<br />"}
					
					// Remove the assay from the study again, since it is still available
					// in the session
					if( flow.assay ) {
						flow.study.removeFromAssays( flow.assay );
						flow.assay.parent = flow.study;
					}
					
					overview();
				}
			}
			on( "finish" ).to "finish"
			on( "overview" ).to "overview"
		}
		
		finish()
		
		handleError{
			redirect action: "errorPage"
		}
	}

	/**
	 * Retrieves the required study from the database or return an empty Study object if
	 * no id is given
	 *
	 * @param params	Request parameters with params.id being the ID of the study to be retrieved
	 * @return			A study from the database or an empty study if no id was given
	 */
	protected Study getStudyFromRequest( def params ) {
		int id = params.int( "id" );

		if( !id ) {
			return new Study( title: "New study", owner: authenticationService.getLoggedInUser() );
		}

		Study s = Study.get( id );

		if( !s ) {
			flash.error = "No study found with given id";
			return null;
		}
		if( !s.canWrite( authenticationService.getLoggedInUser() ) ) {
			flash.error = "No authorization to edit this study."
			return null;
		}

		return s
	}

	/**
	 * Handles study input
	 * @param study		Study to update
	 * @param params	Request parameter map
	 * @return			True if everything went OK, false otherwise. An error message is put in flash.error
	 */
	def handleStudy( study, params ) {
		// did the study template change?
		if (params.get('template') && study.template?.name != params.get('template')) {
			// set the template
			study.template = Template.findByName(params.remove('template'))
		}

		// does the study have a template set?
		if (study.template && study.template instanceof Template) {
			// yes, iterate through template fields
			study.giveFields().each() {
				// and set their values
				study.setFieldValue(it.name, params.get(it.escapedName()))
			}
		}

		// handle public checkbox
		if (params.get("publicstudy")) {
			study.publicstudy = params.get("publicstudy")
		}

		// handle publications
		handleStudyPublications(study, params)

		// handle contacts
		handleStudyContacts(study, params)

		// handle users (readers, writers)
		handleStudyUsers(study, params, 'readers')
		handleStudyUsers(study, params, 'writers')

		return true
	}
	
	/**
	* Handles the editing of existing samples
	* @param study		Study to update
	* @param params		Request parameter map
	* @return			True if everything went OK, false otherwise. An error message is put in flash.error
	*/
   def handleExistingSamples( study, params, flow ) {
	   flash.validationErrors = [];

	   def errors = false;
	   
	   // iterate through objects; set field values and validate the object
	   def eventgroups = study.samples.parentEventGroup.findAll { it }
	   def events;
	   if( !eventgroups )
		   events = []
	   else
		   events = eventgroups.events?.getAt(0);
	   
	   def objects = [
		   'Sample': study.samples,
		   'Subject': study.samples.parentSubject.findAll { it },
		   'SamplingEvent': study.samples.parentEvent.findAll { it },
		   'Event': events.flatten().findAll { it }
	   ];
	   objects.each {
		   def type = it.key;
		   def entities = it.value;
		   
		   entities.each { entity ->
			   // iterate through entity fields
			   entity.giveFields().each() { field ->
				   def value = params.get( type.toLowerCase() + '_' + entity.getIdentifier() + '_' + field.escapedName())

				   // set field value; name cannot be set to an empty value
				   if (field.name != 'name' || value) {
					   log.info "setting "+field.name+" to "+value
					   entity.setFieldValue(field.name, value)
				   }
			   }
			   
			   // has the template changed?
			   def templateName = params.get(type.toLowerCase() + '_' + entity.getIdentifier() + '_template')
			   if (templateName && entity.template?.name != templateName) {
				   entity.template = Template.findByName(templateName)
			   }
   
			   // validate sample
			   if (!entity.validate()) {
				   errors = true;
				   
				   def entityName = entity.class.name[ entity.class.name.lastIndexOf( "." ) + 1 .. -1 ]
				   getHumanReadableErrors( entity ).each {
				   		flash.validationErrors << [ key: it.key, value: "(" + entityName + ") " + it.value ];
				   }
			   }
		   }
	   }

	   return !errors
   }

	/**
	 * Handles the upload of sample data
	 * @param study		Study to update
	 * @param params	Request parameter map
	 * @return			True if everything went OK, false otherwise. An error message is put in flash.error
	 */
	def handleSamples( study, params, flow ) {
		def filename = params.get( 'importfile' );

		// Handle 'existing*' in front of the filename. This is put in front to make a distinction between
		// an already uploaded file test.txt (maybe moved to some other directory) and a newly uploaded file test.txt
		// still being in the temporary directory.
		// This import step doesn't have to make that distinction, since all files remain in the temporary directory.
		if( filename == 'existing*' )
			filename = '';
		else if( filename[0..8] == 'existing*' )
			filename = filename[9..-1]

		def sampleTemplateId  = params.long( 'sample_template_id' )
		def subjectTemplateId  = params.long( 'subject_template_id' )
		def eventTemplateId  = params.long( 'event_template_id' )
		def samplingEventTemplateId  = params.long( 'samplingEvent_template_id' )

		// These fields have been removed from the form, so will always contain
		// their default value. The code however remains like this for future use.
		int sheetIndex = (params.int( 'sheetindex' ) ?: 1 )
		int dataMatrixStart = (params.int( 'datamatrix_start' ) ?: 2 )
		int headerRow = (params.int( 'headerrow' ) ?: 1 )

		// Save form data in session
		flow.sampleForm = [
					importFile: filename,
					templateId: [
						'Sample': sampleTemplateId,
						'Subject': subjectTemplateId,
						'Event': eventTemplateId,
						'SamplingEvent': samplingEventTemplateId
					],
					template: [
						'Sample': sampleTemplateId ? Template.get( sampleTemplateId ) : null,
						'Subject': subjectTemplateId ? Template.get( subjectTemplateId ) : null,
						'Event': eventTemplateId ? Template.get( eventTemplateId ) : null,
						'SamplingEvent': samplingEventTemplateId ? Template.get( samplingEventTemplateId ) : null
					],
					sheetIndex: sheetIndex,
					dataMatrixStart: dataMatrixStart,
					headerRow: headerRow
				];

		// Check whether the template exists
		if (!sampleTemplateId || !Template.get( sampleTemplateId ) ){
			log.error ".simple study wizard not all fields are filled in: " + sampleTemplateId
			flash.error = "No template was chosen. Please choose a template for the samples you provided."
			return false
		}
		
		def importedfile = fileService.get( filename )
		def workbook
		if (importedfile.exists()) {
			try {
				workbook = importerService.getWorkbook(new FileInputStream(importedfile))
			} catch (Exception e) {
				log.error ".simple study wizard could not load file: " + e
				flash.error = "The given file doesn't seem to be an excel file. Please provide an excel file for entering samples.";
				return false
			}
		} else {
			log.error ".simple study wizard no file given";
			flash.error = "No file was given. Please provide an excel file for entering samples.";
			return false;
		}

		if( !workbook ) {
			log.error ".simple study wizard could not load file into a workbook"
			flash.error = "The given file doesn't seem to be an excel file. Please provide an excel file for entering samples.";
			return false
		}

		def selectedentities = []

		if( !excelChecks( workbook, sheetIndex, headerRow, dataMatrixStart ) )
			return false;

		// Get the header from the Excel file using the arguments given in the first step of the wizard
		def importerHeader;
		def importerDataMatrix;

		try {		
			importerHeader = importerService.getHeader(workbook,
					sheetIndex - 1, 		// 0 == first sheet
					headerRow,				// 1 == first row :s
					dataMatrixStart - 1, 	// 0 == first row
					Sample.class)
		
			importerDataMatrix = importerService.getDatamatrix(
					workbook,
					importerHeader,
					sheetIndex - 1, 		// 0 == first sheet
					dataMatrixStart - 1, 	// 0 == first row
					5)
		} catch( Exception e ) {
			// An error occurred while reading the excel file.
			log.error ".simple study wizard error while reading the excel file";
			e.printStackTrace();

			// Show a message to the user
			flash.error = "An error occurred while reading the excel file. Have you provided the right sheet number and row numbers. Contact your system administrator if this problem persists.";
			return false;
		}

		// Match excel columns with template fields
		def fieldNames = [];
		flow.sampleForm.template.each { template ->
			if( template.value ) {
				def fields = template.value.entity.giveDomainFields() + template.value.getFields();
				fields.each { field ->
					if( !field.entity )
						field.entity = template.value.entity
						
					fieldNames << field
				}
			}
		}
		importerHeader.each { mc ->
			def bestfit = importerService.mostSimilar( mc.name, fieldNames, 0.8);
			if( bestfit ) {
				// Remove this fit from the list
				fieldNames.remove( bestfit );
				
				mc.entityclass = bestfit.entity
				mc.property = bestfit.name
			}
		}
		
		// Save read excel data into session
		def dataMatrix = [];
		def df = new DataFormatter();
		importerDataMatrix.each {
			dataMatrix << it.collect{ it ? df.formatCellValue(it) : "" }
		}
		
		flow.excel = [
					filename: filename,
					sheetIndex: sheetIndex,
					dataMatrixStart: dataMatrixStart,
					headerRow: headerRow,
					data: [
						header: importerHeader,
						dataMatrix: dataMatrix
					]
				]

		return true
	}

	
	/**
	 * Handles the matching of template fields with excel columns by the user
	 * @param study		Study to update
	 * @param params	Request parameter map
	 * @return			True if everything went OK, false otherwise. An error message is put in flash.error
	 * 					The field session.simpleWizard.imported.numInvalidEntities reflects the number of
	 * 					entities that have errors, and should be fixed before saving. The errors for those entities
	 * 					are saved into session.simpleWizard.imported.errors
	 */
	def handleColumns( study, params, flow ) {
		// Find actual Template object from the chosen template name
		def templates = [:];
		flow.sampleForm.templateId.each {
			templates[ it.key ] = it.value ? Template.get( it.value ) : null;
		}
		
		def headers = flow.excel.data.header;

		if( !params.matches ) {
			log.error( ".simple study wizard no column matches given" );
			flash.error = "No column matches given";
			return false;
		}

		// Retrieve the chosen matches from the request parameters and put them into
		// the headers-structure, for later reference
		params.matches.index.each { columnindex, value ->
			// Determine the entity and property by splitting it
			def parts = value.toString().tokenize( "||" );
			
			def property
			def entityName
			if( parts.size() > 1 ) {
				property = parts[ 1 ];
				entityName = "dbnp.studycapturing." + parts[ 0 ];
			} else if( parts.size() == 1 ) {
				property = parts[ 0 ];
				entityName = headers[columnindex.toInteger()].entityclass.getName();
			}
			
			// Create an actual class instance of the selected entity with the selected template
			// This should be inside the closure because in some cases in the advanced importer, the fields can have different target entities
			def entityClass = Class.forName( entityName, true, this.getClass().getClassLoader())
			def entityObj = entityClass.newInstance(template: templates[ entityName[entityName.lastIndexOf( '.' ) + 1..-1] ])

			headers[ columnindex.toInteger() ].entityclass = entityClass
			
			// Store the selected property for this column into the column map for the ImporterService
			headers[columnindex.toInteger()].property = property

			// Look up the template field type of the target TemplateField and store it also in the map
			headers[columnindex.toInteger()].templatefieldtype = entityObj.giveFieldType(property)

			// Is a "Don't import" property assigned to the column?
			headers[columnindex.toInteger()].dontimport = (property == "dontimport") ? true : false

			//if it's an identifier set the mapping column true or false
			entityClass.giveDomainFields().each {
				headers[columnindex.toInteger()].identifier = ( it.preferredIdentifier && (it.name == property) )
			}
		}

		// Import the workbook and store the table with entity records and store the failed cells
		println "Importing samples for study " + study + " (" + study.id + ")";
		
		def importedfile = fileService.get( flow.excel.filename )
		def workbook
		if (importedfile.exists()) {
			try {
				workbook = importerService.getWorkbook(new FileInputStream(importedfile))
			} catch (Exception e) {
				log.error ".simple study wizard could not load file: " + e
				flash.error = "The given file doesn't seem to be an excel file. Please provide an excel file for entering samples.";
				return false
			}
		} else {
			log.error ".simple study wizard no file given";
			flash.error = "No file was given. Please provide an excel file for entering samples.";
			return false;
		}

		if( !workbook ) {
			log.error ".simple study wizard could not load file into a workbook"
			flash.error = "The given file doesn't seem to be an excel file. Please provide an excel file for entering samples.";
			return false
		}
			
		def imported = importerService.importOrUpdateDataBySampleIdentifier(templates,
				workbook,
				flow.excel.sheetIndex - 1,
				flow.excel.dataMatrixStart - 1,
				flow.excel.data.header,
				flow.study,
				true			// Also create entities for which no data is imported but where templates were chosen
		);

		def table = imported.table
		def failedcells = imported.failedCells

		flow.imported = [
			data: table,
			failedCells: failedcells
		];
	
		// loop through all entities to validate them and add them to failedcells if an error occurs
		def numInvalidEntities = 0;
		def errors = [];

		// Add all samples
		table.each { record ->
			record.each { entity ->
				if( entity ) {
					// Determine entity class and add a parent. Add the entity to the study
					def preferredIdentifier = importerService.givePreferredIdentifier( entity.class );
					def equalClosure = { it.getFieldValue( preferredIdentifier.name ) == entity.getFieldValue( preferredIdentifier.name ) }
					def entityName = entity.class.name[ entity.class.name.lastIndexOf( "." ) + 1 .. -1 ]

					entity.parent = study
					
					switch( entity.class ) {
						case Sample:
							if( !preferredIdentifier || !study.samples?.find( equalClosure ) ) {
								study.addToSamples( entity );
							}
							break;
						case Subject:
							if( !preferredIdentifier || !study.subjects?.find( equalClosure ) ) {
								study.addToSubjects( entity );
							}
							break;
						case Event:
							if( !preferredIdentifier || !study.events?.find( equalClosure ) ) {
								study.addToEvents( entity );
							}
							break;
						case SamplingEvent:
							if( !preferredIdentifier || !study.samplingEvents?.find( equalClosure ) ) {
								study.addToSamplingEvents( entity );
							}
							break;
					}
					
					if (!entity.validate()) {
						numInvalidEntities++;
						
						// Add this field to the list of failed cells, in order to give the user feedback
						failedcells = addNonValidatingCells( failedcells, entity, flow )
	
						// Also create a full list of errors
						def currentErrors = getHumanReadableErrors( entity )
						if( currentErrors ) {
							currentErrors.each {
								errors += "(" + entityName + ") " + it.value;
							}
						}
					}
				}
			}
		}

		flow.imported.numInvalidEntities = numInvalidEntities + failedcells?.size();
		flow.imported.errors = errors;

		return true
	}
	
	/**
	 * Handles the update of the edited fields by the user
	 * @param study		Study to update
	 * @param params		Request parameter map
	 * @return			True if everything went OK, false otherwise. An error message is put in flash.error.
	 * 					The field session.simpleWizard.imported.numInvalidEntities reflects the number of
	 * 					entities that still have errors, and should be fixed before saving. The errors for those entities
	 * 					are saved into session.simpleWizard.imported.errors
	 */
	def handleMissingFields( study, params, flow ) {
		def numInvalidEntities = 0;
		def errors = [];

		// Check which fields failed previously
		def failedCells = flow.imported.failedCells
		def newFailedCells = [];

		flow.imported.data.each { table ->
			table.each { entity ->
				def invalidFields = 0
				def failed = new ImportRecord();
				def entityName = entity.class.name[ entity.class.name.lastIndexOf( "." ) + 1 .. -1 ]
				

				// Set the fields for this entity by retrieving values from the params
				entity.giveFields().each { field ->
					def fieldName = importerService.getFieldNameInTableEditor( entity, field );

					if( params[ fieldName ] == "#invalidterm" ) {
						// If the value '#invalidterm' is chosen, the user hasn't fixed anything, so this field is still incorrect
						invalidFields++;
						
						// store the mapping column and value which failed
						def identifier = entityName.toLowerCase() + "_" + entity.getIdentifier() + "_" + fieldName
						def mcInstance = new MappingColumn()
						failed.addToImportcells(new ImportCell(mappingcolumn: mcInstance, value: params[ fieldName ], entityidentifier: identifier))
					} else {
						if( field.type == org.dbnp.gdt.TemplateFieldType.ONTOLOGYTERM || field.type == org.dbnp.gdt.TemplateFieldType.STRINGLIST ) {
							// If this field is an ontologyterm field or a stringlist field, the value has changed, so remove the field from
							// the failedCells list
							importerService.removeFailedCell( failedCells, entity, field )
						}

						// Update the field, regardless of the type of field
						entity.setFieldValue(field.name, params[ fieldName ] )
					}
				}
				
				// Try to validate the entity now all fields have been set. If it fails, return an error
				if (!entity.validate() || invalidFields) {
					numInvalidEntities++;

					// Add this field to the list of failed cells, in order to give the user feedback
					failedCells = addNonValidatingCellsToImportRecord( failed, entity, flow )

					// Also create a full list of errors
					def currentErrors = getHumanReadableErrors( entity )
					if( currentErrors ) {
						currentErrors.each {
							errors += "(" + entityName + ") " + it.value;
						}
					}
					
					newFailedCells << failed;
				} else {
					importerService.removeFailedCell( failedCells, entity )
				}
			} // end of record
		} // end of table

		flow.imported.failedCells = newFailedCells
		flow.imported.numInvalidEntities = numInvalidEntities;
		flow.imported.errors = errors;

		return numInvalidEntities == 0
	}
	
	/**
	* Handles assay input
	* @param study		Study to update
	* @param params		Request parameter map
	* @return			True if everything went OK, false otherwise. An error message is put in flash.error
	*/
   def handleAssays( assay, params, flow ) {
	   // did the study template change?
	   if (params.get('template') && assay.template?.name != params.get('template')) {
		   // set the template
		   assay.template = Template.findByName(params.remove('template'))
	   }

	   // does the study have a template set?
	   if (assay.template && assay.template instanceof Template) {
		   // yes, iterate through template fields
		   assay.giveFields().each() {
			   // and set their values
			   assay.setFieldValue(it.name, params.get(it.escapedName()))
		   }
	   }

	   return true
   }
	
	
	/**
	 * Checks whether the given study is simple enough to be edited using this controller.
	 *
	 * The study is simple enough if the samples, subjects, events and samplingEvents can be
	 * edited as a flat table. That is:
	 * 		- Every subject belongs to 0 or 1 eventgroup
	 * 		- Every eventgroup belongs to 0 or 1 sample
	 * 		- Every eventgroup has 0 or 1 subjects, 0 or 1 event and 0 or 1 samplingEvents
	 * 		- If a sample belongs to an eventgroup:
	 * 			- If that eventgroup has a samplingEvent, that same samplingEvent must also be
	 * 				the sampling event that generated this sample
	 * 			- If that eventgroup has a subject, that same subject must also be the subject
	 * 				from whom the sample was taken
	 *
	 * @param study		Study to check
	 * @return			True if the study can be edited by this controller, false otherwise
	 */
	def checkStudySimplicity( study ) {
		def simplicity = true;

		if( !study )
			return false

		if( study.eventGroups ) {
			study.eventGroups.each { eventGroup ->
				// Check for simplicity of eventgroups: only 0 or 1 subject, 0 or 1 event and 0 or 1 samplingEvent
				if( eventGroup.subjects?.size() > 1 || eventGroup.events?.size() > 1 || eventGroup.samplingEvents?.size() > 1 ) {
					flash.message = "One or more eventgroups contain multiple subjects or events."
					simplicity = false;
				}

				// Check whether this eventgroup only belongs to (max) 1 sample
				def numSamples = 0;
				study.samples.each { sample ->
					// If no id is given for the eventGroup, it has been entered in this wizard, but
					// not yet saved. In that case, it is always OK
					if( eventGroup.id && sample.parentEventGroup?.id == eventGroup.id )
						numSamples++;
				}

				if( numSamples > 1 ) {
					flash.message = "One or more eventgroups belong to multiple samples."
					simplicity = false;
				}
			}

			if( !simplicity ) return false;

			// Check whether subject only belong to zero or one event group
			if( study.subjects ) {
				study.subjects.each { subject ->
					def numEventGroups = 0
					study.eventGroups.each { eventGroup ->
						// If no id is given for the subject, it has been entered in this wizard, but
						// not yet saved. In that case, it is always OK
						if( subject.id && eventGroup.subjects[0]?.id == subject.id )
							numEventGroups++
					}

					if( numEventGroups > 1 ) {
						flash.message = "One or more subjects belong to multiple eventgroups."
						simplicity = false;
					}
				}
			}

			if( !simplicity ) return false;

			// Check whether the samples that belong to an eventgroup have the right parentObjects
			study.samples.each { sample ->
				if( sample.parentEventGroup ) {
					// If no id is given for the subject, it has been entered in this wizard, but
					// not yet saved. In that case, it is always OK
					if( sample.parentSubject && sample.parentSubject.id) {
						if( !sample.parentEventGroup.subjects || sample.parentEventGroup.subjects[0]?.id != sample.parentSubject.id ) {
							flash.message = "The structure of the eventgroups of one or more samples is too complex"
							simplicity = false;
						}
					}

					// If no id is given for the sampling event, it has been entered in this wizard, but
					// not yet saved. In that case, it is always OK
					if( sample.parentEvent && sample.parentEvent.id) {
						if( !sample.parentEventGroup.samplingEvents || sample.parentEventGroup.samplingEvents[0]?.id != sample.parentEvent.id ) {
							flash.message = "The structure of the eventgroups of one or more samples is too complex"
							simplicity = false;
						}
					}
				}
			}

			if( !simplicity ) return false;
		}

		return simplicity;
	}

	
	/**
	 * Adds all fields of this entity that have given an error when validating to the failedcells list
	 * @param failedcells	Current list of ImportRecords
	 * @param entity		Entity to check. The entity must have been validated before
	 * @return				Updated list of ImportRecords
	 */
	protected def addNonValidatingCells( failedcells, entity, flow ) {
		// Add this entity and the fields with an error to the failedCells list
		ImportRecord failedRecord = addNonValidatingCellsToImportRecord( new ImportRecord(), entity, flow );

		failedcells.add( failedRecord );

		return failedcells
	}
	
	/**
	* Adds all fields of this entity that have given an error when validating to the failedcells list
	* @param failedcells	Current list of ImportRecords
	* @param entity		Entity to check. The entity must have been validated before
	* @return				Updated list of ImportRecords
	*/
   protected def addNonValidatingCellsToImportRecord( failedRecord, entity, flow ) {
	   entity.getErrors().getFieldErrors().each { error ->
		   String field = error.getField();
		   
		   def mc = importerService.findMappingColumn( flow.excel.data.header, field );
		   def mcInstance = new MappingColumn( name: field, entityClass: Sample.class, index: -1, property: field.toLowerCase(), templateFieldType: entity.giveFieldType( field ) );

		   // Create a clone of the mapping column
		   if( mc ) {
			   mcInstance.properties = mc.properties
		   }

		   failedRecord.addToImportcells( new ImportCell(mappingcolumn: mcInstance, value: error.getRejectedValue(), entityidentifier: importerService.getFieldNameInTableEditor( entity, field ) ) )
	   }
	   
	   return failedRecord
   }

	
	/**
	* Checks an excel workbook whether the given sheetindex and rownumbers are correct
	* @param workbook			Excel workbook to read
	* @param sheetIndex		1-based sheet index for the sheet to read (1=first sheet)
	* @param headerRow			1-based row number for the header row (1=first row)
	* @param dataMatrixStart	1-based row number for the first data row (1=first row)
	* @return					True if the sheet index and row numbers are correct.
	*/
   protected boolean excelChecks( def workbook, int sheetIndex, int headerRow, int dataMatrixStart ) {
	   // Perform some basic checks on the excel file. These checks should be performed by the importerservice
	   // in a perfect scenario.
	   if( sheetIndex > workbook.getNumberOfSheets() ) {
		   log.error ".simple study wizard Sheet index is too high: " + sheetIndex + " / " + workbook.getNumberOfSheets();
		   flash.error = "Your excel sheet contains too few excel sheets. The provided excel sheet has only " + workbook.getNumberOfSheets() + " sheet(s).";
		   return false
	   }

	   def sheet = workbook.getSheetAt(sheetIndex - 1);
	   def firstRowNum = sheet.getFirstRowNum();
	   def lastRowNum = sheet.getLastRowNum();
	   def numRows = lastRowNum - firstRowNum + 1;

	   if( headerRow > numRows  ) {
		   log.error ".simple study wizard Header row number is incorrect: " + headerRow + " / " + numRows;
		   flash.error = "Your excel sheet doesn't contain enough rows (" + numRows + "). Please provide an excel sheet with one header row and data below";
		   return false
	   }

	   if( dataMatrixStart > numRows  ) {
		   log.error ".simple study wizard Data row number is incorrect: " + dataMatrixStart + " / " + numRows;
		   flash.error = "Your excel sheet doesn't contain enough rows (" + numRows + "). Please provide an excel sheet with one header row and data below";
		   return false
	   }

	   return true;
   }
	
	/**
	 * Validates an object and puts human readable errors in validationErrors variable
	 * @param entity		Entity to validate
	 * @return			True iff the entity validates, false otherwise
	 */
	protected boolean validateObject( def entity ) {
		if( !entity.validate() ) {
			flash.validationErrors = getHumanReadableErrors( entity )
			return false;
		}
		return true;
	}

	/**
	 * transform domain class validation errors into a human readable
	 * linked hash map
	 * @param object validated domain class
	 * @return object  linkedHashMap
	 */
	def getHumanReadableErrors(object) {
		def errors = [:]
		object.errors.getAllErrors().each() { error ->
			// error.codes.each() { code -> println code }

			// generally speaking g.message(...) should work,
			// however it fails in some steps of the wizard
			// (add event, add assay, etc) so g is not always
			// availably. Using our own instance of the
			// validationTagLib instead so it is always
			// available to us
			errors[error.getArguments()[0]] = validationTagLib.message(error: error)
		}

		return errors
	}
}
