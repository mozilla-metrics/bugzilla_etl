/* Workflow:
Create the current state object

For each row containing latest state data (fields from bugs table record, fields from other tables (i.e. attachments, dependencies)
    Update the current state object with the latest field values

Walk backward through activity records from bugs_activity (and other activity type tables). For each set of activities:
    Create a new bug version object with the meta data about this activity
        Set id based on modification time
*       Set valid_from field as modification time
*       Set valid_to field as the modification time of the later version - 1 second
        Add modification data (who, when, what)
    For single value fields (i.e. assigned_to, status):
        Update the original state object by replacing the field value with the contents of the activities "removed" column
    For multi-value fields (i.e. blocks, CC, attachments):
        If a deletion, update the original state object by adding the value from the "removed" column to the field values array.
        If an addition, find and remove the added item from the original state object

When finished with all activities, the current state object should reflect the original state of the bug when created.
Now, build the full state of each intermediate version of the bug. 

For each bug version object that was created above:
    Merge the current state object into this version object
    Update fields according to the modification data

*/

var currBugID;
var prevBugID;
var bugVersions;
var bugVersionsMap;
var currBugState;
var currBugAttachments;
var currBugAttachmentsMap;
var prevActivityID;
var currActivity;
var inputRowSize = getInputRowMeta().size();
var outputRowSize = getOutputRowMeta().size();

function processRow(bug_id, modified_ts, modified_by, field_name, field_value, field_value_removed, attach_id, _merge_order) {
    currBugID = bug_id;

    // If we have switched to a new bug
    if (prevBugID < currBugID) {
        // Start replaying versions in ascending order to build full data on each version
        populateIntermediateVersionObjects();
        startNewBug(bug_id, modified_ts, modified_by, _merge_order);
    }


    if (currBugID < 999999999) {
        // Determine where we are in the bug processing workflow
        switch (_merge_order) {
            case 1:
                processSingleValueTableItem(field_name, field_value);
                break;
            case 2:
                processMultiValueTableItem(field_name, field_value);
                break;
            case 7:
                processAttachmentsTableItem(modified_ts, modified_by, field_name, field_value, field_value_removed, attach_id);
                break;
            case 8:
                processFlagsTableItem(modified_ts, modified_by, field_name, field_value, field_value_removed, attach_id);
                break;
            case 9:
                processBugsActivitiesTableItem(modified_ts, modified_by, field_name, field_value, field_value_removed, attach_id);
                break;
            default:
                break;
        }

        //return [currBugState,currActivity];
    }
}

function startNewBug(bug_id, modified_ts, modified_by, _merge_order) {
    if (currBugID >= 999999999) return;
    if (_merge_order != 1) {
        writeToLog("e", "Current bugs table record not found for bug_id: "+bug_id);
    }
    prevBugID = bug_id;
    bugVersions = [];
    bugVersionsMap = {};
    currBugState = {
        bug_id: bug_id,
        modified_ts: modified_ts,
        modified_by: modified_by,
        reported_by: modified_by,
        attachments: [],
        flags: []
    };
	currBugState._id = bug_id+"."+modified_ts;
    currActivity = {};
    currBugAttachments = [];
    currBugAttachmentsMap = {};
}

function processSingleValueTableItem(field_name, field_value) {
    currBugState[field_name] = field_value;
}

function processMultiValueTableItem(field_name, field_value) {
    //writeToLog("e", "About to push "+field_value+" to array field "+field_name+" on bug "+currBugID+" current value:"+JSON.stringify(currBugState[field_name]));
    if (currBugState[field_name] == null) {
        currBugState[field_name] = [];
    }
    try {
        currBugState[field_name].push(field_value);
    } catch(e) {
        writeToLog("e", "Unable to push "+field_value+" to array field "+field_name+" on bug "+currBugID+" current value:"+JSON.stringify(currBugState[field_name]));
    }
}

function processAttachmentsTableItem(modified_ts, modified_by, field_name, field_value, field_value_removed, attach_id) {
    currActivityID = currBugID+"."+modified_ts;
    if (currActivityID != prevActivityID) {
        currActivity = {
            _id: currActivityID,
            modified_ts: modified_ts,
            modified_by: modified_by,
            changes: []
        };
        bugVersions.push(currActivity);
        bugVersionsMap[currActivityID] = currActivity;
        prevActivityID = currActivityID;
    }
    currActivity.changes.push({
        field_name: field_name,
        field_value: field_value,
        attach_id: attach_id
    });
    if (!currBugAttachmentsMap[attach_id]) {
        currBugAttachmentsMap[attach_id] = {
            _id: attach_id+"."+modified_ts,
            attach_id: attach_id,
            modified_ts: modified_ts,
            modified_by: modified_by,
            flags: []
        };
        currBugAttachments.push(currBugAttachmentsMap[attach_id]);
    }
    currBugAttachmentsMap[attach_id][field_name] = field_value;
}

function processFlagsTableItem(modified_ts, modified_by, field_name, field_value, field_value_removed, attach_id) {
    var parts = splitFlag(field_value);
    var flag = {
        modified_ts: modified_ts,
        modified_by: modified_by,
        field_name: parts[0],
        field_value: parts[1]
    };
    if (attach_id != '') {
        if (!currBugAttachmentsMap[attach_id]) {
            writeToLog("e", "Unable to find attachment "+attach_id+" for bug_id "+currBugID);
        }
        currBugAttachmentsMap[attach_id].flags.push(flag);
    } else {
        currBugState.flags.push(flag);
    }
}

function processBugsActivitiesTableItem(modified_ts, modified_by, field_name, field_value, field_value_removed, attach_id) {
    if (field_name == "flagtypes.name") {
        field_name = "flags";
    }
    currActivityID = currBugID+"."+modified_ts;
    if (currActivityID != prevActivityID) {
        currActivity = bugVersionsMap[currActivityID];
        if (!currActivity) {
            currActivity = {
                _id: currActivityID,
                modified_ts: modified_ts,
                modified_by: modified_by,
                changes: []
            };
            bugVersions.push(currActivity);
        }
        prevActivityID = currActivityID;
    }
    if (field_name == 'flags') {
        currActivity.changes.push({
            field_name: field_name,
            field_value: field_value,
            field_value_removed: field_value_removed,
            attach_id: attach_id
        });
    } else {
        currActivity.changes.push({
            field_name: field_name,
            field_value: field_value,
            field_value_removed: field_value_removed,
            attach_id: attach_id
        });
    }
    if (attach_id != '') {
        var attachment = currBugAttachmentsMap[attach_id];
        if (!attachment) {
            writeToLog("e", "Unable to find attachment "+attach_id+" for bug_id "+currBugID+": "+JSON.stringify(currBugAttachmentsMap));
        }
        if (attachment[field_name] instanceof Array) {
            var a = attachment[field_name];
            if (field_value_removed == '') {
                var len = a.length;
                for (i = 0; i < a.length; i++) {
                    if (a[i] == field_value) {
                        a.splice(i,1);
                        break;
                    }
                }
                if (len == a.length) {
                    writeToLog("e", "Unable to find added value "+field_name+":"+field_value+" in attachment: "+JSON.stringify(attachment));
                }
            } else {
                a.push(field_value_removed);
            }
        } else {
            attachment[field_name] = field_value_removed;
        }
    } else {
        if (currBugState[field_name] instanceof Array) {
            var a = currBugState[field_name];
            if (field_value_removed == '') {
                var len = a.length;
                for (i = 0; i < a.length; i++) {
                    if (a[i] == field_value) {
                        a.splice(i,1);
                        break;
                    }
                }
                if (len == a.length) {
                    writeToLog("e", "Unable to find added value "+field_name+":"+field_value+" in currBugState: "+JSON.stringify(currBugState));
                }
            } else {
                a.push(field_value_removed);
            }
        } else {
            currBugState[field_name] = field_value_removed;
        }
    }
}

function populateIntermediateVersionObjects() {
    // Make sure the bugVersions are in order.  They could be mixed because of attachment activity
    bugVersions.sort(function(a,b){return (a.modified_ts>b.modified_ts?-1:(a.modified_ts<b.modified_ts?1:0));});

    var currVersion;

    // Prime the while loop with an empty next version so our first iteration outputs the initial bug state
    var nextVersion = {_id:currBugState._id,changes:[]};

    // Add an empty item to the bugVersions so we execute the loop once for every actual version
    //bugVersions.push({_id:"pad2",changes:[]});

    while (bugVersions.length > 0) {
        currVersion = nextVersion;
        nextVersion = bugVersions.pop();

        writeToLog("d", "Populating JSON for version "+currVersion._id);

        // Link this version to the next one
        currBugState.expires_on = nextVersion.modified_ts;

        // Copy all attributes from the current version into currBugState
        for (var propName in currVersion) {
            currBugState[propName] = currVersion[propName];
        }

        while (currBugAttachments[0] && currBugAttachments[0].creation_ts <= currBugState.modified_ts) {
            writeToLog("d", "Adding attachment into version "+currBugState.modified_ts+": "+JSON.stringify(currBugAttachments[0]));
            currBugState.attachments.push(currBugAttachments.shift());
        }

        // Now walk currBugState forward in time by applying the changes from currVersion
        var changes = currVersion.changes;
        for (var changeIdx = 0; changeIdx < changes.length; changeIdx++) {
            // Special logic for multivalue fields
            if (currBugState[changes[changeIdx].field_name] instanceof Array) {
                var a = currBugState[changes[changeIdx].field_name];

                // This was a deletion, find and delete the value
                if (changes[changeIdx].field_value_removed != '') {
                    var len = a.length;
                    for (i = 0; i < a.length; i++) {
                        if (a[i] == changes[changeIdx].field_value_removed) {
                            a.splice(i,1);
                            break;
                        }
                    }
                    if (len == a.length) {
                        writeToLog("e", "Unable to find removed value "+JSON.stringify(changes[changeIdx])+" from activity: "+JSON.stringify(currVersion));
                    }
                }

                // Handle addition (if any)
                if (changes[changeIdx].field_value != '') {
                    if (changes[changeIdx].field_name == 'flags') {
            field_value_obj: {"modified_ts": modified_ts, "modified_by": modified_by, "field_name": field_value},
                        a.push({
                            "modified_ts": currentVersion.modified_ts, 
                            "modified_by": currentVersion.modified_by, 
                            "field_name": changes[changeIdx].field_value
                        });
                    } else {
                        a.push(changes[changeIdx].field_value);
                    }
                }
            } else {
                // Simple field change.
                currBugState[changes[changeIdx].field_name] = changes[changeIdx].field_value;
            }
        }

        // Emit this version as a JSON string
        var newRow = createRowCopy(outputRowSize);
        var rowIndex = inputRowSize;
        newRow[rowIndex++] = currBugState.bug_id;
        newRow[rowIndex++] = currBugState._id;
        newRow[rowIndex++] = JSON.stringify(currBugState,null,2);
        putRow(newRow);
    }
}

function splitFlag(flag) {
    var parts = flag.split('(');
    if (parts.length == 2) {
        parts[1].slice(0,-1);
    }
    return parts;
}
