package dbnp.configuration

import dbnp.studycapturing.Study
import grails.util.Holders
import org.dbnp.gdt.Identity
import org.dbnp.gdt.TemplateEntity

/**
 * A script to automatically perform database changes
 *
 * @Author Jeroen Wesbeek
 * @Since 20101209
 *
 * Revision information:
 * $Rev$
 * $Author$
 * $Date$
 */
class DatabaseUpgrade {
	/**
	 * handle database upgrades
	 *
	 * @param dataSource
	 */
	public static void handleUpgrades(dataSource, grailsApplication) {
		// get a sql instance
		groovy.sql.Sql sql = new groovy.sql.Sql(dataSource)

		// get configuration
		def config = Holders.config
		def db = config.dataSource.driverClassName

		// execute per-change check and upgrade code
		changeStudyDescription(sql, db)                 // r1245 / r1246
		changeStudyDescriptionToText(sql, db)           // r1327
		changeTemplateTextFieldSignatures(sql, db)      // prevent Grails issue, see http://jira.codehaus.org/browse/GRAILS-6754
		setAssayModuleDefaultValues(sql, db)            // r1490
		dropMappingColumnNameConstraint(sql, db)        // r1525
		makeMappingColumnValueNullable(sql, db)         // r1525
		alterStudyAndAssay(sql, db)                     // r1594
		fixDateCreatedAndLastUpdated(sql, db)
		dropAssayModulePlatform(sql, db)                // r1689
        addAssayModuleBaseUrl(sql, db)                  // Adds a baseUrl to AssayModule
		makeStudyTitleAndTemplateNamesUnique(sql, db)   // #401, #406
		renameGdtMappingColumnIndex(sql, db)            // 'index' column in GdtImporter MappingColumn is a reserved keyword in MySQL
		// GdtImporter now by default uses 'columnindex' as domain field name
		fixShibbolethSecUser(sql, db)                   // fix shibboleth user
		//changeOntologyDescriptionType(sql, db)          // change ontology description type to text
		changeSpecificUUIDsToGenericUUIDs(sql, db)      // change domain specific UUIDs to generic UUIDs (GDT >= 0.3.1)
		fixStudyCodes(sql, db)                          // remove spaces from study codes
		fixUUIDs(sql,db, grailsApplication)				// fix missing UUIDs in database
	}

	/**
	 * execute database change r1245 / r1246 if required
	 * @param sql
	 * @param db
	 */
	public static void changeStudyDescription(sql, db) {
		// check if we need to perform this upgrade
		if (sql.firstRow("SELECT count(*) as total FROM template_field WHERE templatefieldentity='dbnp.studycapturing.Study' AND templatefieldname='Description'").total > 0) {
			// grom that we are performing the upgrade
			if (String.metaClass.getMetaMethod("grom")) "performing database upgrade: study description".grom()

			// database upgrade required
			try {
				// get the template field id
				def id = sql.firstRow("SELECT id FROM template_field WHERE templatefieldentity='dbnp.studycapturing.Study' AND templatefieldname='Description'").id

				// iterate through all obsolete study descriptions
				sql.eachRow("SELECT study_id, template_text_fields_elt as description FROM study_template_text_fields WHERE template_text_fields_idx='Description'") { row ->
					// migrate the template description to the study object itself
					// so we don't have to bother with sql injections, etc
					def study = Study.findById(row.study_id)
					study.setFieldValue('description', row.description)
					if (!(study.validate() && study.save())) {
						throw new Exception("could not save study with id ${row.study_id}")
					}
				}

				// delete all obsolete descriptions
				sql.execute("DELETE FROM study_template_text_fields WHERE template_text_fields_idx='Description'")

				// find all template id's where this field is used
				sql.eachRow("SELECT DISTINCT template_fields_id, fields_idx FROM template_template_field WHERE template_field_id=${id}") { row ->
					// delete the template_template_field reference
					sql.execute("DELETE FROM template_template_field WHERE template_field_id=${id} AND template_fields_id=${row.template_fields_id}")

					// and lower the idx-es of the remaining fields
					sql.execute("UPDATE template_template_field SET fields_idx=fields_idx-1 WHERE fields_idx>${row.fields_idx} AND template_fields_id=${row.template_fields_id}")
				}

				// and delete the obsolete template field
				sql.execute("DELETE FROM template_field WHERE id=${id}")
			} catch (Exception e) {
				println "changeStudyDescription database upgrade failed: " + e.getMessage()
			}
		}
	}

	/**
	 * execute database change r1327 if required
	 * @param sql
	 * @param db
	 */
	public static void changeStudyDescriptionToText(sql, db) {
		// are we running postgreSQL ?
		if (db == "org.postgresql.Driver") {
			// check if column 'description' in table 'study' is not of type 'text'
			if (sql.firstRow("SELECT count(*) as total FROM information_schema.columns WHERE columns.table_schema::text = 'public'::text AND columns.table_name='study' AND column_name='description' AND data_type != 'text'").total > 0) {
				// grom that we are performing the upgrade
				if (String.metaClass.getMetaMethod("grom")) "performing database upgrade: study description to text".grom()

				// database upgrade required
				try {
					// change the datatype of study::description to text
					sql.execute("ALTER TABLE study ALTER COLUMN description TYPE text")
				} catch (Exception e) {
					println "changeStudyDescriptionToText database upgrade failed: " + e.getMessage()
				}
			}
		}
	}

	/**
	 * it appears that some TEXT template fields are not of type 'text'
	 * due to an issue in how Grails' GORM works with domain inheritance
	 * (see http://jira.codehaus.org/browse/GRAILS-6754)
	 * @param sql
	 * @param db
	 */
	public static void changeTemplateTextFieldSignatures(sql, db) {
		if (db == "org.postgresql.Driver") {
			// check if any TEXT template fields are of type 'text'
			sql.eachRow("SELECT columns.table_name as tablename FROM information_schema.columns WHERE columns.table_schema::text = 'public'::text AND column_name='template_text_fields_elt' AND data_type != 'text';")
					{ row ->
						if (String.metaClass.getMetaMethod("grom")) "performing database upgrade: ${row.tablename} template_text_fields_string/elt to text".grom()
						try {
							// change the datatype of text fields to text
							sql.execute(sprintf("ALTER TABLE %s ALTER COLUMN template_text_fields_elt TYPE text", row.tablename))
							sql.execute(sprintf("ALTER TABLE %s ALTER COLUMN template_text_fields_string TYPE text", row.tablename))

						} catch (Exception e) {
							println "changeTemplateTextFieldSignatures database upgrade failed: " + e.getMessage()
						}
					}
		}
	}

	/**
	 * The fields 'notify' and 'openInFrame' have been added to AssayModule. However, there
	 * seems to be no method to setting the default values of these fields in the database. They
	 * are set to NULL by default, so all existing fields have 'NULL' set.
	 * This method sets the default values
	 * @param sql
	 * @param db
	 */
	public static void setAssayModuleDefaultValues(sql, db) {
		// do we need to perform this upgrade?
		if ((db == "org.postgresql.Driver" || db == "com.mysql.jdbc.Driver") &&
				(sql.firstRow("SELECT * FROM assay_module WHERE notify IS NULL") || sql.firstRow("SELECT * FROM assay_module WHERE open_in_frame IS NULL"))
		) {
			if (String.metaClass.getMetaMethod("grom")) "performing database upgrade: assay_module default values for boolean fields".grom()

			try {
				sql.execute("UPDATE assay_module SET notify=" + ((db == "org.postgresql.Driver") ? 'FALSE' : '0') + " WHERE notify IS NULL")
			} catch (Exception e) {
				println "setAssayModuleDefaultValues database upgrade failed, notify field couldn't be set to default value: " + e.getMessage()
			}

			// set open_in_frame?
			try {
				sql.execute("UPDATE assay_module SET open_in_frame=" + ((db == "org.postgresql.Driver") ? 'TRUE' : '1') + " WHERE open_in_frame IS NULL")
			} catch (Exception e) {
				// Maybe gdt plugin is not updated yet after revision 109 ?
				println "setAssayModuleDefaultValues database upgrade failed, openInFrame field couldn't be set to default value: " + e.getMessage()
			}
		}
	}

	/**
	 * Drop the unique constraint for the "name" column in the MappingColumn domain
	 *
	 * @param sql
	 * @param db
	 */
	public static void dropMappingColumnNameConstraint(sql, db) {
		// are we running postgreSQL ?
		if (db == "org.postgresql.Driver") {
			if (sql.firstRow("SELECT * FROM pg_constraint WHERE contype='mapping_column_name_key'")) {
				if (String.metaClass.getMetaMethod("grom")) "performing database upgrade: mapping column name constraint".grom()
				try {
					// Check if constraint still exists
					sql.execute("ALTER TABLE mapping_column DROP CONSTRAINT mapping_column_name_key")
				} catch (Exception e) {
					println "dropMappingColumnNameConstraint database upgrade failed, `name` field unique constraint couldn't be dropped: " + e.getMessage()
				}
			}
		}
	}

	/**
	 * Rename the column 'index' (reserved keyword in MySQL) from GdtImporterMapping to 'columnindex'
	 *
	 * @param sql
	 * @param db
	 */
	public static void renameGdtMappingColumnIndex(sql, db) {
		// are we running postgreSQL ?
		if (db == "org.postgresql.Driver") {
			if (sql.firstRow("SELECT * FROM information_schema.columns WHERE columns.table_name='gdt_mapping_column' AND columns.column_name='index'")) {
				if (String.metaClass.getMetaMethod("grom")) "performing database upgrade: GDT mapping column rename `index` to `columnindex`".grom()
				try {
					// Rename column 'index' to 'columnindex' in Gdt Mapping Column
					sql.execute("ALTER TABLE gdt_mapping_column RENAME COLUMN index TO columnindex;")
				} catch (Exception e) {
					println "renameGdtMappingColumnIndex database upgrade failed, `index` field couldn't be renamed to `columnindex`: " + e.getMessage()
				}
			}
		}
	}

	/**
	 * the importer requires the value field to be nullable
	 * @param sql
	 * @param db
	 */
	public static void makeMappingColumnValueNullable(sql, db) {
		// are we running postgreSQL?
		if (db == "org.postgresql.Driver") {
			// do we need to perform this update?
			if (sql.firstRow("SELECT * FROM information_schema.columns WHERE columns.table_name='mapping_column' AND columns.column_name='value' AND is_nullable='NO'")) {
				if (String.metaClass.getMetaMethod("grom")) "performing database upgrade: making mapping_column::value nullable".grom()

				try {
					sql.execute("ALTER TABLE mapping_column ALTER COLUMN value DROP NOT NULL")
				} catch (Exception e) {
					println "makeMappingColumnValueNullable database upgrade failed: " + e.getMessage()
				}
			}
		}
	}

	/**
	 * The field study.code has been set to be nullable
	 * The field assay.externalAssayId has been removed
	 * @param sql
	 * @param db
	 */
	public static void alterStudyAndAssay(sql, db) {
		def updated = false

		// are we running postgreSQL ?
		if (db == "org.postgresql.Driver") {
			// see if table assay contains a column external_assayid
			if (sql.firstRow("SELECT * FROM information_schema.columns WHERE columns.table_name='assay' AND columns.column_name='external_assayid'")) {
				if (String.metaClass.getMetaMethod("grom")) "performing database upgrade: dropping column 'external_assayid' from table 'assay'".grom()

				try {
					sql.execute("ALTER TABLE assay DROP COLUMN external_assayid")
					updated = true
				} catch (Exception e) {
					println "alterStudyAndAssay database upgrade failed, externalAssayId could not be removed from assay: " + e.getMessage()
				}

			}

			// see if table study contains a column code which is not nullable
			if (sql.firstRow("SELECT * FROM information_schema.columns WHERE columns.table_name='study' AND columns.column_name='code' AND is_nullable='NO'")) {
				if (String.metaClass.getMetaMethod("grom")) "performing database upgrade: dropping column 'code' from table 'study'".grom()

				try {
					sql.execute("ALTER TABLE study ALTER COLUMN code DROP NOT NULL")
					updated = true
				} catch (Exception e) {
					println "alterStudyAndAssay database upgrade failed, study.code could not be set to accept null values: " + e.getMessage()
				}
			}

			// Load all studies and save them again. This prevents errors on saving later
			if (updated) {
				if (String.metaClass.getMetaMethod("grom")) "re-saving studies...".grom()

				Study.list().each { study ->
					if (String.metaClass.getMetaMethod("grom")) "re-saving study: ${study}".grom()
					study.save()
				}
			}
		}
	}

	/**
	 * make sure all date_created and last_updated columns are NOT nullable, and
	 * set values to now() of they are null
	 * @param sql
	 * @param db
	 */
	public static void fixDateCreatedAndLastUpdated(sql, db) {
		// are we running PostgreSQL?
		if (db == "org.postgresql.Driver") {
			// see if we need to modify anything?
			sql.eachRow("SELECT table_name,column_name FROM information_schema.columns WHERE column_name IN ('last_updated', 'date_created') AND is_nullable='YES'") { row ->
				// grom what we are doing
				if (String.metaClass.getMetaMethod("grom")) "fixing nullable for ${row.table_name}:${row.column_name}".grom()

				// fix database
				try {
					// setting all null values to now()
					sql.execute(sprintf("UPDATE %s SET %s=now() WHERE %s IS NULL", row.table_name, row.column_name, row.column_name))

					// and alter the table to disallow null values
					sql.execute(sprintf("ALTER TABLE %s ALTER COLUMN %s SET NOT NULL", row.table_name, row.column_name))
				} catch (Exception e) {
					println "fixDateCreatedAndLastUpdated database upgrade failed: " + e.getMessage()
				}
			}
		}
	}

	/**
	 * drops the field platform from assay modules
	 * @param sql
	 * @param db
	 */
	public static void dropAssayModulePlatform(sql, db) {
		// are we running postgreSQL?
		if (db == "org.postgresql.Driver") {
			// do we need to perform this update?
			if (sql.firstRow("SELECT * FROM information_schema.columns WHERE columns.table_name='assay_module' AND columns.column_name='platform'")) {
				if (String.metaClass.getMetaMethod("grom")) "performing database upgrade: removing assayModule platform".grom()

				try {
					sql.execute("ALTER TABLE assay_module DROP COLUMN platform")
				} catch (Exception e) {
					println "dropAssayModulePlatform database upgrade failed: " + e.getMessage()
				}
			}
		}
	}

    /**
     * creates the field baseUrl for assay modules
     * @param sql
     * @param db
     */
    public static void addAssayModuleBaseUrl(sql, db) {
        // are we running postgreSQL?
        if (db == "org.postgresql.Driver") {
            // do we need to perform this update?
            if (sql.firstRow("SELECT * FROM information_schema.columns WHERE columns.table_name='assay_module' AND 'base_url' NOT IN (SELECT column_name FROM information_schema.columns WHERE table_name ='assay_module' ORDER BY ordinal_position)")) {
                if (String.metaClass.getMetaMethod("grom")) "performing database upgrade: creating assayModule baseurl".grom()

                try {
                    sql.execute("ALTER TABLE assay_module ADD COLUMN base_url character varying")
                } catch (Exception e) {
                    println "createAssayModuleBaseUrl database upgrade failed: " + e.getMessage()
                }
            }
        }
    }

	/**
	 * After adding shibboleth support, a boolean field should be set to false
	 * @param sql
	 * @param db
	 */
	public static void fixShibbolethSecUser(sql, db) {
		if (db == "org.postgresql.Driver") {
			// do we have shibboleth support available
			if (sql.firstRow("SELECT * FROM information_schema.columns WHERE columns.table_name='sec_user' AND columns.column_name='shibboleth_user'")) {
				// do we have null values?
				if (sql.firstRow("SELECT * FROM sec_user WHERE shibboleth_user IS NULL")) {
					// update null values to false
					if (String.metaClass.getMetaMethod("grom")) "performing database upgrade: setting shibboleth boolean to false (default)".grom()

					try {
						sql.execute("UPDATE sec_user SET shibboleth_user='f' WHERE shibboleth_user IS NULL")
					} catch (Exception e) {
						println "fixShibbolethSecUser database upgrade failed: " + e.getMessage()
					}
				}
			}
		}
	}

	/**
	 * Make sure the study title, template name and template field names are unique
	 * @param sql
	 * @param db
	 */
	public static void makeStudyTitleAndTemplateNamesUnique(sql, db) {
		def titleCount, title, newTitle, templateFieldTypeCount, templateFieldUnitCount
		def grom = false

		// are we running postgreSQL?
		if (db == "org.postgresql.Driver") {
			// yes, find all duplicate study titles
			sql.eachRow("	SELECT DISTINCT a.title, 'study' as tablename, '' as entity FROM study a WHERE (SELECT count(*) FROM study b WHERE b.title=a.title) > 1\
							UNION SELECT DISTINCT d.name as title, 'template' as tablename, d.entity FROM template d WHERE (SELECT count(*) FROM template c WHERE d.name=c.name AND d.entity=c.entity) > 1\
							UNION SELECT DISTINCT e.templatefieldname as title, 'template_field' as tablename, e.templatefieldentity as entity FROM template_field e WHERE (SELECT count(*) FROM template_field f WHERE e.templatefieldname=f.templatefieldname AND e.templatefieldentity=f.templatefieldentity) > 1\
							") { row ->

				// grom what we are doing
				if (String.metaClass.getMetaMethod("grom") && !grom) {
					"making study titles, template names and template_field names unique".grom()
					grom = true
				}

				// set work variables
				titleCount = 1
				title = row.title.replace("'", "\'")

				// check what we are updating
				switch (row.tablename) {
					case "study":
						// update study titles
						sql.eachRow(sprintf("SELECT id FROM study WHERE title='%s'", title)) { studyRow ->
							newTitle = title + ((titleCount > 1) ? " - ${titleCount}" : "")
							sql.execute(sprintf("UPDATE study SET title='%s' WHERE id=%d", newTitle, studyRow.id))
							titleCount++
						}
						break
					case "template":
						// update template names
						sql.eachRow(sprintf("SELECT id FROM template WHERE name='%s' AND entity='%s'", title, row.entity)) { templateRow ->
							newTitle = title + ((titleCount > 1) ? " - ${titleCount}" : "")
							sql.execute(sprintf("UPDATE template SET name='%s' WHERE id=%d", newTitle, templateRow.id))
							titleCount++
						}
						break
					case "template_field":
						templateFieldTypeCount = [:]
						templateFieldUnitCount = [:]

						// update template_field names
						sql.eachRow(sprintf("SELECT id,templatefieldunit as unit,templatefieldtype as type FROM template_field WHERE templatefieldname='%s' AND templatefieldentity='%s'", title, row.entity)) { templateFieldRow ->
							if (templateFieldRow.unit) {
								templateFieldUnitCount[templateFieldRow.unit] = (templateFieldUnitCount[templateFieldRow.unit]) ? templateFieldUnitCount[templateFieldRow.unit] + 1 : 1
								newTitle = "${title} (${templateFieldRow.unit})" + ((templateFieldUnitCount[templateFieldRow.unit] > 1) ? " - ${templateFieldUnitCount[templateFieldRow.unit]}" : "")
							} else {
								templateFieldTypeCount[templateFieldRow.type] = (templateFieldTypeCount[templateFieldRow.type]) ? templateFieldTypeCount[templateFieldRow.type] + 1 : 1
								newTitle = "${title} (${templateFieldRow.type})" + ((templateFieldTypeCount[templateFieldRow.type] > 1) ? " - ${templateFieldTypeCount[templateFieldRow.type]}" : "")
								titleCount++
							}
							sql.execute(sprintf("UPDATE template_field SET templatefieldname='%s' WHERE id=%d", newTitle, templateFieldRow.id))
						}
						break
					default:
						// this shouldn't happen
						break
				}
			}
		}
	}

	/**
	 * Make sure the ontology's description is of type text instead of varchar
	 * @param sql
	 * @param db
	 */
	public static void changeOntologyDescriptionType(sql, db) {
		// are we running postgreSQL?
		if (db == "org.postgresql.Driver") {
			if (sql.firstRow("SELECT * FROM information_schema.columns WHERE columns.table_name='study' AND columns.column_name='code' AND columns.data_type='character varying'")) {
				if (String.metaClass.getMetaMethod("grom")) "performing database upgrade: change ontology description type".grom()

				try {
					// change the datatype of the ontology's description to text
					sql.execute("ALTER TABLE ontology ALTER COLUMN description TYPE text")
				} catch (Exception e) {
					println "changeOntologyDescriptionType database upgrade failed: " + e.getMessage()
				}
			}
		}
	}

	/**
	 * Migrate domain class specific uuid's to generic uuid's as set up in GDT 0.3.1
	 * @param sql
	 * @param db
	 */
	public static void changeSpecificUUIDsToGenericUUIDs(sql, db) {
		def grom = false

		// are we running postgreSQL?
		if (db == "org.postgresql.Driver") {
			sql.eachRow("SELECT * FROM information_schema.columns WHERE columns.column_name LIKE '%uuid' AND columns.column_name != 'uuid'") { row ->
				if (sql.firstRow(sprintf("SELECT * FROM information_schema.columns WHERE columns.column_name='uuid' AND table_name='%s'",row.table_name))) {
					if (String.metaClass.getMetaMethod("grom") && !grom) {
						"migrating domain specific UUIDs to system wide generic UUIDs".grom()
						grom = true
					}

					try {
						// copy contents of domain specific uuid to generic uuid
						//log.info "migrating domain specific ${row.column_name} (${row.table_name}) to generic uuid"
						sql.execute(sprintf("UPDATE %s SET uuid=%s", row.table_name, row.column_name))

						// drop domain specific column
						//log.info "drop column ${row.column_name} (${row.table_name})"
						sql.execute(sprintf("ALTER TABLE %s DROP COLUMN %s", row.table_name, row.column_name))
					} catch (Exception e) {
						println "changeSpecificUUIDsToGenericUUIDs database upgrade failed: " + e.getMessage()
					}
				}
			}
		}
	}

	/**
	 * strip spaces from study codes
	 * @param sql
	 * @param db
	 */
	public static void fixStudyCodes(sql, db) {
		def grom = false

		if (db == "org.postgresql.Driver") {
			sql.eachRow("SELECT * FROM Study WHERE code LIKE '% %'") { row ->
				if (String.metaClass.getMetaMethod("grom") && !grom) {
					"replacing spaces in study codes to underscores".grom()
					grom = true
				}

				try {
					// replace spaces with underscores
					sql.execute(sprintf("UPDATE Study SET code='%s' WHERE id=%d", row.code.replaceAll(" ","_"), row.id))
				} catch (Exception e) {
					println "fixStudyCodes database upgrade failed: " + e.getMessage()
				}
			}
		}
	}

	/**
	 * make sure all UUID's are defined
	 * @param sql
	 * @param db
	 */
	public static void fixUUIDs(sql, db, grailsApplication) {
        if (db == "org.postgresql.Driver") {
            grailsApplication.domainClasses.sort { it.naturalName }.each { d ->
                def grom        = true
                //naturalName does not work for SAMSample due to capitalized SAM but is not needed in this case.
                def tableName   = d.naturalName.toLowerCase().replaceAll(' ','_')

                // check if this domain class has a uuid
                if (sql.firstRow(sprintf("SELECT * FROM information_schema.columns WHERE columns.table_name='%s' AND columns.column_name='uuid'", tableName))) {
                    // yes, check if this table has empty uuid columns
                    sql.eachRow(sprintf("SELECT * FROM %s WHERE uuid IS NULL", tableName)) { row ->
                        // show feedback?
                        if (grom) {
                            "fixUUIDs: generating uuid's for ${d.getNaturalName()}"
                            grom = false
                        }

                        // generate a UUID
                        def newUUID = java.util.UUID.randomUUID().toString()

                        // update record with generated UUID
                        sql.execute(sprintf("UPDATE %s SET uuid='%s' WHERE id=%d", tableName, newUUID, row.id))
                    }
                }
            }
		}
	}
}