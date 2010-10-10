/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is mozilla.org code.
 *
 * The Initial Developer of the Original Code is
 * Mozilla Corporation.
 * Portions created by the Initial Developer are Copyright (C) 2010
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *   Michael Kurze (michael@thefoundation.de)
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK *****
 */

package com.mozilla.bugzilla_etl.old;                                                        // snip
                                                                                             // snip
import org.pentaho.di.trans.Trans;                                                           // snip
import org.pentaho.di.trans.TransMeta;                                                       // snip
import org.pentaho.di.trans.step.StepDataInterface;                                          // snip
import org.pentaho.di.trans.step.StepMeta;                                                   // snip
import org.pentaho.di.trans.step.StepMetaInterface;                                          // snip
import org.pentaho.di.trans.steps.userdefinedjavaclass.*;                                    // snip
import org.pentaho.di.core.exception.*;                                                      // snip
import org.pentaho.di.core.row.*;                                                            // snip

import java.util.concurrent.atomic.AtomicIntegerArray;
import org.pentaho.di.core.*;
import org.pentaho.di.core.variables.VariableSpace;
import org.pentaho.di.trans.steps.userdefinedjavaclass.UserDefinedJavaClassMeta.FieldInfo;
import java.util.*;
import org.apache.commons.lang.time.*;

@SuppressWarnings({"rawtypes", "unchecked", "unused"})                                       // snip
public class RebuildHistoryStep extends TransformClassBase {                                 // snip
                                                                                             // snip
    public RebuildHistoryStep(UserDefinedJavaClass parent,                                   // snip
                              UserDefinedJavaClassMeta meta,                                 // snip
                              UserDefinedJavaClassData data) throws KettleStepException {    // snip
        super(parent, meta, data);                                                           // snip
    }                                                                                        // step

    private static final TimeZone pacificTimeZone = TimeZone.getTimeZone("America/Los_Angeles");

    private HashMap statusStates = new HashMap();

    private static final AtomicIntegerArray counters = new AtomicIntegerArray(11);

    private static class Fields {
        public static class In  {
            public static FieldHelper bug_id, activity_bug_id, bug_state_tk, previous_valid_to_ts, bug_when, creation_ts, valid_from_ts, valid_to_ts, row_number;
            public static HashMap hashFields = new HashMap();
            private static FieldHelper get(String name) throws KettleValueException {
                FieldHelper fieldHelper = (FieldHelper)hashFields.get(name);
                if (fieldHelper == null) {
                    throw new KettleValueException("FieldHelper not initialized for: "+name);
                }
                return fieldHelper;
            }
            public static String getStringValue(String name, Object[] r) throws KettleValueException {
                return get(name).getString(r);
            }
            public static Object getObjectValue(String name, Object[] r) throws KettleValueException {
                return get(name).getObject(r);
            }
            public static Long getLongValue(String name, Object[] r) throws KettleValueException {
                return get(name).getInteger(r);
            }
            public static void setValue(String name, Object[] r, Object value) throws KettleValueException {
                get(name).setValue(r, value);
            }
        }

        public static class Out {
            public static FieldHelper valid_from_ts, valid_to_ts, is_latest_state;
            public static HashMap hashFields = new HashMap();
            public static void setValue(String name, Object[] r, Object value) throws KettleValueException {
                FieldHelper fieldHelper = (FieldHelper)hashFields.get(name);
                if (fieldHelper == null) {
                    throw new KettleValueException("FieldHelper not initialized for: "+name);
                }
                fieldHelper.setValue(r, value);
            }
        }
    }

    private static final String[] prodFields = new String[] {
        "product",
        "component",
        "version",
    };

    private static final String[] statusModifiedFields = new String[] {
        "status_modified_by",
        "status_modified_on",
        "status_modified_at",
        "status_modified_by_team_product",
        "status_modified_by_team_component",
        "status_modified_by_team_version",
    };

    private static final String[] modifiedTimeFields = new String[] {
        "modified_by",
        "modified_on",
        "modified_at",
    };
    private static final String[] modifiedTeamFields = new String[] {
        "modified_by_team_product",
        "modified_by_team_component",
        "modified_by_team_version",
    };

    private static final String[] baseFields = new String[] {
        "assigned_to",
        "severity",
        "priority",
        "opsys",
        "status_whiteboard",
        "status",
        "resolution",
        "product",
        "component",
        "version",
    };

    private static final String[] newBugReporterFields = new String[] {
        "curr_modified_by",
        "curr_status_modified_by",
        "curr_major_status_modified_by",
    };

    private static final String[] newBugProductFieldPrefixes = new String[] {
        "reported_by_team_",
        "curr_modified_by_team_",
        "curr_assigned_to_team_",
        "curr_status_modified_by_team_",
        "curr_major_status_modified_by_team_",
    };

    private static final String[] newBugCreatedFieldPrefixes = new String[] {
        "curr_modified_",
        "curr_status_modified_",
        "curr_major_status_modified_",
    };

    private static final String[] newBugStringDefaultFields = new String[] {
        "prev_product",
        "prev_component",
        "prev_version",
        "prev_severity",
        "prev_priority",
        "prev_major_status",
        "prev_opsys",
        "prev_status_whiteboard",
        "prev_status",
        "prev_resolution",
        "prev_assigned_to",
        "prev_assigned_to_team_product",
        "prev_assigned_to_team_component",
        "prev_assigned_to_team_version",
        "prev_status_modified_by_team_product",
        "prev_status_modified_by_team_component",
        "prev_status_modified_by_team_version",
        "prev_major_status_modified_by_team_product",
        "prev_major_status_modified_by_team_component",
        "prev_major_status_modified_by_team_version",
        "prev_modified_by_team_product",
        "prev_modified_by_team_component",
        "prev_modified_by_team_version",
    };
    private static final String[] newBugPrevOneDefaultFields = new String[] {
        "prev_modified_by",
        "prev_status_modified_by",
        "prev_major_status_modified_by",
    };
    private static final String[] newBugPrevZeroDefaultFields = new String[] {
        "prev_status_modified_on",
        "prev_status_modified_at",
        "prev_major_status_modified_on",
        "prev_major_status_modified_at",
        "prev_modified_on",
        "prev_modified_at",
        "is_reopened",
        "days_since_created",
        "days_since_last_modified",
        "days_in_current_status",
        "days_in_previous_status",
        "days_in_current_major_status",
        "days_in_previous_major_status",
    };

    private static final Long zero = new Long(0L);
    private static final Long one = new Long(1L);

    private static final String[] parseFormats = new String[] { "yyyyMMdd", "yyyy-MM-dd hh:mm:ss" };

    private static Date futureInitializer;
    private static final Date future;
    static {
        try { futureInitializer = DateUtils.parseDate("2199-12-31 23:59:59", parseFormats);
        } catch(Exception e) { e.printStackTrace(); futureInitializer = null; }
        future = futureInitializer;
        futureInitializer = null;
    }

    private void printLongCounters() {
        System.err.format("%n%-20s:%,10d%n%-20s:%,10d%n%-20s:%,10d%n%-20s:%,10d%n%-20s:%,10d%n%-20s:%,10d%n%-20s:%,10d%n%-20s:%,10d%n%-20s:%,10d%n%-20s:%,10d%n%-20s:%,10d%n",
            new Object[] {
                "New bugs", counters.get(0),
                "  with 0 activity", counters.get(1),
                "  with 1-5 activity", counters.get(2),
                "  with 6-10 activity", counters.get(3),
                "  with 10+ activity", counters.get(4),
                "Old bugs", counters.get(5),
                "  with 0 activity", counters.get(6),
                "  with 1-5 activity", counters.get(7),
                "  with 6-10 activity", counters.get(8),
                "  with 10+ activity", counters.get(9),
                "Old bug state error", counters.get(10),
            });
    }

    private Long currentBugID = null;
    private boolean isNewBug = false;
    private boolean hasOutputEarliestRecord = false;
    private int numBugs = 0;
    private int outputRowSize;
    private Object[] previousRow = null;
    private Object[] workingState = null;

    private RowSet main;
    
    @Override
    public boolean processRow(StepMetaInterface smi, StepDataInterface sdi) throws KettleException
    {
        if (first)
        {
            first = false;
            main = findInputRowSet("Join bugs/curr_bugs");
            previousRow = getRowFrom(main);
            if (previousRow == null) {
                setOutputDone();
                printLongCounters();
                return false;
            }

            data.inputRowMeta = main.getRowMeta();
            data.outputRowMeta = new RowMeta();
            meta.getFields(data.outputRowMeta, getStepname(), null, null, parent);
            outputRowSize = data.outputRowMeta.size();
            workingState = new Object[previousRow == null ? 0 : previousRow.length + 10];

            Fields.In.bug_id = new FieldHelper(data.inputRowMeta, "bug_id");
            Fields.In.activity_bug_id = new FieldHelper(data.inputRowMeta, "activity_bug_id");
            Fields.In.bug_state_tk = new FieldHelper(data.inputRowMeta, "bug_state_tk");
            Fields.In.previous_valid_to_ts = new FieldHelper(data.inputRowMeta, "previous_valid_to_ts");
            Fields.In.bug_when = new FieldHelper(data.inputRowMeta, "bug_when");
            Fields.In.creation_ts = new FieldHelper(data.inputRowMeta, "creation_ts");
            Fields.In.valid_from_ts = new FieldHelper(data.inputRowMeta, "valid_from_ts");
            Fields.In.valid_to_ts = new FieldHelper(data.inputRowMeta, "valid_to_ts");
            Fields.In.row_number = new FieldHelper(data.inputRowMeta, "row_number");
            Fields.Out.valid_from_ts = new FieldHelper(data.outputRowMeta, "valid_from_ts");
            Fields.Out.valid_to_ts = new FieldHelper(data.outputRowMeta, "valid_to_ts");
            Fields.Out.is_latest_state = new FieldHelper(data.outputRowMeta, "is_latest_state");

            String[] allInFields = data.inputRowMeta.getFieldNames();
            for (int i = 0; i < allInFields.length; i++) {
                String fieldName = allInFields[i];
                FieldHelper fieldHelper = new FieldHelper(data.inputRowMeta, fieldName);
                Fields.In.hashFields.put(fieldName, fieldHelper);
            }
            String[] allOutFields = data.outputRowMeta.getFieldNames();
            for (int i = 0; i < allOutFields.length; i++) {
                String fieldName = allOutFields[i];
                FieldHelper fieldHelper = new FieldHelper(data.outputRowMeta, fieldName);
                Fields.Out.hashFields.put(fieldName, fieldHelper);
            }

            RowSet lookup = findInputRowSet("Get Status States");
            Object[] lookupData = getRowFrom(lookup);
            RowMetaInterface lookupMeta = lookup.getRowMeta();
            FieldHelper status_name = new FieldHelper(lookupMeta, "status_name");
            FieldHelper status_isopen = new FieldHelper(lookupMeta, "status_isopen");
            for (; lookupData != null; lookupData = getRowFrom(lookup))
            {
                incrementLinesInput();
                statusStates.put(status_name.getString(lookupData), status_isopen.getInteger(lookupData));
            }
        }

        if (previousRow == null) {
            setOutputDone();
            printLongCounters();
            return false;
        }

        if (numBugs++ % 100 == 0) {
            System.err.print("\r");
            System.err.print(counters.toString());
            System.err.print("\r");
        }

        incrementLinesOutput();
        hasOutputEarliestRecord = false;

        currentBugID = Fields.In.bug_id.getInteger(previousRow);
        if (currentBugID == null) {
            debugPrintRow(0L, "(activity_)?bug_id|bug_state_tk|bug_when|is_(latest|earliest)_state", data.inputRowMeta, previousRow);
            throw new KettleStepException("bug_id for first bug state was null.");
        }

        Long bugStateTK = Fields.In.bug_state_tk.getInteger(previousRow);
        Long thisRowBugID = Fields.In.activity_bug_id.getInteger(previousRow);

        if (bugStateTK != null && thisRowBugID == null) {
            // This is a bug with no activity that we have already recorded. Skip it.
            counters.getAndIncrement(6);
            previousRow = getRowFrom(main);
            return true;
        }

        final LinkedList bugStates = new LinkedList();

        bugStates.add(previousRow);

        boolean finishedWithBug = false;
        while (!finishedWithBug) {
            previousRow = getRowFrom(main);

            if (previousRow != null) {
                thisRowBugID = Fields.In.activity_bug_id.getInteger(previousRow);
                if (thisRowBugID == null) {
                    // Might be a new bug with no activity, fall back to bug_id
                    thisRowBugID = Fields.In.bug_id.getInteger(previousRow);
                }

                if (currentBugID.equals(thisRowBugID)) {
                    bugStates.add(previousRow);
                    bugStateTK = Fields.In.bug_state_tk.getInteger(previousRow);
                } else {
                    finishedWithBug = true;
                }
            } else {
                finishedWithBug = true;
            }

            if (finishedWithBug) {
                Date latestActivityTime = Fields.In.bug_when.getDate((Object[])bugStates.getFirst());
                Date latestStateTime = Fields.In.valid_from_ts.getDate((Object[])bugStates.getLast());
                // If this isn't an existing bug, or if there is new activity, process it
                if (latestStateTime == null || latestActivityTime == null || (latestStateTime.compareTo(latestActivityTime) < 0)) {
                    isNewBug = bugStateTK == null;
                    if (isNewBug) {
                        counters.getAndIncrement(0); // new bug
                        rebuildNewBugCurrValues(bugStates.listIterator(0));
                    } else {
                        counters.getAndIncrement(5); // old bug
                        rebuildBugPrevValues(bugStates.listIterator(bugStates.size()));
                    }
                } else { // No new activity
                    counters.getAndIncrement(6);
                }
                return true;
            }
        }
        return false;
    }

    private void rebuildNewBugCurrValues(final ListIterator bugStatesIter) throws KettleException
    {
        Object[] r = (Object[])bugStatesIter.next();

        initializeWorkingState(r);

        // No need to do this state rebuild if there is only one bug state.
        if (Fields.In.activity_bug_id.getInteger(r) != null) {

            int numActivities = 0;
            // Iterate latest state to earliest state, rebuilding the current values in _to
            while (r != null) {
                numActivities++;
                for (int i = 0; i < baseFields.length; i++) {
                    String fieldName = baseFields[i];

                    // For each tracked field, if there is no activity in this state, copy the current value.
                    String fromValue = Fields.In.getStringValue(fieldName+"_from", r);
                    String toValue = Fields.In.getStringValue(fieldName+"_to", r);
                    if (fromValue != null || toValue != null) { // There was activity for this field.  Track _from as the next current value.
                        Fields.In.setValue(fieldName, workingState, fromValue);
                    }
                }
                //debugPrintRow(71L, "component", data.inputRowMeta, workingState);
                if (bugStatesIter.hasNext()) {
                    r = (Object[])bugStatesIter.next();
                } else {
                    r = null;
                }
            }

            rebuildBugPrevValues(bugStatesIter);

            if (numActivities <= 5) counters.getAndIncrement(2); // Bug with 1 to 5 activity
            else if (numActivities <= 10) counters.getAndIncrement(3); // bug with 6 to 10 activity
            else counters.getAndIncrement(4); // bug with more than 10 activity
        } else {
            counters.getAndIncrement(1); // bug with no activity
            populatePreviousValues(workingState);
            outputRow(workingState, false);
        }

    }

    private void rebuildBugPrevValues(final ListIterator bugStatesIter) throws KettleException
    {
        int numActivities = 0;
        Object[] thisRow = (Object[])bugStatesIter.previous();
        Object[] nextRow = null;

        while (!populatePreviousValues(thisRow)) {
            if (bugStatesIter.hasPrevious()) {
                thisRow = (Object[])bugStatesIter.previous();
            } else {
                thisRow = null;
                break;
            }
        }

        //debugPrintRow(104L, null, data.inputRowMeta, workingState);
        if (isNewBug) {
            outputRow(workingState, false);
        } else if (thisRow == null || Fields.In.activity_bug_id.getInteger(thisRow) == null) {
            // Special case if we have a bug_nk but no activity_bug_id.  Something in the bug was modified that we don't track.
            // Don't record a new record for now.
            thisRow = null;
        }


        // Now, we will walk back up the iterator, rebuilding the previous values in _from
        while (thisRow != null) {
            //debugPrintRow(38L, "status_(from|to)", data.inputRowMeta, thisRow);
            numActivities++;

            // Set up the workingState with needed fields from this row.
            Fields.In.row_number.setValue(workingState, Fields.In.row_number.getInteger(thisRow));

            Date thisValidFrom = Fields.In.bug_when.getDate(thisRow);
            Fields.In.valid_from_ts.setValue(workingState, thisValidFrom);
            if (bugStatesIter.hasPrevious()) {
                nextRow = (Object[])bugStatesIter.previous();

                Date nextValidFrom = Fields.In.bug_when.getDate(nextRow);
                Date thisValidTo;
                int timeDiff = thisValidFrom.compareTo(nextValidFrom);
                if (timeDiff < 0) { // Normal case this is before next.
                    thisValidTo = DateUtils.addSeconds(nextValidFrom, -1);
                } else if (timeDiff == 0) {
                    // This is a special case that seems to be where someone hacked up a SQL script to adjust some product names.
                    // We need to fudge the dates since the current DWH can't handle activities with the same timestamp.
                    System.err.println("Odd bug activity timeline found for bug "+currentBugID);
                    System.err.println("Two activities in same second.  Bumping second activity 100 ms.");
                    thisValidTo = (Date)(thisValidFrom.clone());
                    Fields.In.bug_when.setValue(nextRow, DateUtils.addMilliseconds(nextValidFrom, 100));
                } else { // I don't think I'll even see these unless it is the case where the first activity is after dst and the creation was before.
                    System.err.println("Odd bug activity timeline found for bug "+currentBugID);
                    Date tryDSTAdjust = DateUtils.addHours(nextValidFrom, 1);
                    if (!pacificTimeZone.inDaylightTime(thisValidFrom) && pacificTimeZone.inDaylightTime(tryDSTAdjust) && thisValidFrom.compareTo(tryDSTAdjust) < 0) {
                        System.err.println("Seems to be due to daylight saving time.  Adjusting next activity time forward one hour.");
                        thisValidTo = DateUtils.addSeconds(tryDSTAdjust, -1);
                        Fields.In.bug_when.setValue(nextRow, tryDSTAdjust);
                    } else {
                        System.err.println("Wasn't due to daylight saving time. Faking next activity time to next instant for timeline consistency.");
                        thisValidTo = (Date)(thisValidFrom.clone());
                        Fields.In.bug_when.setValue(nextRow, DateUtils.addMilliseconds(nextValidFrom, 100));
                    }
                }
                Fields.In.valid_to_ts.setValue(workingState, thisValidTo);
            } else {
                nextRow = null;
                Fields.In.valid_to_ts.setValue(workingState, future);
            }

            // Second, update the modification related fields in this bug state
            for (int i = 0; i < modifiedTimeFields.length; i++) {
                String fieldName = modifiedTimeFields[i];
                //FieldHelper fieldHelper = (FieldHelper)Fields.In.hashFields.get(fieldName);
                //System.err.println(fieldName+" ; "+fieldHelper+" ; "+(fieldHelper == null ? -1 : fieldHelper.indexOfValue())+" ; "+Arrays.toString(thisRow));
                Object fieldValue = Fields.In.getObjectValue(fieldName, thisRow);
                Fields.In.setValue("prev_"+fieldName, workingState, Fields.In.getObjectValue("curr_"+fieldName, workingState));
                Fields.In.setValue("curr_"+fieldName, workingState, fieldValue);
            }

            // Roll over the modified by product as well.
            // Even if the product changes in this activity, we'll be using the old product for the modified_by_team info.
            for (int i = 0; i < modifiedTeamFields.length; i++) {
                String fieldName = modifiedTeamFields[i];
                Fields.In.setValue("prev_"+fieldName, workingState, Fields.In.getStringValue("curr_"+fieldName, workingState));
            }

            boolean hasProdChange = false;
            boolean[] prodChanges = new boolean[3];
            boolean hasStatusChange = false;

            // Next, update the simple fields (these fields don't track a person team or time)
            for (int i = 0; i < baseFields.length; i++) {
                String fieldName = baseFields[i];

                String fromValue = Fields.In.getStringValue(fieldName+"_from", thisRow);
                String toValue = Fields.In.getStringValue(fieldName+"_to", thisRow);
                if (fromValue != null || toValue != null) { // There was a change, update workingState
                    if (fromValue == null) fromValue = "<none>";
                    if (toValue == null) toValue = "<none>";
                    Fields.In.setValue("prev_"+fieldName, workingState, fromValue);
                    Fields.In.setValue("curr_"+fieldName, workingState, toValue);

                    for (int j = 0; j < prodFields.length; j++) {
                        if (prodFields[j].equals(fieldName)) {
                            hasProdChange = true;
                            prodChanges[j] = true;
                        }
                    }
                    if ("status".equals(fieldName)) hasStatusChange = true;
                }
            }

            if (hasStatusChange) {
                String prevStatus = Fields.In.getStringValue("prev_status", workingState);
                Long prevStatusState = (Long)statusStates.get(prevStatus);
                String currStatus = Fields.In.getStringValue("curr_status", workingState);
                Long currStatusState = (Long)statusStates.get(currStatus);

                Long netOpen = Long.valueOf(currStatusState.longValue() - prevStatusState.longValue());

                Fields.In.setValue("is_open", workingState, currStatusState);
                Fields.In.setValue("is_closed", workingState, one.equals(currStatusState) ? zero : one);
                Fields.In.setValue("net_open", workingState, netOpen);

                // This is a major status change
                if (!zero.equals(netOpen)) {
                    Fields.In.setValue("is_reopened", workingState,
                        Long.valueOf((prevStatusState.longValue() + 1L) * currStatusState.longValue()));

                    // Rotate the current status_modification related fields to previous
                    for (int i = 0; i < statusModifiedFields.length; i++) {
                        String fieldName = statusModifiedFields[i];
                        Object fieldValue = Fields.In.getObjectValue("curr_major_"+fieldName, workingState);
                        Fields.In.setValue("prev_major_"+fieldName, workingState, fieldValue);
                    }
                    // Copy current modification related fields for status change
                    for (int i = 0; i < modifiedTimeFields.length; i++) {
                        String fieldName = modifiedTimeFields[i];
                        Object fieldValue = Fields.In.getObjectValue("curr_"+fieldName, workingState);
                        Fields.In.setValue("curr_major_status_"+fieldName, workingState, fieldValue);
                    }
                    for (int i = 0; i < modifiedTeamFields.length; i++) {
                        String fieldName = modifiedTeamFields[i];
                        Fields.In.setValue("curr_major_status_"+fieldName, workingState, Fields.In.getStringValue("curr_"+fieldName, workingState));
                    }

                    Fields.In.setValue("prev_major_status", workingState, Fields.In.getStringValue("curr_major_status", workingState));
                    Fields.In.setValue("curr_major_status", workingState, Fields.In.getStringValue("curr_status", workingState));
                } else {
                    Fields.In.setValue("is_reopened", workingState, zero);
                }

                // Rotate the current status_modification related fields to previous
                for (int i = 0; i < statusModifiedFields.length; i++) {
                    String fieldName = statusModifiedFields[i];
                    Object fieldValue = Fields.In.getObjectValue("curr_"+fieldName, workingState);
                    Fields.In.setValue("prev_"+fieldName, workingState, fieldValue);
                }
                // Copy current modification related fields for status change
                for (int i = 0; i < modifiedTimeFields.length; i++) {
                    String fieldName = modifiedTimeFields[i];
                    Object fieldValue = Fields.In.getObjectValue("curr_"+fieldName, workingState);
                    Fields.In.setValue("curr_status_"+fieldName, workingState, fieldValue);
                }
                for (int i = 0; i < modifiedTeamFields.length; i++) {
                    String fieldName = modifiedTeamFields[i];
                    Fields.In.setValue("curr_status_"+fieldName, workingState, Fields.In.getStringValue("curr_"+fieldName, workingState));
                }
            } else {
                Fields.In.setValue("net_open", workingState, zero);
                Fields.In.setValue("is_reopened", workingState, zero);
            }

            if (hasProdChange) {
                for (int i = 0; i < prodFields.length; i++) {
                    String fieldName = prodFields[i];
                    Fields.In.setValue("prev_assigned_to_team_"+fieldName, workingState, Fields.In.getStringValue("curr_assigned_to_team_"+fieldName, workingState));

                    String newFieldValue = Fields.In.getStringValue("curr_"+fieldName, workingState);

                    // If this isn't the field that changed, copy over the unchanged value to prev.
                    if (!prodChanges[i]) {
                        Fields.In.setValue("prev_"+fieldName, workingState, newFieldValue);
                    }
                    Fields.In.setValue("curr_assigned_to_team_"+fieldName, workingState, newFieldValue);
                    Fields.In.setValue("curr_modified_by_team_"+fieldName, workingState, newFieldValue);
                }
            }

            try {
                long createdOn = DateUtils.parseDate(String.valueOf(Fields.In.getLongValue("created_on", workingState)), parseFormats).getTime();
                long currModifiedOn = DateUtils.parseDate(String.valueOf(Fields.In.getLongValue("curr_modified_on", workingState)), parseFormats).getTime();
                long currStatusModifiedOn = DateUtils.parseDate(String.valueOf(Fields.In.getLongValue("curr_status_modified_on", workingState)), parseFormats).getTime();
                long currMajorStatusModifiedOn = DateUtils.parseDate(String.valueOf(Fields.In.getLongValue("curr_major_status_modified_on", workingState)), parseFormats).getTime();

                long prevModifiedOn = Fields.In.getLongValue("prev_modified_on", workingState);
                long prevModifiedOnMs = 0L == prevModifiedOn ? 0L : DateUtils.parseDate(String.valueOf(prevModifiedOn), parseFormats).getTime();
                long prevStatusModifiedOn = Fields.In.getLongValue("prev_status_modified_on", workingState);
                long prevStatusModifiedOnMs = 0L == prevStatusModifiedOn ? 0L : DateUtils.parseDate(String.valueOf(prevStatusModifiedOn), parseFormats).getTime();
                long prevMajorStatusModifiedOn = Fields.In.getLongValue("prev_major_status_modified_on", workingState);
                long prevMajorStatusModifiedOnMs = 0L == prevMajorStatusModifiedOn ? 0L : DateUtils.parseDate(String.valueOf(prevMajorStatusModifiedOn), parseFormats).getTime();

                Fields.In.setValue("days_since_created", workingState, Long.valueOf(DurationFormatUtils.formatPeriod(createdOn, currModifiedOn, "d")));
                Fields.In.setValue("days_in_current_status", workingState, Long.valueOf(DurationFormatUtils.formatPeriod(currStatusModifiedOn, currModifiedOn, "d")));
                Fields.In.setValue("days_in_current_major_status", workingState, Long.valueOf(DurationFormatUtils.formatPeriod(currMajorStatusModifiedOn, currModifiedOn, "d")));

                Fields.In.setValue("days_since_last_modified", workingState, (0L == prevModifiedOn ? zero :
                        Long.valueOf(DurationFormatUtils.formatPeriod(prevModifiedOnMs, currModifiedOn, "d"))));

                Fields.In.setValue("days_in_previous_status", workingState, (0L == prevStatusModifiedOn ? zero :
                        Long.valueOf(DurationFormatUtils.formatPeriod(prevStatusModifiedOnMs, currStatusModifiedOn, "d"))));

                Fields.In.setValue("days_in_previous_major_status", workingState, (0L == prevMajorStatusModifiedOn ? zero :
                        Long.valueOf(DurationFormatUtils.formatPeriod(prevMajorStatusModifiedOnMs, currMajorStatusModifiedOn, "d"))));

                /*
                debugPrintRow(35L, "created_on", data.inputRowMeta, workingState);
                debugPrintRow(35L, "curr_modified_on", data.inputRowMeta, workingState);
                debugPrintRow(35L, "curr_status_modified_on", data.inputRowMeta, workingState);
                debugPrintRow(35L, "curr_major_status_modified_on", data.inputRowMeta, workingState);
                debugPrintRow(35L, "prev_modified_on", data.inputRowMeta, workingState);
                debugPrintRow(35L, "prev_status_modified_on", data.inputRowMeta, workingState);
                debugPrintRow(35L, "prev_major_status_modified_on", data.inputRowMeta, workingState);
                debugPrintRow(35L, "days_since_created", data.inputRowMeta, workingState);
                debugPrintRow(35L, "days_in_current_status", data.inputRowMeta, workingState);
                debugPrintRow(35L, "days_in_current_major_status", data.inputRowMeta, workingState);
                debugPrintRow(35L, "days_since_last_modified", data.inputRowMeta, workingState);
                debugPrintRow(35L, "days_in_previous_status", data.inputRowMeta, workingState);
                debugPrintRow(35L, "days_in_previous_major_status", data.inputRowMeta, workingState);
                */
            } catch (Exception e) {
                debugPrintRow(0L, ".*_on|.*bug_id|bug_nk", data.inputRowMeta, workingState);
                throw new KettleStepException("Error during duration measures calculation", e);
            }

            //debugPrintRow(35L, "days_", data.inputRowMeta, workingState);
            //debugPrintRow(71L, "component", data.inputRowMeta, workingState);
            outputRow(workingState, hasProdChange);

            thisRow = nextRow;
        }

        if (!isNewBug) {
            if (numActivities == 0) counters.getAndIncrement(6); // old bug no activity (untracked event?)
            else if (numActivities <= 5) counters.getAndIncrement(7); // old bug 1 to 5 activity
            else if (numActivities <= 10) counters.getAndIncrement(8); // old bug 6 to 10 activity
            else counters.getAndIncrement(9); // old bug more than 10 activity
        }
    }


    private boolean populatePreviousValues(Object[] earliestRow) throws KettleException
    {
        // If it is a new bug, set all the const defaults and the currs to working state.
        if (isNewBug) {
            Fields.In.row_number.setValue(workingState, zero);

            // bootstrap the valid from with the creation date as a long.
            Fields.In.valid_from_ts.setValue(workingState, Fields.In.creation_ts.getDate(workingState));
            Date validTo = Fields.In.bug_when.getDate(earliestRow);
            if (validTo == null) {
                Fields.In.valid_to_ts.setValue(workingState, future);
            } else {
                Fields.In.valid_to_ts.setValue(workingState, DateUtils.addSeconds(validTo, -1));
            }

            for (int i = 0; i < baseFields.length; i++) {
                String fieldName = baseFields[i];
                String newValue = Fields.In.getStringValue(fieldName, workingState);
                if (newValue == null) newValue = "<none>";
                Fields.In.setValue("curr_"+fieldName, workingState, newValue);
            }

            String currStatus = Fields.In.getStringValue("curr_status", workingState);
            Long currStatusState = (Long)statusStates.get(currStatus);
            Fields.In.setValue("curr_major_status", workingState, currStatus);
            Fields.In.setValue("net_open", workingState, currStatusState);
            Fields.In.setValue("is_open", workingState, currStatusState);
            Fields.In.setValue("is_closed", workingState, one.equals(currStatusState) ? zero : one);

            for (int i = 0; i < newBugReporterFields.length; i++) {
                String fieldName = newBugReporterFields[i];
                Fields.In.setValue(fieldName, workingState, Fields.In.getLongValue("reported_by", workingState));
            }

            for (int i = 0; i < newBugProductFieldPrefixes.length; i++) {
                String fieldName = newBugProductFieldPrefixes[i];
                for (int j = 0; j < prodFields.length; j++) {
                    Fields.In.setValue(fieldName+prodFields[j], workingState, Fields.In.getStringValue("curr_"+prodFields[j], workingState));
                }
            }

            for (int i = 0; i < newBugCreatedFieldPrefixes.length; i++) {
                String fieldName = newBugCreatedFieldPrefixes[i];
                Fields.In.setValue(fieldName+"on", workingState, Fields.In.getLongValue("created_on", workingState));
                Fields.In.setValue(fieldName+"at", workingState, Fields.In.getLongValue("created_at", workingState));
            }

            for (int i = 0; i < newBugStringDefaultFields.length; i++) {
                String fieldName = newBugStringDefaultFields[i];
                Fields.In.setValue(fieldName, workingState, "<none>");
            }

            for (int i = 0; i < newBugPrevOneDefaultFields.length; i++) {
                String fieldName = newBugPrevOneDefaultFields[i];
                Fields.In.setValue(fieldName, workingState, one);
            }

            for (int i = 0; i < newBugPrevZeroDefaultFields.length; i++) {
                String fieldName = newBugPrevZeroDefaultFields[i];
                Fields.In.setValue(fieldName, workingState, zero);
            }

            return true;
        }


        Date previousValidFrom = Fields.In.valid_from_ts.getDate(workingState);
        if (previousValidFrom == null) {
            // First time called for this bug, we haven't initialized the working state
            // with the values from the existing record in the data warehouse
            System.arraycopy(earliestRow, 0, workingState, 0, earliestRow.length);
            previousValidFrom = Fields.In.valid_from_ts.getDate(workingState);
            Fields.In.setValue("bug_id", workingState, currentBugID);
            Fields.In.setValue("created_on", workingState, Fields.In.getLongValue("curr_created_on", earliestRow));
            Fields.In.setValue("created_at", workingState, Fields.In.getLongValue("curr_created_at", earliestRow));
        }
        Date thisBugWhen = Fields.In.bug_when.getDate(earliestRow);
        // Determine if there are any activities we have already processed that we should skip
        if (previousValidFrom != null && thisBugWhen != null && previousValidFrom.compareTo(thisBugWhen) < 0) {
            // This is an unprocessed activity...
            // Set the valid_to of the old record to end before the first activity of this record
            Fields.In.previous_valid_to_ts.setValue(workingState, DateUtils.addSeconds(thisBugWhen, -1));
            for (int i = 0; i < baseFields.length; i++) {
                String fieldName = baseFields[i];
                String newValue = Fields.In.getStringValue(fieldName, workingState);
                String currValue = Fields.In.getStringValue("curr_"+fieldName, workingState);
                if ((currValue != null && !currValue.equals(newValue)) || (currValue == null && newValue != null)) {
                    counters.getAndIncrement(10); // Mismatch on old bug earliest state
                }
            }
            return true;
        }
        return false;

    }

    private void initializeWorkingState(Object[] latestRow) throws KettleException
    {
        System.arraycopy(latestRow, 0, workingState, 0, latestRow.length);

        for (int i = 0; i < baseFields.length; i++) {
            String fieldName = baseFields[i];
            String fieldValue = Fields.In.getStringValue(fieldName, workingState);

            // Set any null fields to the <none> default.
            if (fieldValue == null) {
                Fields.In.setValue(fieldName, workingState, "<none>");
            }

            // Clear out the _from _to fields to reduce confusion
            Fields.In.setValue(fieldName+"_from", workingState, null);
            Fields.In.setValue(fieldName+"_to", workingState, null);
        }

        //debugPrintRow(38L, "^status$", data.inputRowMeta, workingState);
    }

    private void outputRow(Object[] referenceRow, boolean hasProdChange) throws KettleException
    {
        Object[] newRow = RowDataUtil.allocateRowData(outputRowSize);
        for (Iterator iterator = Fields.Out.hashFields.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry entry = (Map.Entry)iterator.next();
            FieldHelper fieldHelper = (FieldHelper)entry.getValue();
            String fieldName = (String)entry.getKey();
            if (hasOutputEarliestRecord) {
                if (fieldName.matches("bug_state_tk|previous_valid_to_ts|set_previous_is_latest_state_to_zero")) {
                    continue;
                }
            }
            fieldHelper.setValue(newRow, Fields.In.getObjectValue(fieldName, referenceRow));
        }

        hasOutputEarliestRecord = true;

        if (hasProdChange) {
            // As a special case, rewrite the curr_modified_by_team fields to be the previous ones if the product changed.
            for (int i = 0; i < modifiedTeamFields.length; i++) {
                String fieldName = modifiedTeamFields[i];
                Fields.Out.setValue("curr_"+fieldName, newRow, Fields.In.getStringValue("prev_"+fieldName, referenceRow));
            }
        }

        if (DateUtils.isSameInstant(future, Fields.Out.valid_to_ts.getDate(newRow))) {
            Fields.Out.is_latest_state.setValue(newRow, one);
        } else {
            Fields.Out.is_latest_state.setValue(newRow, zero);
        }

        //debugPrintRow(0L, "bug_id|(curr|prev)_opsys", data.inputRowMeta, referenceRow);
        putRow(data.outputRowMeta, newRow);
    }

    private void debugPrintRow(Long debugID, String filterField, RowMetaInterface rowMeta, Object[] row) {
        if (debugID == 0L || currentBugID.equals(debugID)) {
            boolean printedRow = false;
            String[] names = rowMeta.getFieldNamesAndTypes(45);
            for (int i = 0; i < names.length; i++) {
                if (filterField == null || names[i].split(" ",2)[0].matches(filterField)) {
                    printedRow = true;
                    System.err.format("%-60s%s%n", new Object[] { names[i], row[i] });
                }
            }
            if (printedRow)
                System.err.println();
        }
    }

    public static String[] getInfoSteps()
    {
        return new String[] { "Get Status States" };
    }

    public static void getFields(RowMetaInterface row, String originStepname, RowMetaInterface[] info, StepMeta nextStep, VariableSpace space, List fields)
    {
        row.clear();
        for (Iterator iterator = fields.iterator(); iterator.hasNext();)
        {
            FieldInfo fi = (FieldInfo)iterator.next();
            ValueMetaInterface v;
            v = new ValueMeta(fi.name, fi.type);
            v.setLength(fi.length);
            v.setPrecision(fi.precision);
            v.setOrigin(originStepname);
            row.addValueMeta(v);
        }
    }

                                                                                             // snip
}                                                                                            // snip
                                                                                             // snip