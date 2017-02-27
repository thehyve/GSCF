/**
 * SampleSearch Domain Class
 *
 * This class provides querying capabilities for searching for samples 
 *
 * @author  Robert Horlings (robert@isdat.nl)
 * @since	20110118
 * @package	dbnp.query
 *
 * Revision information:
 * $Rev$
 * $Author$
 * $Date$
 */
package dbnp.query

import groovy.lang.Closure;

import java.util.Map;

import dbnp.studycapturing.*
import org.dbnp.gdt.*
import org.apache.commons.logging.LogFactory;

class SampleSearch extends Search {
	private static final log = LogFactory.getLog(this);

	public SampleSearch() {
		super();

		this.entity = "Sample";
	}

	/**
	 * Returns a closure for the given entitytype that determines the value for a criterion
	 * on the given object. The closure receives two parameters: the object and a criterion.
	 *
	 * For example:
	 * 		For a study search, the object given is a study. How to determine the value for that study of
	 * 		the criterion field of type sample? This is done by returning the field values for all
	 * 		samples in the study
	 * 			{ study, criterion -> return study.samples?.collect { criterion.getFieldValue( it ); } }
	 * @return
	 */
	protected Closure valueCallback( String entity ) {
		switch( entity ) {
			case "Study":
				return { sample, criterion -> return criterion.getFieldValue( sample.parent ) }
			case "Subject":
				return { sample, criterion -> return criterion.getFieldValue( sample.parentSubject ); }
			case "Sample":
				return { sample, criterion -> return criterion.getFieldValue( sample ) }
			case "Event":
				return { sample, criterion ->
					if( !sample || !sample.parentEventGroup || !sample.parentEventGroup.events || sample.parentEventGroup.events.size() == 0 )
						return null

					return sample.parentEventGroup.events?.collect { criterion.getFieldValue( it ) };
				}
			case "SamplingEvent":
				return { sample, criterion -> return criterion.getFieldValue( sample.parentEvent ); }
			case "Assay":
				return { sample, criterion ->
					def sampleAssays = Assay.findByParent( sample.parent ).findAll { it.samples?.contains( sample ) };
					if( sampleAssays && sampleAssays.size() > 0 )
						return sampleAssays.collect { criterion.getFieldValue( it ) }
					else
						return null
				}
			default:
				return super.valueCallback( entity );
		}
	}

	/**
	 * Returns the HQL name for the element or collections to be searched in, for the given entity name
	 * For example: when searching for Subject.age > 50 with Study results, the system must search in all study.subjects for age > 50.
	 * But when searching for Sample results, the system must search in sample.parentSubject for age > 50
	 *
	 * @param entity	Name of the entity of the criterion
	 * @return			HQL name for this element or collection of elements
	 */
	protected String elementName( String entity ) {
		switch( entity ) {
			case "Sample":			return "sample"
			case "Study": 			return "sample.parent"
			case "Subject":			return "sample.parentSubject"
			case "SamplingEvent":	return "sample.parentEvent"
			case "Event":			return "sample.parentEventGroup.events"	
			case "Assay":			return "sample.assays"			// Will not be used, since entityClause() is overridden
			default:				return null;
		}
	}

	/**
	 * Returns the a where clause for the given entity name
	 * For example: when searching for Subject.age > 50 with Study results, the system must search
	 *
	 * 	WHERE EXISTS( FROM study.subjects subject WHERE subject IN (...)
	 *
	 * The returned string is fed to sprintf with 3 string parameters:
	 * 		from (in this case 'study.subjects'
	 * 		alias (in this case 'subject'
	 * 		paramName (in this case '...')
	 *
	 * @param entity		Name of the entity of the criterion
	 * @return			HQL where clause for this element or collection of elements
	 */
	protected String entityClause( String entity ) {
		switch( entity ) {
			case "Assay":
				return 'EXISTS( FROM Assay assay WHERE assay.UUID IN (:%3$s) AND EXISTS( FROM assay.samples assaySample WHERE assaySample = sample ) ) '
			default:
				return super.entityClause( entity );
		}
	}

	/**
	 * Returns true iff the given entity is accessible by the user currently logged in
	 *
	 * @param entity		Study to determine accessibility for.
	 * @return			True iff the user is allowed to access this study
	 */
	protected boolean isAccessible( def entity ) {
		return entity?.parent?.canRead( this.user );
	}

	/**
	 * Returns the saved field data that could be shown on screen. This means, the data 
	 * is filtered to show only data of the query results. Also, the study title and sample
	 * name are filtered out, in order to be able to show all data on the screen without
	 * checking further
	 *
	 * @return	Map with the entity id as a key, and a field-value map as value
	 */
	public Map getShowableResultFields() {
		Map showableFields = super.getShowableResultFields()
		showableFields.each { sampleElement ->
			sampleElement.value = sampleElement.value.findAll { fieldElement ->
				fieldElement.key != "Study title" && fieldElement.key != "Sample name"
			}
		}
	}
    
    
    /**
     * Returns a list of entities from the database, based on the given UUIDs
     *
     * @param uuids      A list of UUIDs for the entities to retrieve
     */
    protected List getEntitiesByUUID( List uuids ) {
        if( !uuids )
        return []
        
        return Sample.findAll( "FROM Sample WHERE UUID in (:uuids)", [ 'uuids': uuids ] )
    }
    
    /**
     * Filters the list of entities, based on the studies that can be read.
     * As this depends on the type of entity, it should be overridden in subclasses
     */
    protected def filterAccessibleUUIDs(UUIDs, readableStudies) {
        if( !UUIDs || !readableStudies )
            return []
            
        Sample.executeQuery( "SELECT s.id, s.UUID FROM Sample s where s.UUID in (:uuids) and s.parent in (:studies)", [ uuids: UUIDs, studies: readableStudies ] )
    }

    /**
     * Returns a map with data about the results, based on the given parameters.
     * The parameters are the ones returned from the dataTablesService.
     * @param searchParams        Parameters to search
                    int      offset          Display start point in the current data set.
                    int      max             Number of records that the table can display in the current draw. It is expected that the number of records returned will be equal to this number, unless the server has fewer records to return.
                    
                    string   search          Global search field
                    
                    int      sortColumn      Column being sorted on (you will need to decode this number for your database)
                    string   sortDirection   Direction to be sorted - "desc" or "asc".
     * @return A map with HQL parts. Keys are
     *              select  Select part from the hql
                    from    From part including any required where clauses (e.g. FROM Study WHERE ids IN (:ids)
                    where   (optional) where clause to filter
                    order   (optional) order clause to sort the items
                    params  Parameters to add to the HQL query
     */
    @Override
    public Map basicResultHQL(def searchParams) {
        def hql = [ from: "", params: [:] ]
        
        hql.select = "SELECT s.id, s.name, s.parent.title"
        hql.from = "FROM Sample s WHERE s.id IN (:ids)"
        hql.params[ 'ids' ] = getResultIds()
        
        // Handle search parameter
        if( searchParams.search ) {
            hql.where = " AND ( s.name LIKE :searchParam )"
            hql.params[ 'searchParam' ] = '%' + searchParams.search + '%'
        }
        
        // Handle order
        def columns = [ 's.name', 's.parent.title' ]
        if( searchParams.sortColumn != null && searchParams.sortColumn < columns.size() ) {
            hql.order = " ORDER BY " + columns[ searchParams.sortColumn ] + " " + ( searchParams.sortDirection == "desc" ? "DESC" : "ASC" )
        }
        
        hql
    }
}
