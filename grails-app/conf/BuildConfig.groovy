/**
 * Project build configuration
 * @Author  Jeroen Wesbeek
 * @Since   20091027
 * @Description
 *
 * Specific build configuration for the GSCF application
 *
 * Revision information:
 * $Rev$
 * $Author$
 * $Date$
 */

grails.project.dependency.resolution = {
    // inherit Grails' default dependencies
    inherits("global") {
        // uncomment to disable ehcache
        // excludes 'ehcache'
    }
    log "warn" // log level of Ivy resolver, either 'error', 'warn', 'info', 'debug' or 'verbose'
    repositories {
	    grailsCentral()
	    grailsRepo "http://grails.org/plugins"
	    mavenCentral()

	    // grails 1.3.9 does not seem to properly inherit maven repo's from plugins
	    // so explicitely put ontocat in here. When upgraded to Grails 2.x this can
	    // probably be removed
	    mavenRepo "http://ontocat.sourceforge.net/maven/repo"

		// other maven repo's
	    mavenRepo "http://nexus.dbnp.org/content/repositories/releases"
	    //mavenRepo "http://repository.springsource.com/maven/bundles/release"
        //mavenRepo "http://repository.springsource.com/maven/bundles/external"
        //mavenRepo "http://repository.springsource.com/maven/libraries/release"
        //mavenRepo "http://repository.springsource.com/maven/libraries/external"

	    // Repository for ISATAB tools
	    //mavenRepo "http://frog.oerc.ox.ac.uk:8080/nexus-2.1.2/content/repositories/releases"
    }
    dependencies {
        // specify dependencies here under either 'build', 'compile', 'runtime', 'test' or 'provided' scopes eg.
        // runtime 'mysql:mysql-connector-java:5.1.5'
		runtime 'postgresql:postgresql:9.1-901.jdbc4'
		compile 'org.apache.poi:poi:3.7'
		compile 'org.apache.poi:poi-ooxml:3.7'
		compile 'org.apache.poi:poi-ooxml-schemas:3.7'

        // quartz jar is not packaged in the war properly
        // make sure to pull it in
        //compile('org.quartz-scheduler:quartz:1.8.4') {
            // resolve SLF4J version conflict:
            // SLF4J: The requested version 1.5.8 by your slf4j binding is not compatible with [1.6]
            //excludes([ group: 'org.slf4j', name: 'slf4j-api', version: '1.5.8'])
        //}

	    /*compile('org.isatools:ISAcreator:1.7.0') {
		    transitive = false
	    } */
		// dependency for ISATAB schema
		//compile 'net.sourceforge.collections:collections-generic:4.01'

	    // we seem to be needing XStream in some cases
	    compile("com.thoughtworks.xstream:xstream:1.3.1")

	    //runtime 'hsqldb:hsqldb:1.8.0.10'

        compile "org.grails:grails-webflow:$grailsVersion"
    }
	plugins {

		// tomcat plugin should not end up in the WAR file
		provided(
				":tomcat:$grailsVersion",
		)

		compile(
            ":dbxp-base:0.1.3.1.rc5",
            ":dbxp-sam:0.9.6.3.rc8",

            ":hibernate:$grailsVersion",

//            ":jquery:latest.integration",
//            ':jquery-ui:latest.integration',

            //Temporary static version due to not working table tooltip with jQuery 1.10.2
            ":jquery:1.8.3",
            ':jquery-ui:1.8.24',

            ":resources:latest.integration",
            ":spring-security-core:1.2.7.3",

            ":famfamfam:1.0.1",
            ":mail:1.0.1",
            ":quartz:1.0-RC5"
        )

        compile(":gdtimporter:0.5.6.9"){transitive = false}

        if (System.getProperty("grails.env") == "development") {
            // development mode only Plugins
            compile(":console:1.2",
                    ":trackr:0.7.3",
                    ":grom:latest.integration"
            )
        }

        compile ':webflow:2.0.0', {
            exclude 'grails-webflow'
        }

        // add { transative = false } to ignore dependency transition

//        runtime ':grails-melody:1.11'
	}
}

//grails.plugin.location.'grom' = '../grom'
//grails.plugin.location.'ajaxflow' = '../ajaxflow'
//grails.plugin.location.'jumpbar' = '../jumpbar'
//grails.plugin.location.'gdtimporter' = '../GDTImporter'
//grails.plugin.location.'dbxp-base' = '../dbxpBase'
//grails.plugin.location.'spring-security-core' = '../grails-spring-security-core'
//grails.plugin.location.'dbxpSam' = '../SAM'
//grails.plugin.location.'dbxp-base' = '../dbxpBase'
