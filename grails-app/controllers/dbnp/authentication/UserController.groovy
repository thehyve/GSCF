/* Copyright 2009-2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dbnp.authentication

import grails.converters.JSON
import grails.plugin.springsecurity.annotation.Secured
import dbnp.studycapturing.Study
import org.springframework.dao.DataIntegrityViolationException

/**
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
class UserController {

	def userCache
	def springSecurityService

	static defaultAction = 'search'

	def create = {
		if (!session.gscfUser.shibbolethUser) {
			def user = new SecUser(params)
			[user: user, authorityList: sortedRoles()]
		} else {
			response.sendError(404)
		}
	}

	def save = {
		def user = new SecUser(params)
		if (params.password) {
			user.password = springSecurityService.encodePassword(params.password, params.username)
		}
		if (!user.save(flush: true)) {
			// Reset encoded password
			user.password = params.password
			render view: 'create', model: [user: user, authorityList: sortedRoles()]
			return
		}

		addRoles(user)
		flash.message = "${message(code: 'default.created.message', args: [message(code: 'user.label', default: 'User'), user.id])}"
		redirect action: edit, id: user.id
	}

	def edit = {
		def user = findById()

		if (!user) return

		return buildUserModel(user)
	}

	def update = {
		def user = findById()
		if (!user) return

		if (!versionCheck('user.label', 'User', user, [user: user])) {
			return
		}
        
                // Handle relation with user groups
                def selectedUserGroups = user.getUserGroups()
                for(selectedUserGroup in selectedUserGroups){
                        def studies = Study.all.findAll{it.readerGroups.id.contains(selectedUserGroup.id)}
                        for (studyU in studies){
                                studyU.removeFromReaders(user)
                                studyU.save(flush: true) 
                        }
                        studies = Study.all.findAll{it.writerGroups.id.contains(selectedUserGroup.id)}
                        for (studyU in studies){
                                studyU.removeFromWriters(user)
                                studyU.save(flush: true) 
                        }
                        SecUserSecUserGroup.remove(user,selectedUserGroup, true)
                }
		
		def oldPassword = user.password
		user.properties = params
		if (params.password && !params.password.equals(oldPassword)) {
			user.password = springSecurityService.encodePassword(params.password, params.username)
		}

		if (!user.save()) {
			render view: 'edit', model: buildUserModel(user)
			return
		}

		SecUserSecRole.removeAll user
		addRoles user
               
                if(params.optionalGroups){                  
                    def userGroups = params.list('optionalGroups')
                    for(userGroup in userGroups){
                        SecUserGroup singleGroup = SecUserGroup.get(userGroup)
                        
                        def studies = Study.all.findAll{it.readerGroups.contains(singleGroup)}
                        for (studyU in studies){
                                studyU.addToReaders(user)
                                studyU.save(flush: true) 
                        }
                        studies = Study.all.findAll{it.writerGroups.contains(singleGroup)}
                        for (studyU in studies){
                                studyU.addToWriters(user)
                                studyU.save(flush: true) 
                        }
                                
                        SecUserSecUserGroup sec = new SecUserSecUserGroup(secUser:user, secUserGroup: singleGroup)
                        sec.save(flush: true)
                    }
                }
        
		userCache.removeUserFromCache user.username

		flash.message = "${message(code: 'default.updated.message', args: [message(code: 'user.label', default: 'User'), user.id])}"
		redirect action: edit, id: user.id
	}

	def delete = {
		def user = findById()
		if (!user) return

		try {
			SecUserSecRole.removeAll user
                        SecUserSecUserGroup.removeAll user
			user.delete flush: true

			userCache.removeUserFromCache user.username
			
			flash.message = "${message(code: 'default.deleted.message', args: [message(code: 'user.label', default: 'User'), params.id])}"
			redirect action: search
		}
		catch (DataIntegrityViolationException e) {
			flash.userError = "${message(code: 'default.not.deleted.message', args: [message(code: 'user.label', default: 'User'), params.id])}"
			redirect action: edit, id: params.id
		}
	}

	def search = {
		[enabled: 0, accountExpired: 0, accountLocked: 0, passwordExpired: 0]
	}

	def userSearch = {

		boolean useOffset = params.containsKey('offset')
		params.max = params.max ?: 10
		params.offset = params.offset ?: 0

		def hql = new StringBuilder('FROM SecUser u WHERE 1=1 ')
		def queryParams = [:]

		for (name in ['username']) {
			if (params[name]) {
				hql.append " AND LOWER(u.$name) LIKE :$name"
				queryParams[name] = "%${params[name].toLowerCase()}%"
			}
		}

		for (name in ['enabled', 'accountExpired', 'accountLocked', 'passwordExpired']) {
			if (params[name]) { // check if a value is specified at all
				def value = params[name] as Integer
				if (value) { // 0 indicates either option, so don't filter in that case
					hql.append " AND u.$name=:$name"
					queryParams[name] = value == 1 // value can be 1 or -1
				}
            }
		}

		int totalCount = SecUser.executeQuery("SELECT COUNT(DISTINCT u) $hql", queryParams)[0]

	    def max = params.max as Integer
		def offset = params.offset as Integer

		def orderBy = " ORDER BY u.${params.sort ?: 'username'} ${params.order ?: 'ASC'}"

		def results = SecUser.executeQuery(
				"SELECT DISTINCT u $hql $orderBy",
				queryParams, [max: max, offset: offset])
		def model = [results: results, totalCount: totalCount, searched: true]

		// add query params to model for paging
		def defaultParams = [
				username: '',
				enabled: 0,
				accountExpired: 0,
				accountLocked: 0,
				passwordExpired: 0,
				sort: null,
				order: null
		]
		defaultParams.each{ key, defaultValue ->
			model[key] = params[key] ?: defaultValue
		}

		render view: 'search', model: model
	}

	/**
	 * Ajax call used by autocomplete textfield.
	 */
	def ajaxUserSearch = {

		def jsonData = []

		if (params.term?.length() > 2) {
			String username = params.term

                        def max = Math.min( params.max ?: 10, 100 )

			def results = SecUser.executeQuery(
					"SELECT DISTINCT u.username " +
					"FROM SecUser u " +
					"WHERE LOWER(u.username) LIKE :name " +
					"ORDER BY u.username",
					[name: username.toLowerCase() + "%"],
					[max: max])

			for (result in results) {
				jsonData << [value: result]
			}
		}

		render text: jsonData as JSON, contentType: 'application/json'
	}

	protected void addRoles(user) {
		for (String key in params.keySet()) {
			if (key.contains('ROLE') && 'on' == params.get(key)) {
				SecUserSecRole.create user, SecRole.findByAuthority(key), true
			}
		}
	}

	protected Map buildUserModel(user) {

		List roles = sortedRoles()
		Set userRoleNames = user.authorities*.authority
		def granted = [:]
		def notGranted = [:]
		for (role in roles) {
			if (userRoleNames.contains(role.authority)) {
				granted[(role)] = userRoleNames.contains(role.authority)
			}
			else {
				notGranted[(role)] = userRoleNames.contains(role.authority)
			}
		}
                
                def groups = SecUserGroup.all.sort { it.groupName }
                def selectedGroups = user.getUserGroups()
                

		return [user: user, roleMap: granted + notGranted, groups:groups, selectedGroups:selectedGroups]
	}

	protected findById() {
		if(!params.id) {
			flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'user.label', default: 'User'), params.id])}"
			redirect action: search
		}

		def user = SecUser.get(params.id)
		
		if (!user) {
			flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'user.label', default: 'User'), params.id])}"
			redirect action: search
		}

		user
	}

	protected List sortedRoles() {
		SecRole.list().sort { it.authority }
	}

	protected boolean versionCheck(String messageCode, String messageCodeDefault, instance, model) {
		if (params.version) {
			def version = params.version.toLong()
			if (instance.version > version) {
				instance.errors.rejectValue('version', 'default.optimistic.locking.failure',
						[message(code: messageCode, default: messageCodeDefault)] as Object[],
						"Another user has updated this instance while you were editing")
				render view: 'edit', model: model
				return false
			}
		}
		true
	}
}
