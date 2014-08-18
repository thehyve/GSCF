package gscf

import grails.test.*
import org.dbnp.gdt.*
import sun.reflect.generics.reflectiveObjects.NotImplementedException

/**
 * OntologyTests Test
 *
 * Test ontology/term functionality on domain model level
 *
 * @author keesvb
 * @since 20100510
 * @package dbnp.studycapturing
 *
 * Revision information:
 * $Rev$
 * $Author$
 * $Date$
 */
class OntologyTests extends GrailsUnitTestCase {

	final String testOntologyName = "Test ontology"
	final String testOntologyDescription = "Test description"
	final String testOntologyUrl = "http://www.test.com"
	final String testOntologyVersionNumber = "1.0"
	final int testOntologyNcboId = 0
	final int testOntologyNcboVersionedId = 0
	final String testTermName = "Test term"
	final String testAccession = 'TEST01234$'

	protected void setUp() {
		super.setUp()

		def ontology = new Ontology(
		    name: testOntologyName,
		    description: testOntologyDescription,
		    url: testOntologyUrl,
		    versionNumber: testOntologyVersionNumber,
		    ncboId: testOntologyNcboId,
		    ncboVersionedId: testOntologyNcboVersionedId
		);

		// Validate and save ontology
		assert ontology.validate()
		assert ontology.save(flush: true)
	}

	protected void tearDown() {
		super.tearDown()
	}

	/**
	 * Test if ontology was properly saved
	 */
	void testSave() {

		// Try to retrieve the ontology and make sure it's the same
		def ontologyDB = Ontology.findByName(testOntologyName)
		assert ontologyDB
		assert ontologyDB.name.equals(testOntologyName)
		assert ontologyDB.description.equals(testOntologyDescription)
		assert ontologyDB.url.equals(testOntologyUrl)
		assert ontologyDB.versionNumber.equals(testOntologyVersionNumber)
		assert ontologyDB.ncboId.equals(testOntologyNcboId)
		assert ontologyDB.ncboVersionedId.equals(testOntologyNcboVersionedId)

	}

	/**
	 * Test saving and retrieving a term within the ontology and test giveTermByName(name) and giveTerms()
	 */
	void testTermSave() {

		// Find created ontology
		def testOntology = Ontology.findByName(testOntologyName)
		assert testOntology

		// Create a new term
		def term = Term.getOrCreateTerm(testTermName, testOntology, testAccession)

		assert term.validate()
		assert term.save(flush: true)

		// Try to retrieve the term from the ontology and make sure it's the same
		def termDB = testOntology.giveTermByName(testTermName)
		assert termDB.name.equals(testTermName)
		assert termDB.accession.equals(testAccession)
		assert termDB.ontology == testOntology

		// Test giveTerms() and make sure the term is in there
		def terms = testOntology.giveTerms()
		assert terms
		assert terms.size() == 1
		assert terms.asList().first().name.equals(testTermName)
	}

	/**
	 * Ontocat test for debug purposes: show all properties of a certain ontology
	* Make this method private in order to run it
     * TODO create test for bioontology API
	 */
	private void testOntocatBioPortalDebug() {
		return NotImplementedException();
	}

	public void testAddBioPortalOntology() {
		// Add a new ontology
		def ontology = Ontology.getBioPortalOntology("1031")
		// Validate and save ontology
		if (!ontology.validate()) { ontology.errors.each { println it} }
		assert ontology.validate()
		assert ontology.save(flush: true)
		assert Ontology.findByNcboId(1031).name.equals(ontology.name)

		// Make sure that it is not possible to add an ontology twice
		def addAgain = Ontology.getBioPortalOntology("1031")
		assert !addAgain.validate()
	}
}
