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

    writeToLog("d", "bug_id={" + bug_id + "}, modified_ts={" + modified_ts + "}, modified_by={" + modified_by 
          + "}, field_name={" + field_name + "}, field_value={" + field_value + "}, field_value_removed={"
          + field_value_removed + "}, attach_id={" + attach_id + "}, _merge_order={" + _merge_order + "}");

    // If we have switched to a new bug
    if (prevBugID < currBugID) {
        // Start replaying versions in ascending order to build full data on each version
        writeToLog("d", "Emitting intermediate versions for " + prevBugID);
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
        writeToLog("e", "Current bugs table record not found for bug_id: "+bug_id+" (merge order " + _merge_order + ")");
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
    //writeToLog("e", "About to push "+field_value+" to array field "+field_name+" on bug "
    //    +currBugID+" current value:"+JSON.stringify(currBugState[field_name]));
    if (currBugState[field_name] == null) {
        currBugState[field_name] = [];
    }
    try {
        currBugState[field_name].push(field_value);
    } catch(e) {
        writeToLog("e", "Unable to push "+field_value+" to array field "+field_name+" on bug "
              +currBugID+" current value:"+JSON.stringify(currBugState[field_name]));
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
//            _id: attach_id+"."+modified_ts, // not needed anymore
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
//    var parts = splitFlag(field_value);
    var flag = {
        modified_ts: modified_ts,
        modified_by: modified_by,
        field_name: field_value
//        field_value: parts[1]
    };
    if (attach_id != '') {
        if (!currBugAttachmentsMap[attach_id]) {
            writeToLog("d", "Unable to find attachment "+attach_id+" for bug_id "+currBugID);
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

    var multi_field_value = getMultiFieldValue(field_name, field_value);
    var multi_field_value_removed = getMultiFieldValue(field_name, field_value_removed);

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
    currActivity.changes.push({
        field_name: field_name,
        field_value: field_value,
        field_value_removed: field_value_removed,
        attach_id: attach_id
    });
    if (attach_id != '') {
        var attachment = currBugAttachmentsMap[attach_id];
        if (!attachment) {
            writeToLog("e", "Unable to find attachment "+attach_id+" for bug_id "+currBugID+": "+JSON.stringify(currBugAttachmentsMap));
        } else {
           if (attachment[field_name] instanceof Array) {
               var a = attachment[field_name];
               // Can have both added and removed values.
               if (multi_field_value[0] != '') {
                   // Theory: If we fail to remove an added value, that means the value was added, then later removed.
                   removeValues(a, multi_field_value, "added", field_name, "attachment", attachment);
               }

               if (multi_field_value_removed[0] != '') {
                   a = a.concat(multi_field_value_removed);
               }
           } else {
               writeToLog("d", "Setting attachment " + attach_id + "[" + field_name + "] to '" + field_value_removed + "'");
               attachment[field_name] = field_value_removed;
           }
        }
    } else {
        if (currBugState[field_name] instanceof Array) {
            var a = currBugState[field_name];
            // Can have both added and removed values.
            if (multi_field_value[0] != '') {
                // Theory: If we fail to remove an added value, that means the value was added, then later removed.
                removeValues(a, multi_field_value, "added", field_name, "currBugState", currBugState);
            }

            if (multi_field_value_removed[0] != '') {
                a = a.concat(multi_field_value_removed);
            }
        } else if (isMultiField(field_name)) {
            // field must currently be missing, otherwise it would
            // be an instanceof Array above.  This handles multi-valued
            // fields that are not first processed by processMultiValueTableItem().
            currBugState[field_name] = multi_field_value_removed;
        } else {
            // Replace current value
            currBugState[field_name] = field_value_removed;
        }
    }
}

function sortAscByField(a, b, aField) {
    if (a[aField] > b[aField])
        return 1;
    if (a[aField] < b[aField])
        return -1;
    return 0;
}
function sortDescByField(a, b, aField) {
    return -1 * sortAscByField(a, b, aField);
}

function populateIntermediateVersionObjects() {
    // Make sure the bugVersions are in descending order by modification time.
    // They could be mixed because of attachment activity
    bugVersions.sort(function(a,b){return sortDescByField(a, b, "modified_ts")});

    // Tracks the previous distinct value for each field
    var prevValues = {};

    var currVersion;
    // Prime the while loop with an empty next version so our first iteration outputs the initial bug state
    var nextVersion = {_id:currBugState._id,changes:[]};

    var flagMap = {};

    while (bugVersions.length > 0) {
        currVersion = nextVersion;
        nextVersion = bugVersions.pop(); // Oldest version
        writeToLog("d", "Populating JSON for version "+currVersion._id);

        // Link this version to the next one
        currBugState.expires_on = nextVersion.modified_ts;

        // Copy all attributes from the current version into currBugState
        for (var propName in currVersion) {
            currBugState[propName] = currVersion[propName];
        }

        // Attachments are already sorted.  No need to sort again.
        while (currBugAttachments[0] && currBugAttachments[0].created_ts <= currBugState.modified_ts) {
            currBugState.attachments.push(currBugAttachments.shift());
        }

        // Now walk currBugState forward in time by applying the changes from currVersion
        var changes = currVersion.changes;
        //writeToLog("d", "Processing changes: "+JSON.stringify(changes));
        for (var changeIdx = 0; changeIdx < changes.length; changeIdx++) {
            var change = changes[changeIdx];
            var target = currBugState;
            var targetName = "currBugState";
            if (change["attach_id"] != '') {
               // Attachment change
               target = findAttachment(currBugState["attachments"], change["attach_id"]);
               targetName = "attachment";
               if (target == null) {
                  writeToLog("e", "Encountered a change to missing attachment for bug '" + currVersion["bug_id"] + "': " + JSON.stringify(change) + ".");

                  // treat it as a change to the main bug instead :(
                  target = currBugState;
                  targetName = "currBugState";
               }
            }

            // Track the previous value
            // for now, skip attachment changes and multi-value fields
            if (targetName != "attachment" && !isMultiField(change.field_name)) {
               if (target[change.field_name] != change.field_value) {
                  setPrevious(prevValues, change.field_name, target[change.field_name], currVersion.modified_ts, nextVersion.modified_ts);
               }
            } else if (targetName == "attachment") {
               if (change.field_name == "flags") {
                  // Handle attachment flags
                  processFlagChange(flagMap, change, change["attach_id"], currVersion.modified_ts);
               } else {
                  // Handle attachments
                  if (!prevValues["attachments"]) {
                     prevValues["attachments"] = [];
                  }
                  var att = findAttachment(prevValues["attachments"], change["attach_id"]);
                  if (!att) {
                     //writeToLog("d", "Unable to find attachment with id '" + attachId + "' in " + JSON.stringify(attachments) + ".");
                     att = { attach_id: change["attach_id"] };
                     prevValues["attachments"].push(att);
                  }

                  // Make sure it's actually changing.  We seem to get change entries for attachments that show the current field value.
                  if (target[change.field_name] != change.field_value) {
                     setPrevious(att, change.field_name, target[change.field_name], currVersion.modified_ts, nextVersion.modified_ts);
                  }
               }
            } else if (change.field_name == "flags") {
               // Handle bug flags (but not the other simple multi-fields)
               processFlagChange(flagMap, change, "bug", currVersion.modified_ts);
            } else {
               writeToLog("d", "Skipping previous_value for multi-value field " + change.field_name);
            }

            // Multi-value fields
            if (target[change.field_name] instanceof Array) {
                var a = target[change.field_name];
                var multi_field_value = getMultiFieldValue(change.field_name, change.field_value);
                var multi_field_value_removed = getMultiFieldValue(change.field_name, change.field_value_removed);

                // This was a deletion, find and delete the value(s)
                if (multi_field_value_removed[0] != '') {
                   removeValues(a, multi_field_value_removed, "removed", change.field_name, targetName, target);
                }

                // Handle addition(s) (if any)
                for each (var added in multi_field_value) {
                    if (added != '') {
                        if (change.field_name == 'flags') {
                           var addedFlag = {
                                   "modified_ts": currVersion.modified_ts, 
                                   "modified_by": currVersion.modified_by, 
                                   "field_name": added
                               };
                           a.push(addedFlag);
                        } else {
                           a.push(added);
                        }
                    }
                }
            } else if (isMultiField(change.field_name)) {
                // First appearance of a multi-value field
                target[change.field_name] = [change.field_value];
            } else {
                // Simple field change.
                target[change.field_name] = change.field_value;
            }
        }

        currBugState.previous_values = prevValues;

        // TODO: if we remove the completed flags from flagMap as we apply them
        //       we don't have to reset with each bug version.
        // unset flags
        //currBugState.previous_values["flags"] = undefined;

        // Apply the previous value flags
        applyFlags(currBugState, flagMap);

        // Do some processing to make sure that diffing betweens runs stays as similar as possible.
        stabilize(currBugState);

        // Empty string breaks date parsing, remove it from bug state.
        if (currBugState["deadline"] == "") {
           //currBugState["deadline"] = null;
           currBugState["deadline"] = undefined;
        }

        // Emit this version as a JSON string
        var newRow = createRowCopy(outputRowSize);
        var rowIndex = inputRowSize;
        newRow[rowIndex++] = currBugState.bug_id;
        newRow[rowIndex++] = currBugState._id;
        newRow[rowIndex++] = JSON.stringify(currBugState,null,2); // DEBUGGING, expanded output
        //newRow[rowIndex++] = JSON.stringify(currBugState);
        putRow(newRow);
    }
}

// aFlagLabel is either "bug" if it's a bug flag, or an attachment id if it's 
// an attachment flag.
function processFlagChange(aFlagMap, aChange, aFlagLabel, aTimestamp) {
   if (!aFlagMap[aFlagLabel]) {
      aFlagMap[aFlagLabel] = [];
   }

   processFlags(aFlagMap[aFlagLabel], aChange.field_value, "change_to_ts", aTimestamp);
   processFlags(aFlagMap[aFlagLabel], aChange.field_value_removed, "change_away_ts", aTimestamp);
}

function processFlags(aFlagList, aFieldValue, aTsField, aTimestamp) {
   var values = getMultiFieldValue("flags", aFieldValue);
   for each (var value in values) {
      if (value != '') {
         var existingFlag = findByKey(aFlagList, "flag", value);
         if (!existingFlag) {
            existingFlag = {
               flag: value,
            };
            aFlagList.push(existingFlag);
         }

         existingFlag[aTsField] = aTimestamp;

         if (existingFlag["change_to_ts"] && existingFlag["change_away_ts"]) {
            var duration_ms = existingFlag["change_away_ts"] - existingFlag["change_to_ts"];
            existingFlag["duration_seconds"] = (duration_ms / 1000);
            existingFlag["duration_days"] = (duration_ms / (1000.0 * 60 * 60 * 24));
         }
      }
   }
}

// TODO: if we remove the "used" flags as we go, we do not have to reset the 
//       flags between each bug version.
function applyFlags(aBug, aFlagMap) {
   // Apply bug flags
   applyOneFlagSet(aBug["previous_values"], aFlagMap["bug"]);

   // Apply attachment flags
   // Only grab attachments that are in the bug.
   // FIXME: should we iterate aBug["previous_values"]["attachments"] or aBug["attachments"]?
   for each (var attachment in aBug["previous_values"]["attachments"]) {
      applyOneFlagSet(attachment, aFlagMap[attachment["attach_id"]]);
   }
}

function applyOneFlagSet(aPrevValues, aFlagSet) {
   if (aFlagSet) {
      for (var i = 0; i < aFlagSet.length; i++) {
         var flag = aFlagSet[i];
         if (flag["change_to_ts"] && flag["change_away_ts"]) {
            // it's a flag with a full previous value
            if (aPrevValues["flags"]) {
               aPrevValues["flags"].push(flag);
            } else {
               aPrevValues["flags"] = [flag];
            }

            // Remove this flag:
            writeToLog("d", "Removing used up flag: " + JSON.stringify(flag));
            aFlagSet[i] = {};
         } else if (flag["change_away_ts"]) {
            writeToLog("e", "Found a previous flag with only change_away_ts: " + JSON.stringify(flag));
         } else {
            writeToLog("d", "Skipping incomplete previous flag: " + JSON.stringify(flag));
         }
      }
   }
}

function setPrevious(dest, aFieldName, aValue, aChangeTo, aChangeAway) {
    var duration_ms = (aChangeAway - aChangeTo);
    dest[aFieldName + "_value"] = aValue;
    //dest[aFieldName + "_change_to_ts"] = new Date(aChangeTo); // For debugging;
    //dest[aFieldName + "_change_away_ts"] = new Date(aChangeAway); // for debugging;
    dest[aFieldName + "_change_to_ts"] = aChangeTo;
    dest[aFieldName + "_change_away_ts"] = aChangeAway;
    dest[aFieldName + "_duration_seconds"] = (duration_ms / 1000);
    dest[aFieldName + "_duration_days"] = (duration_ms / (1000.0 * 60 * 60 * 24));
}

function findAttachment(attachments, attachId) {
   return findByKey(attachments, "attach_id", attachId);
}

function findByKey(aList, aField, aValue) {
   for each (var item in aList) {
      if (item[aField] == aValue) {
         return item;
      }
   }
   return null;
}


function stabilize(aBug) {
   if (aBug["cc"] && aBug["cc"][0]) {
      aBug["cc"].sort();
   }
   if (aBug["changes"]) {
      aBug["changes"].sort(function(a,b){ return sortAscByField(a, b, "field_name") });
   }
}

// XXX - do we need this?
function splitFlag(flag) {
    var parts = flag.split('(');
    if (parts.length == 2) {
        parts[1] = parts[1].slice(0,-1);
    }
    return parts;
}

function removeValues(anArray, someValues, valueType, fieldName, arrayDesc, anObj) {
    if (fieldName == "flags") {
        for each (var v in someValues) {
            var len = anArray.length;
            for (var i = 0; i < len; i++) {
                // Match on flag name (incl. status) and flag value
                if (anArray[i].field_name == v) {
                     anArray.splice(i, 1);
                     break;
                }
            }

            if (len == anArray.length) {
                writeToLog("d", "Unable to find " + valueType + " flag " + fieldName + ":" + v
                        + " in " + arrayDesc + ": " + JSON.stringify(anObj));
            }
        }
    } else {
        for each (var v in someValues) {
            var foundAt = anArray.indexOf(v);
            if (foundAt >= 0) {
                anArray.splice(foundAt, 1);
            } else {
                // XXX if this is a "? 12345" type value for "dependson" etc, try looking for
                //     the value with the leading "? " trimmed off.
                writeToLog("d", "Unable to find " + valueType + " value " + fieldName + ":" + v
                        + " in " + arrayDesc + ": " + JSON.stringify(anObj));
            }
        }
    }
}

function isMultiField(aFieldName) {
    return (aFieldName == "flags" || aFieldName == "cc" || aFieldName == "keywords"
     || aFieldName == "dependson" || aFieldName == "blocked" || aFieldName == "dupe_by"
     || aFieldName == "dupe_of" || aFieldName == "bug_group");
}

function getMultiFieldValue(aFieldName, aFieldValue) {
    if (isMultiField(aFieldName)) {
        return aFieldValue.split(/\s*,\s*/);
    }

    return [aFieldValue];
}
