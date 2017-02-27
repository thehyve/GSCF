/**
 * study wizard javascript functions
 *
 * @author  Jeroen Wesbeek
 * @since   20100115
 * @package wizard
 *
 * Revision information:
 * $Rev$
 * $Author$
 * $Date$
 */
var tableEditor = null;		// in global namespace
var warnOnRedirect = true;
$(document).ready(function() {
	insertOnRedirectWarning();
	onStudyWizardPage();
});

function onStudyWizardPage() {
	// GENERAL
	attachHelpTooltips();
	attachDatePickers();
	attachDateTimePickers();
	disableKeys();
	disableDatePickerKeys();

    // show creative commons agreement popup
    $(":checkbox[name^='public']").on('change', function() {
        var box = $(this);
        if (box.is(':checked')) {
            $( "#dialog-creative-commons" ).dialog({
                resizable: false,
                height:350,
                width: 800,
                modal: true,
                buttons: {
                    "Yes": function() {
                        $( this ).dialog( "close" );
                    },
                    "No": function() {
                        $( this ).dialog( "close" );
                        box.attr('checked', false);
                    }
                }
            });
        }
    });
    
	// handle and initialize table(s)
	tableEditor = new TableEditor().init({
		tableIdentifier : 'div.tableEditor',
		rowIdentifier   : 'div.row',
		columnIdentifier: 'div.firstColumn, div.column',
		headerIdentifier: 'div.header'
	});

	// initialize the ontology chooser
	new OntologyChooser().init();

	// handle term selects
	new SelectAddMore().init({
		rel	 : 'term',
		url	 : baseUrl + '/termEditor',
		vars	: 'ontologies',
		label   : 'add more...',
		style   : 'addMore',
		onClose : function(scope) {
			refreshFlow();
		}
	});

	// handle template selects
	new SelectAddMore().init({
		rel	 : 'template',
		url	 : baseUrl + '/templateEditor',
		vars	: 'entity,ontologies',
		label   : 'add / modify..',
		style   : 'modify',
		onClose : function(scope) {
			refreshFlow();
		}
	});

	// Handle person selects
	new SelectAddMore().init({
		rel	 : 'person',
		url	 : baseUrl + '/person/list?dialog=true',
		vars	: 'person',
		label   : 'add / modify persons...',
		style   : 'modify',
		onClose : function(scope) {
			refreshFlow();
		}
	});

	// Handle personRole selects
	new SelectAddMore().init({
		rel	 : 'role',
		url	 : baseUrl + '/personRole/list?dialog=true',
		vars	: 'role',
		label   : 'add / modify roles...',
		style   : 'modify',
		onClose : function(scope) {
			refreshFlow();
		}
	});

	// handle fuzzy matching
	new FuzzyStringMatcher().init({});

	// initialize accordeon(s)
	$("#accordion").accordion({autoHeight: false});
}

// disable all key presses in every text element which has a datapicker
// attached
function disableDatePickerKeys() {
	$(".hasDatepicker").each(function() {
		$(this).bind('keydown', function(e) { return false; });
	});
}

// insert a redirect confirmation dialogue to all anchors leading the
// user away from the wizard
function insertOnRedirectWarning() {
	// find all anchors that lie outside the wizard
	$('a').each(function() {
		var element = $(this);
		var re = /^#/gi;

		// bind to the anchor?
		if (!element.attr('href').match(/^#/gi) && !element.attr('href').match(/\/([^\/]+)\/wizard\/pages/gi) && element.attr('target') == "undefined") {
			// bind a warning to the onclick event
			element.bind('click', function() {
				if (warnOnRedirect) {
					return onDirectWarning();
				}
			});
		}
	});
}

function onDirectWarning() {
	return confirm('Warning: navigating away from the wizard causes loss of work and unsaved data. Are you sure you want to continue?');
}

// add datepickers to date fields
function attachDatePickers() {
	$("input[type=text][rel$='date']").each(function() {
		$(this).datepicker({
			changeMonth : true,
			changeYear  : true,
			/*numberOfMonths: 3,*/
			showButtonPanel: true,
			dateFormat  : 'dd/mm/yy',
			yearRange   : 'c-80:c+20',
			altField	: '#' + $(this).attr('name') + 'Example',
			altFormat   : 'DD, d MM, yy'
		});
	});
}

// add datetimepickers to date fields
function attachDateTimePickers() {
	$("input[type=text][rel$='datetime']").each(function() {
		$(this).datepicker({
			changeMonth	 : true,
			changeYear	  : true,
			dateFormat	  : 'dd/mm/yy',
			altField		: '#' + $(this).attr('name') + 'Example',
			altTimeField	: '#' + $(this).attr('name') + 'Example2',
			altFormat	   : 'DD, d MM, yy',
			showTime		: true,
			time24h		 : true
		});
	});
}

// obsolete, left here for backwards compatibility
function handleWizardTable() {}

/*************************************************
 *
 * Functions for RelTime fields
 *
 ************************************************/

// Show example of parsed data next to RelTime fields
function showExampleReltime(inputfield) {
	var fieldName = inputfield.name;

	var successFunc = function(data, textStatus, request) {
		var exampleField = document.getElementById(fieldName + "Example");

		if (request.status == 200 && exampleField) {
			document.getElementById(fieldName + "Example").value = data;
		}
	};

	var errorFunc = function(request, textStatus, errorThrown) {
		var exampleField = document.getElementById(fieldName + "Example");

		if (exampleField) {
			// On error, clear the example field
			document.getElementById(fieldName + "Example").value = "";
		}
	};

	$.ajax({
		url	 : baseUrl + '/studyWizard/ajaxParseRelTime?reltime=' + inputfield.value,
		success : successFunc,
		error   : errorFunc
	});
}

/*************************************************
 *
 * Functions for adding publications to the study
 *
 ************************************************/

/**
 * Adds a publication to the study using javascript
 * N.B. The publication must be added in grails when the form is submitted
 */
function addPublication(element_id) {
	/* Find publication ID and add to form */
	jQuery.ajax({
		type:"GET",
		url: baseUrl + "/publication/getID?" + $("#" + element_id + "_form").serialize(),
		success: function(data, textStatus) {
			var id = parseInt(data);

			// Put the ID in the array, but only if it does not yet exist
			var ids = getPublicationIds(element_id);

			if ($.inArray(id, ids) == -1) {
				ids[ ids.length ] = id;
				$('#' + element_id + '_ids').val(ids.join(','));

				// Show the title and a remove button
				showPublication(element_id, id, $("#" + element_id + "_form").find('[name=publication-title]').val(), $("#" + element_id + "_form").find('[name=publication-authorsList]').val(), ids.length - 1);

				// Hide the 'none box'
				$('#' + element_id + '_none').hide();
			}
		},
		error:function(XMLHttpRequest, textStatus, errorThrown) {
			alert("Publication could not be added.")
		}
	});
	return false;
}

/**
 * Removes a publication from the study using javascript
 * N.B. The deletion must be handled in grails when the form is submitted
 */
function removePublication(element_id, id) {
	var ids = getPublicationIds(element_id);
	if ($.inArray(id, ids) != -1) {
		// Remove the ID
		ids.splice($.inArray(id, ids), 1);
		$('#' + element_id + '_ids').val(ids.join(','));

		// Remove the title from the list
		var li = $("#" + element_id + '_item_' + id);
		if (li) {
			li.remove();
		}

		// Show the 'none box' if needed
		if (ids.length == 0) {
			$('#' + element_id + '_none').show();
		}

	}
}

/**
 * Returns an array of publications IDs currently attached to the study
 * The array contains integers
 */
function getPublicationIds(element_id) {
	var ids = $('#' + element_id + '_ids').val();
	if (ids == "") {
		return new Array();
	} else {
		ids_array = ids.split(',');
		for (var i = 0; i < ids_array.length; i++) {
			ids_array[ i ] = parseInt(ids_array[ i ]);
		}

		return ids_array;
	}
}

/**
 * Shows a publication on the screen
 */
function showPublication(element_id, id, title, authors, nr) {
	var deletebutton = document.createElement('img');
	deletebutton.className = 'famfamfam delete_button';
	deletebutton.setAttribute('alt', 'remove this publication');
	deletebutton.setAttribute('src', baseUrl + '/plugins/famfamfam-1.0.1/images/icons/delete.png');
	deletebutton.onclick = function() {
		removePublication(element_id, id);
		return false;
	};

	var titleDiv = document.createElement('div');
	titleDiv.className = 'title';
	titleDiv.appendChild(document.createTextNode(title));

	var authorsDiv = document.createElement('div');
	authorsDiv.className = 'authors';
	authorsDiv.appendChild(document.createTextNode(authors));

	var li = document.createElement('li');
	li.setAttribute('id', element_id + '_item_' + id);
	li.className = nr % 2 == 0 ? 'even' : 'odd';
	li.appendChild(deletebutton);
	li.appendChild(titleDiv);
	li.appendChild(authorsDiv);

	$('#' + element_id + '_list').append(li);
}

/**
 * Creates the dialog for searching a publication
 */
function createPublicationDialog(element_id) {
	/* Because of the AJAX loading of this page, the dialog will be created
	 * again, when the page is reloaded. This raises problems when reading the
	 * values of the selected publication. For that reason we check whether the
	 * dialog already exists
	 */
	if ($("." + element_id + "_publication_dialog").length == 0) {
		$("#" + element_id + "_dialog").dialog({
			title   : "Add publication",
			autoOpen: false,
			width   : 800,
			height  : 400,
			modal   : true,
			dialogClass : element_id + "_publication_dialog",
			position: "center",
			buttons : {
				Add  : function() {
					addPublication(element_id);
					$(this).dialog("close");
				},
				Close  : function() {
					$(this).dialog("close");
				}
			},
			close   : function() {
				/* closeFunc(this); */
			}
		}).width(790).height(400);
	} else {
		/* If a dialog already exists, remove the new div */
		$("#" + element_id + "_dialog").remove();
	}
}

/**
 * Opens the dialog for searching a publication
 */
function openPublicationDialog(element_id) {
	// Empty input field
	var field = $('#' + element_id);
	field.autocomplete('close');
	field.val('');

	// Show the dialog
	$('#' + element_id + '_dialog').dialog('open');
	field.focus();

	// Disable 'Add' button
	enableButton('.' + element_id + '_publication_dialog', 'Add', false);
}

/**
 * Finds a button in a jquery dialog by name
 */
function getDialogButton(dialog_selector, button_name) {
	var buttons = $(dialog_selector + ' .ui-dialog-buttonpane button');
	for (var i = 0; i < buttons.length; ++i) {
		var jButton = $(buttons[i]);
		if (jButton.text() == button_name) {
			return jButton;
		}
	}

	return null;
}

/**
 * Enables or disables a button in a selected dialog
 */
function enableButton(dialog_selector, button_name, enable) {
	var dlgButton = getDialogButton(dialog_selector, button_name);

	if (dlgButton) {
		if (enable) {
            dlgButton.removeAttr('disabled', '');
			dlgButton.removeClass('ui-state-disabled');
		} else {
			dlgButton.attr('disabled', 'disabled');
			dlgButton.addClass('ui-state-disabled');
		}
	}
}

/*************************************************
 *
 * Functions for adding contacts to the study
 *
 ************************************************/

/**
 * Adds a contact to the study using javascript
 * N.B. The contact must be added in grails when the form is submitted
 */
function addContact(element_id) {
	// FInd person and role IDs
	var person_id = $('#' + element_id + '_person').val();
	var role_id = $('#' + element_id + '_role').val();

	if (person_id == "" || person_id == 0 || role_id == "" || role_id == 0) {
		alert("Please select both a person and a role.");
		return false;
	}

	var combination = person_id + '-' + role_id;

	// Put the ID in the array, but only if it does not yet exist
	var ids = getContactIds(element_id);
	if ($.inArray(combination, ids) == -1) {
		ids[ ids.length ] = combination;
		$('#' + element_id + '_ids').val(ids.join(','));

		// Show the title and a remove button
		showContact(element_id, combination, $("#" + element_id + "_person  :selected").text(), $("#" + element_id + "_role :selected").text(), ids.length - 1);

		// Hide the 'none box'
		$('#' + element_id + '_none').hide();
	}

	return true;
}

/**
 * Removes a contact from the study using javascript
 * N.B. The deletion must be handled in grails when the form is submitted
 */
function removeContact(element_id, combination) {
	var ids = getContactIds(element_id);
	if ($.inArray(combination, ids) != -1) {
		// Remove the ID
		ids.splice($.inArray(combination, ids), 1);
		$('#' + element_id + '_ids').val(ids.join(','));

		// Remove the title from the list
		var li = $("#" + element_id + '_item_' + combination);
		if (li) {
			li.remove();
		}

		// Show the 'none box' if needed
		if (ids.length == 0) {
			$('#' + element_id + '_none').show();
		}

	}
}

/**
 * Returns an array of studyperson IDs currently attached to the study.
 * The array contains string formatted like '[person_id]-[role_id]'
 */
function getContactIds(element_id) {
	var ids = $('#' + element_id + '_ids').val();
	if (ids == "") {
		return new Array();
	} else {
		ids_array = ids.split(',');

		return ids_array;
	}
}

/**
 * Shows a contact on the screen
 */
function showContact(element_id, id, fullName, role, nr) {
	var deletebutton = document.createElement('img');
	deletebutton.className = 'famfamfam delete_button';
	deletebutton.setAttribute('alt', 'remove this person');
	deletebutton.setAttribute('src', baseUrl + '/plugins/famfamfam-1.0.1/images/icons/delete.png');
	deletebutton.onclick = function() {
		removeContact(element_id, id);
		return false;
	};

	var titleDiv = document.createElement('div');
	titleDiv.className = 'person';
	titleDiv.appendChild(document.createTextNode(fullName));

	var authorsDiv = document.createElement('div');
	authorsDiv.className = 'role';
	authorsDiv.appendChild(document.createTextNode(role));

	var li = document.createElement('li');
	li.setAttribute('id', element_id + '_item_' + id);
	li.className = nr % 2 == 0 ? 'even' : 'odd';
	li.appendChild(deletebutton);
	li.appendChild(titleDiv);
	li.appendChild(authorsDiv);

	$('#' + element_id + '_list').append(li);
}

/*************************************************
 *
 * Functions for adding users (readers or writers) to the study
 *
 ************************************************/

/**
 * Adds a user to the study using javascript
 */
function addUser(element_id) {
	/* Find publication ID and add to form */
	id = parseInt($("#" + element_id + "_form select").val());

	// Put the ID in the array, but only if it does not yet exist
	var ids = getUserIds(element_id);

	if ($.inArray(id, ids) == -1) {
		ids[ ids.length ] = id;
		$('#' + element_id + '_ids').val(ids.join(','));

		// Show the title and a remove button
		showUser(element_id, id, $("#" + element_id + "_form select option:selected").text(), ids.length - 1);

		// Hide the 'none box'
		$('#' + element_id + '_none').css('display', 'none');
	}

	return false;
}

/**
 * Removes a user from the study using javascript
 * N.B. The deletion must be handled in grails when the form is submitted
 */
function removeUser(element_id, id) {
	var ids = getUserIds(element_id);
	if ($.inArray(id, ids) != -1) {
		// Remove the ID
		ids.splice($.inArray(id, ids), 1);
		$('#' + element_id + '_ids').val(ids.join(','));

		// Remove the title from the list
		var li = $("#" + element_id + '_item_' + id);
		if (li) {
			li.remove();
		}

		// Show the 'none box' if needed
		if (ids.length == 0) {
			$('#' + element_id + '_none').css('display', 'inline');
		}

	}
}

/**
 * Returns an array of user IDs currently attached to the study
 * The array contains integers
 */
function getUserIds(element_id) {
	var ids = $('#' + element_id + '_ids').val();
	if (ids == "") {
		return new Array();
	} else {
		ids_array = ids.split(',');
		for (var i = 0; i < ids_array.length; i++) {
			ids_array[ i ] = parseInt(ids_array[ i ]);
		}

		return ids_array;
	}
}

/**
 * Shows a publication on the screen
 */
function showUser(element_id, id, username, nr) {
	var deletebutton = document.createElement('img');
	deletebutton.className = 'famfamfam delete_button';
	deletebutton.setAttribute('alt', 'remove this user');
	deletebutton.setAttribute('src', baseUrl + '/plugins/famfamfam-1.0.1/images/icons/delete.png');
	deletebutton.onclick = function() {
		removeUser(element_id, id);
		return false;
	};

	var titleDiv = document.createElement('div');
	titleDiv.className = 'username';
	titleDiv.appendChild(document.createTextNode(username));

	var li = document.createElement('li');
	li.setAttribute('id', element_id + '_item_' + id);
	li.className = nr % 2 == 0 ? 'even' : 'odd';
	li.appendChild(deletebutton);
	li.appendChild(titleDiv);

	$('#' + element_id + '_list').append(li);
}

/**
 * Creates the dialog for searching a publication
 */
function createUserDialog(element_id) {
	/* Because of the AJAX loading of this page, the dialog will be created
	 * again, when the page is reloaded. This raises problems when reading the
	 * values of the selected publication. For that reason we check whether the
	 * dialog already exists
	 */
	if ($("." + element_id + "_user_dialog").length == 0) {
		$("#" + element_id + "_dialog").dialog({
			title   : "Add user",
			autoOpen: false,
			width   : 800,
			height  : 400,
			modal   : true,
			dialogClass : element_id + "_user_dialog",
			position: "center",
			buttons : {
				Add  : function() {
					addUser(element_id);
					$(this).dialog("close");
				},
				Close  : function() {
					$(this).dialog("close");
				}
			},
			close   : function() {
				/* closeFunc(this); */
			}
		}).width(790).height(400);
	} else {
		/* If a dialog already exists, remove the new div */
		$("#" + element_id + "_dialog").remove();
	}
}

/**
 * Opens the dialog for searching a publication
 */
function openUserDialog(element_id) {
	// Empty input field
	var field = $('#' + element_id);
	field.val('');

	// Show the dialog
	$('#' + element_id + '_dialog').dialog('open');
	field.focus();

	// Disable 'Add' button
	//enableButton( '.' + element_id + '_user_dialog', 'Add', false );
}

/*************************************************
 *
 * Functions for adding userGroups (readerGroups or writerGroups) to the study
 *
 ************************************************/

/**
 * Adds a user to the study using javascript
 */
function addUserGroup(element_id) {
	/* Find publication ID and add to form */
	id = parseInt($("#" + element_id + "_form select").val());

	// Put the ID in the array, but only if it does not yet exist
	var ids = getUserGroupIds(element_id);

	if ($.inArray(id, ids) == -1) {
		ids[ ids.length ] = id;
		$('#' + element_id + '_ids').val(ids.join(','));

		// Show the title and a remove button
		showUserGroup(element_id, id, $("#" + element_id + "_form select option:selected").text(), ids.length - 1);

		// Hide the 'none box'
		$('#' + element_id + '_none').css('display', 'none');
	}

	return false;
}

/**
 * Removes a userGroup from the study using javascript
 * N.B. The deletion must be handled in grails when the form is submitted
 */
function removeUserGroup(element_id, id) {
	var ids = getUserGroupIds(element_id);
	if ($.inArray(id, ids) != -1) {
		// Remove the ID
		ids.splice($.inArray(id, ids), 1);
		$('#' + element_id + '_ids').val(ids.join(','));

		// Remove the title from the list
		var li = $("#" + element_id + '_item_' + id);
		if (li) {
			li.remove();
		}

		// Show the 'none box' if needed
		if (ids.length == 0) {
			$('#' + element_id + '_none').css('display', 'inline');
		}

	}
}

/**
 * Returns an array of userGroupIDs currently attached to the study
 * The array contains integers
 */
function getUserGroupIds(element_id) {
	var ids = $('#' + element_id + '_ids').val();
	if (ids == "") {
		return new Array();
	} else {
		ids_array = ids.split(',');
		for (var i = 0; i < ids_array.length; i++) {
			ids_array[ i ] = parseInt(ids_array[ i ]);
		}

		return ids_array;
	}
}

/**
 * Shows a publication on the screen
 */
function showUserGroup(element_id, id, groupName, nr) {
	var deletebutton = document.createElement('img');
	deletebutton.className = 'famfamfam delete_button';
	deletebutton.setAttribute('alt', 'remove this usergroup');
	deletebutton.setAttribute('src', baseUrl + '/plugins/famfamfam-1.0.1/images/icons/delete.png');
	deletebutton.onclick = function() {
		removeUserGroup(element_id, id);
		return false;
	};

	var titleDiv = document.createElement('div');
	titleDiv.className = 'groupName';
	titleDiv.appendChild(document.createTextNode(groupName));

	var li = document.createElement('li');
	li.setAttribute('id', element_id + '_item_' + id);
	li.className = nr % 2 == 0 ? 'even' : 'odd';
	li.appendChild(deletebutton);
	li.appendChild(titleDiv);

	$('#' + element_id + '_list').append(li);
}

/**
 * Creates the dialog for userGroup
 */
function createUserGroupDialog(element_id) {
	/* Because of the AJAX loading of this page, the dialog will be created
	 * again, when the page is reloaded. This raises problems when reading the
	 * values of the selected publication. For that reason we check whether the
	 * dialog already exists
	 */
	if ($("." + element_id + "_userGroup_dialog").length == 0) {
		$("#" + element_id + "_dialog").dialog({
			title   : "Add UserGroup",
			autoOpen: false,
			width   : 800,
			height  : 400,
			modal   : true,
			dialogClass : element_id + "_userGroup_dialog",
			position: "center",
			buttons : {
				Add  : function() {
					addUserGroup(element_id);
					$(this).dialog("close");
				},
				Close  : function() {
					$(this).dialog("close");
				}
			},
			close   : function() {
				/* closeFunc(this); */
			}
		}).width(790).height(400);
	} else {
		/* If a dialog already exists, remove the new div */
		$("#" + element_id + "_dialog").remove();
	}
}

/**
 * Opens the dialog for searching a publication
 */
function openUserGroupDialog(element_id) {
	// Empty input field
	var field = $('#' + element_id);
	field.val('');

	// Show the dialog
	$('#' + element_id + '_dialog').dialog('open');
	field.focus();

	// Disable 'Add' button
	//enableButton( '.' + element_id + '_user_dialog', 'Add', false );
}