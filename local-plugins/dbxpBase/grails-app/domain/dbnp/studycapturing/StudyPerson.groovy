package dbnp.studycapturing
import org.dbnp.gdt.*

/**
 * Link table which couples studies with persons and the role they have within the study
 *
 * Revision information:
 * $Rev$
 * $Author$
 * $Date$
 */
class StudyPerson extends Identity {

	// A StudyPerson relation always belongs to one study.
	static belongsTo = [parent : Study]

    /** The Person which is referenced in the Study */
	Person person

	/** The role this Person has in the Study */
	PersonRole role

    static constraints = {
    }
}
