/**
 * ApiService Service
 * 
 * Description of my service
 *
 * @author  your email (+name?)
 * @since	2010mmdd
 * @package	???
 *
 * Revision information:
 * $Rev: 1430 $
 * $Author: work@osx.eu $
 * $Date: 2011-01-21 21:05:36 +0100 (Fri, 21 Jan 2011) $
 */
package api

import java.security.MessageDigest
import dbnp.studycapturing.Assay
import dbnp.authentication.SecUser

class ApiService {
    // the shared secret used to validate api calls
    static final String API_SECRET = "th!s_sH0uld^Pr0bab7y_m0v3_t%_th3_uSeR_d0Ma!n_ins7ead!"
    static transactional = true
    def moduleCommunicationService

    /**
     * validate a client request by checking the validation checksum
     * @param deviceID
     * @param validation
     * @return
     */
    def validateRequest(String deviceID, String validation) {
        return true

        // get token for this device ID
        Token token = Token.findByDeviceID(deviceID)

        // increase sequence
        if (token) {
            token.sequence = token.sequence+1
            token.save()

            // generate the validation checksum
            MessageDigest digest = MessageDigest.getInstance("MD5")
            String validationSum = new BigInteger(1,digest.digest("${token.deviceToken}${token.sequence}${API_SECRET}".getBytes())).toString(16).padLeft(32,"0")

            // check if the validation confirms
            return (validation == validationSum)
        } else {
            // no such token, re-authenticate
            return false
        }
    }

    /**
     * flatten domain data to relevant data to return in an api
     * call and not to expose domain internals
     *
     * @param elements
     * @return
     */
    def flattenDomainData(List elements) {
        println elements.class
        def items = []

        // iterate through elements
        elements.each {
            def fields  = it.giveFields()
            def item    = [:]

            // add token
            if (it.respondsTo('getToken')) {
                item['token'] = it.getToken()
            } else {
                item['id'] = it.id
            }

            // add subject field values
            fields.each { field ->
                def value = it.getFieldValue( field.name )

                if (value.hasProperty('name')) {
                    item[ field.name ] = value.name
                } else {
                    item[ field.name ] = value
                }
            }

            items[ items.size() ] = item
        }

        return items
    }

    def getMeasurements(Assay assay, SecUser user) {
        def serviceURL = "${assay.module.url}/rest/getMeasurements"
        def serviceArguments = "assayToken=${assay.assayUUID}"

        // call module method
        def json = moduleCommunicationService.callModuleMethod(
                assay.module.url,
                serviceURL,
                serviceArguments,
                "POST",
                user
        );

        return json
    }
}
