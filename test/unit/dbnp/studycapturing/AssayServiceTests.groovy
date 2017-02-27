package dbnp.studycapturing

import grails.test.*
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.usermodel.Cell
import org.dbnp.gdt.*
import org.dbnp.gdt.AssayModule
import org.dbnp.gdt.TemplateStringField

/**
 * AssayServiceTests Test
 *
 * @author  s.h.sikkema@gmail.com
 * @since	20101218
 * @package	dbnp.studycapturing
 *
 * Revision information:
 * $Rev$
 * $Author$
 * $Date$
 */
class AssayServiceTests extends GrailsUnitTestCase {

    def service = new AssayService()

    protected void setUp() {
        super.setUp()


        mockDomain(Term,          [ new Term(id: 1, name: 'Human')])

        mockDomain(TemplateField, [ new TemplateField(id: 1, name: 'tf1', type: TemplateFieldType.STRING),
                                    new TemplateField(id: 2, name: 'tf2', type: TemplateFieldType.STRING),
                                    new TemplateField(id: 3, name: 'tf3', type: TemplateFieldType.STRING)])

        mockDomain(Template,      [ new Template(id: 1, fields: [TemplateField.get(1), TemplateField.get(2)]),
                                    new Template(id: 2, fields: [TemplateField.get(3)])])

        def mockGdtService = [getTemplateFieldTypeByCasedName: { a -> TemplateStringField }]
        def mockLog = [ info:{a->println a},error:{a->println "Error: $a"}]

        mockDomain(Subject,       [ new Subject(gdtService: mockGdtService, id: 1, name:'subject1', template: Template.get(1), species: Term.get(1)),
                                    new Subject(gdtService: mockGdtService, id: 2, name:'subject2', template: Template.get(2), species: Term.get(1))])

        mockDomain(SamplingEvent, [ new SamplingEvent(id:1, startTime: 2, duration: 5, sampleTemplate: new Template()),
                                    new SamplingEvent(id:2, startTime: 12, duration: 15, sampleTemplate: new Template())])

        mockDomain(Event,         [ new Event(id: 1, startTime: 6, endTime: 7), new Event(id: 2, startTime: 8, endTime: 9)])//, new Event(id: 2, startTime: 8, endTime: 9)])

        mockDomain(EventGroup,    [ new EventGroup(id:1, name: 'EventGroup1', events: [Event.get(1)], samplingEvents: [SamplingEvent.get(1)]),
                                    new EventGroup(id:2, name: 'EventGroup2', events: [Event.get(2)], samplingEvents: [SamplingEvent.get(2)])])

        mockDomain(Sample,        [ new Sample(id: 1, name:'sample1', parentSubject: Subject.get(1), parentEvent: SamplingEvent.get(1), parentEventGroup: EventGroup.get(1), UUID: 'uuid1'),
                                    new Sample(id: 2, name:'sample2', parentSubject: Subject.get(2), parentEvent: SamplingEvent.get(2), parentEventGroup: EventGroup.get(2), UUID: 'uuid2'),
                                    new Sample(id: 3, name:'sample3', parentSubject: Subject.get(2), parentEvent: SamplingEvent.get(2), parentEventGroup: EventGroup.get(2), UUID: 'uuid3')])

        mockDomain(AssayModule,   [ new AssayModule(id: 1, url: 'http://www.example.com') ])

        mockDomain(Assay,         [ new Assay(id: 1, module: AssayModule.get(1), samples: [Sample.get(1),Sample.get(2), Sample.get(3)]),
                                    new Assay(id: 2, module: AssayModule.get(1), samples: [])])

        Subject.get(1).metaClass.static.log = mockLog

        Subject.get(1).setFieldValue('tf1', 'tfv1')
        Subject.get(1).setFieldValue('tf2', 'tfv2')
        Subject.get(2).setFieldValue('tf3', 'tfv3')

        // mock authenticationService
        service.authenticationService = [
                isLoggedIn: { true },
                logInRemotely: { a, b, c -> },
                logOffRemotely: { a, b -> },
                getLoggedInUser: { null }
        ]

        // mock moduleCommunicationService
        service.moduleCommunicationService = [
                isModuleReachable: { a -> true },
                callModuleMethod: { consumer, path, c, d ->
                    [['uuid1', 'uuid2', 'uuid3'],
                     ['measurement1','measurement2','measurement3','measurement4'],
                     [1,2,3,4,5,6,7,8,9,10,11,12] ]
                }
        ]

    }

    protected void tearDown() {
        super.tearDown()
    }

    void testExportColumnWiseDataAsExcelFile() {

        def columnData = [
                Category1: [Column1: [1,2,3], Column2: [4,5,6]],
                Category2: [Column3: [7,8,9], Column4: [10,11,12], Column5: [13,14,15]],
                EmptyCategory: [:]
        ]

        def rowData = [
                ['Category1','','Category2','',''],
                ['Column1','Column2','Column3','Column4','Column5'],
                [1,4,7,10,13],
                [2,5,8,11,14],
                [3,6,9,12,15]]

        ByteArrayOutputStream   baos    = new ByteArrayOutputStream(1024)
        DataOutputStream        dos     = new DataOutputStream(baos)

        service.exportColumnWiseDataToExcelFile(columnData, dos, true)

        ByteArrayInputStream    bais    = new ByteArrayInputStream(baos.toByteArray())

        assertEquals 'Expected Excel contents', rowData, readExcelIntoArray(bais)

    }

    def readExcelIntoArray = { inputStream ->

        Workbook wb = WorkbookFactory.create(inputStream)

        def sheet = wb.getSheetAt(0)

        def readData = []

        sheet.eachWithIndex { row, ri ->

            readData[ri] = []

            row.eachWithIndex { cell, ci ->
                // TODO: what happens when there are empty cells
                readData[ri][ci] = (cell.cellType == Cell.CELL_TYPE_NUMERIC) ? cell.numericCellValue : cell.stringCellValue

            }

        }

        readData

    }

    // class to test writing non number/string values to excel
    class SomeCustomType { String toString() {'13'} }

    void testExportRowWiseDataToExcelFile() {

        SomeCustomType someCustomType = new SomeCustomType()

        def rowData = [
                ['Category1','','Category2','',''],
                ['Column1','Column2','Column3','Column4','Column5'],
                [1,4,7,10,someCustomType],
                [2,5,8,11,null],
                [3,6,9,12,15]]

        ByteArrayOutputStream   baos    = new ByteArrayOutputStream(1024)
        DataOutputStream        dos     = new DataOutputStream(baos)

        service.exportRowWiseDataToExcelFile(rowData, dos, false)

        ByteArrayInputStream    bais    = new ByteArrayInputStream(baos.toByteArray())

        // replace custom type with expected written value
        rowData[2][4] = '13'
        rowData[3][4] = ''

        def result = readExcelIntoArray(bais)

        assertEquals 'Excel contents', rowData, result

    }
    
    void testExportRowWiseDataToExcelFileWithRealisticData() {

        def rowData = [
                ['Subject Data', '', '', 'Sampling Event Data', '', '', 'Sample Data', '', ''],
                ['name', 'species', 'Gender', 'startTime', 'sampleTemplate', 'Sample volume', 'name', 'material', 'Text on vial'],
                [11, 'Homo Sapiens', 'Male', 367200, 'Human blood sample', 4.5, '11_A', 'blood plasma', 'T8.93650593495392']]

        ByteArrayOutputStream   baos    = new ByteArrayOutputStream(1024)
        DataOutputStream        dos     = new DataOutputStream(baos)

        service.exportRowWiseDataToExcelFile(rowData, dos, false)

        ByteArrayInputStream    bais    = new ByteArrayInputStream(baos.toByteArray())

        def result = readExcelIntoArray(bais)

        assertEquals 'Excel contents', rowData, result
    }

    void testTemplateFieldsAreCollected() {

        def assay = Assay.get(1)

        def fieldMap = [
                'Subject Data':[[name:'tf1', displayName: 'tf1'],[name:'tf2', displayName: 'tf2'],[name:'tf3', displayName: 'tf3'],[name:'species', displayName: 'species'],[name:'name', displayName: 'name']],
                'Sampling Event Data':[[name:'startTime', displayName: 'startTime'],[name:'duration', displayName: 'duration']],
                'Sample Data':[[name:'name', displayName: 'name']],
                'Event Group':[[name:'name', displayName: 'name']]
        ]

        def measurementTokens = ['measurement1', 'measurement2', 'measurement3', 'measurement4']

        String.metaClass.'encodeAsURL' = {delegate}

        def assayData = service.collectAssayData(assay, fieldMap, [])

        def sample1index = assayData.'Sample Data'.'name'.findIndexOf{it == 'sample1'}
        def sample2index = assayData.'Sample Data'.'name'.findIndexOf{it == 'sample2'}

        assertEquals 'Subject template field', ['tfv1',''], assayData.'Subject Data'.tf1[sample1index, sample2index]
        assertEquals 'Subject template field', ['tfv2',''], assayData.'Subject Data'.tf2[sample1index, sample2index]
        assertEquals 'Subject template field', ['','tfv3'], assayData.'Subject Data'.tf3[sample1index, sample2index]
        assertEquals 'Subject species template field', ['Human', 'Human', 'Human'], assayData.'Subject Data'.species*.toString()
        assertEquals 'Subject name template field', ['subject1','subject2'], assayData.'Subject Data'.name[sample1index, sample2index]

        assertEquals 'Sampling event template fields', ['2s','12s'], assayData.'Sampling Event Data'.startTime[sample1index, sample2index]
        assertEquals 'Sampling event template fields', ['5s','15s'], assayData.'Sampling Event Data'.duration[sample1index, sample2index]
//        assertEquals 'Sampling event template fields', '[null, null]', assayData.'Sampling Event Data'.sampleTemplate.toString()
        assertEquals 'Sample template fields', ['sample1', 'sample2'], assayData.'Sample Data'.name[sample1index, sample2index]

        assertEquals 'Event group names', ['EventGroup1', 'EventGroup2'], assayData.'Event Group'.name[sample1index, sample2index]

        assertEquals 'Module Measurement Data', ['measurement1': [1,2,3], 'measurement2': [4,5,6], 'measurement3': [7,8,9], 'measurement4': [10,11,12]], assayData.'Module Measurement Data'
    }

//    // Test for out of memory exception when exporting large excel workbooks
//    // - xls format can handle max 256 columns
//    // - but, xls format can handle more data (1000000 cells, no problem -> 27.2 MB)
//    // - we'll need a good method to overcome the xlsx heap space problem
//    void testExportLargeExcelWorkbook() {
//
//        def file    = new File( '/tmp', 'tmpFile.xls' )
//
//        def os      = file.newOutputStream()
//
//        def rowData = (0..1).collect { row ->
//
//            (0..256).collect { col ->
//                "$row - $col"
//            }
//
//        }
//
////        try {
//            service.exportRowWiseDataToExcelFile rowData, os, false
////        } catch (Exception e) {
////            e.printStackTrace()
////            assert false
////        } finally {
//            os.flush()
////            file.delete()
////        }
//    }

    void testCSVOutput() {

        // We're testing:
        // - strings containing any newlines, comma's, or double quotes should
        //   be surrounded with double quotes
        // - double quotes should be escaped by double quotes ( " -> "" )
        // - other strings and numbers should remain 'quoteless'
        // - is the custom delimiter (e.g. tab, comma, semicolon) correctly handled
        // - null values are exported as empty strings
        // - possibility to use comma's as decimal separators
        // - 2.0 -> 2 and 2.1 -> 2.1
        // - no thousand separators

        def rowData = [["""a
b""","a,b","a\"b", "abc"],[1,2.1,"3,1"],[null,2, 2.0, 2000]]

        def baos = new ByteArrayOutputStream()

        service.exportRowWiseDataToCSVFile rowData, baos, '\t'
        assertEquals 'CSV Output', '"a\nb"\t"a,b"\t"a""b"\tabc\n1\t2.1\t"3,1"\n\t2\t2\t2000', baos.toString()

        baos.reset()

        service.exportRowWiseDataToCSVFile rowData, baos, ','
        assertEquals 'CSV Output', '"a\nb","a,b","a""b",abc\n1,2.1,"3,1"\n,2,2,2000', baos.toString()

        baos.reset()

        service.exportRowWiseDataToCSVFile rowData, baos, ';', java.util.Locale.GERMAN
        assertEquals 'CSV Output', '"a\nb";"a,b";"a""b";abc\n1;2,1;"3,1"\n;2;2;2000', baos.toString()


    }
}
