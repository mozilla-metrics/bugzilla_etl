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

package com.mozilla.bugzilla_etl.di;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicIntegerArray;

import org.pentaho.di.core.RowSet;
import org.pentaho.di.core.exception.KettleStepException;
import org.pentaho.di.core.exception.KettleValueException;
import org.pentaho.di.trans.steps.userdefinedjavaclass.TransformClassBase;

import com.mozilla.bugzilla_etl.base.Assert;
import com.mozilla.bugzilla_etl.base.Bug;
import com.mozilla.bugzilla_etl.base.Converters;
import com.mozilla.bugzilla_etl.base.Counter;
import com.mozilla.bugzilla_etl.base.Fields;
import com.mozilla.bugzilla_etl.base.Flag;
import com.mozilla.bugzilla_etl.base.Lookup;
import com.mozilla.bugzilla_etl.base.Pair;
import com.mozilla.bugzilla_etl.base.Version;
import com.mozilla.bugzilla_etl.di.io.Input;
import com.mozilla.bugzilla_etl.di.io.Input.Row;


/**
 * Does the work for the Rebuild-History Step.
 *
 * Incoming Bugs are represented by a table that contains current bug status on the left side,
 * and incremental changes on the right side (by date desc). From those incremental changes, bug
 * versions are constructed. Changed fields replace fields in their predecessor version.
 *
 * There is some special treatment for flags: In the activity table, incremental changes for
 * flags are logged per flag. This means that we also have to reconstruct the set of effective flags
 * in reverse order.
 */
public class RebuilderBugSource extends AbstractSource<Bug> {

    private boolean DEBUG_REORDER = false;

    private Lookup<Bug, KettleStepException> bugLookup;
    public RebuilderBugSource(TransformClassBase step,
                              RowSet bugs,
                              RowSet majorStatusLookup,
                              final Lookup<Bug, ? extends Exception> bugLookup)
    throws KettleStepException, KettleValueException {
        super(step, bugs);
        majorStatusTable = new MajorStatusHelper(step, majorStatusLookup).table();
        // Wrap lookup to convert exception
        this.bugLookup = new Lookup<Bug, KettleStepException>() {
            @Override
            public Bug find(Long id) throws KettleStepException {
                try { return bugLookup.find(id); }
                catch (Exception e) {
                    String message = String.format("Trouble looking up bug %s: %s \n",
                                                   id,
                                                   e.getClass().getSimpleName());
                    System.out.print(message);
                    e.printStackTrace(System.out);
                    throw new KettleStepException(message, e);
                }
            }
        };
    }

    /** Are there more bugs in the input? */
    @Override
    public boolean hasMore() {
        return !input.empty();
    }

    /** Assemble a versioned bug from bug state plus activities. */
    @Override
    public Bug receive() throws KettleValueException, KettleStepException {
        return bugFromRows();
    }

    /**
     * Construct a bug using the current row, and all subsequent rows that are
     * referring to the same bug, advancing the current input row past all rows
     * that belong to this bug.
     */
    private Bug bugFromRows() throws KettleValueException, KettleStepException {

        // Get author and date of creation for least recent version (non-incremental).
        final Date creationStateDate = input.cell(Fields.Bug.CREATION_DATE).dateValue();
        final String creationStateAuthor = input.cell(Fields.Bug.REPORTED_BY).stringValue();
        final Long bugId = input.cell(Fields.Bug.ID).longValue();

        if (bugId == null) {
            Assert.unreachable("Cannot have bug_id of NULL. Encountered in fields from bugs table.");
        }
        if (creationStateAuthor == null) {
            Assert.unreachable("Author from bugs table for bug %s is null!", bugId);
        }

        System.out.format("Rebuilder: processing bug %s\n", bugId);

        final Bug bug = new Bug(bugId, creationStateAuthor, creationStateDate);

        // Keep current facet state and mutate it while walking the revisions.
        // Keep flags state separate so it does not have to be parsed for every comparison.
        Pair<EnumMap<Fields.Facet, String>, Map<String, Flag>> state = stateFromBug(input.row());
        final EnumMap<Fields.Facet, String> facetState = state.first();
        final Map<String, Flag> flagState = state.second();

        if (input.cell(Fields.Version.BUG_ID).longValue() == null) {
            // No activities (yet). Advance to the next bug and use this bug as-is (initial state).
            // This should always be the latest version of a bug (initial import: also the first).
            bug.prepend(Version.latest(bug, facetState, creationStateAuthor, creationStateDate,
                                       annotation("0 new activities")));
            input.next();
            applyAnyPersistedState(bug);
        }
        else {
            processActivitiesRows(bug, facetState, flagState);
            if (!applyAnyPersistedState(bug)) {
                // Initial import: Add creation revision.
                Version successor = bug.iterator().next();
                Date adjustedDate = creationStateDate;
                if (successor.from().equals(creationStateDate)) {
                    // First modification has same date. Adjust creation date.
                    adjustedDate = new Date(creationStateDate.getTime() - deltaMs);
                }
                bug.prepend(successor.predecessor(facetState, creationStateAuthor, adjustedDate,
                                                  annotation("initial")));
            }
        }

        bug.updateFacetsAndMeasurements(majorStatusTable, now);
        counter.count(bug);
        return bug;
    }

    /**
     * @const
     * @return the current state facet state and flag state of the bug.
     */
    private Pair<EnumMap<Fields.Facet, String>, Map<String, Flag>> stateFromBug(Input.Row row)
    throws KettleValueException {
        // Use the "left leg" (latest bug state from bugzilla) to construct the "current" version.
        final EnumMap<Fields.Facet, String> facetState = Version.createFacets();
        final Map<String, Flag> flagState = new java.util.HashMap<String, Flag>();
        for (Fields.Facet facet : Fields.Facet.values()) {
            if (facet.isComputed) continue;
            String value = row.cell(facet).stringValue();
            facetState.put(facet, value);
            if (facet == Fields.Facet.FLAGS && value != null) {
                for (Flag flag : Converters.FLAGS.parse(value)) {
                    flagState.put(flag.name(), flag);
                }
            }
        }
        return new Pair<EnumMap<Fields.Facet, String>, Map<String, Flag>>(facetState, flagState);
    }

    /**
     * Initialize bug versions (except creation) from the bug activities table.
     * Facet state and flag state are manipulated in place.
     */
    private void processActivitiesRows(final Bug bug,
                                       final EnumMap<Fields.Facet, String> facetState,
                                       final Map<String, Flag> flagState)
    throws KettleValueException, KettleStepException {
        final LinkedList<Input.Row> candidates = new java.util.LinkedList<Input.Row>();
        Date candidateDate = null;
        do {
            final Date currentDate = input.cell(Fields.Version.MODIFICATION_DATE).dateValue();
            if (!candidates.isEmpty() && !currentDate.equals(candidateDate)) {
                // New date, process all activities with the previous (more recent) date.
                processCandidates(bug, candidates, facetState, flagState);
                candidates.clear();
            }
            candidateDate = currentDate;
            candidates.add(input.row());
        } while (input.next() && bug.id().equals(input.cell(Fields.Version.BUG_ID).longValue()));

        // the last set of candidates is not processed yet
        processCandidates(bug, candidates, facetState, flagState);
    }

    /**
     * If there is a persisted version of this bug, rebase it on that.
     * @return <tt>true</tt> if this bug has been rebased, <tt>false</tt> otherwise.
     */
    private boolean applyAnyPersistedState(final Bug bug) throws KettleStepException {
        final Bug persistentBug = bugLookup.find(bug.id());
        if (persistentBug == null) return false;
        bug.baseUpon(persistentBug);
        return true;
    }

    /**
     * Create predecessor revisions from the given bug activities and append them to the bug.
     *
     * If there are multiple candidate activities, order them in such a way that their operations
     * are consistent with the given facetState and flagState. If that cannot be achieved, the
     * change caused by the lower userid (probably nobody@mozilla.com in case of a batch
     * modification) wins.
     * :TODO: If we were to collect facet-ids and use a comparison of activities table and bug
     * table to get historic mappings of string values to IDs, we probably could achieve almost
     * 100% coverage for the consistent algorithm. Plus we would have a much healthier Solr index.
     *
     * It is expected that in >99% of the cases, candidates will be of size 1, as usually activity
     * order should be ensured by bugzilla collision detection.
     *
     * Find activities to test this with:
        SELECT bug_id
        , bug_when
        , COUNT(DISTINCT who) AS num_logins
        , GROUP_CONCAT(DISTINCT login_name) AS logins
        , GROUP_CONCAT(DISTINCT `fielddefs`.`name`) AS fields
        , GROUP_CONCAT(DISTINCT removed) AS froms
        , GROUP_CONCAT(DISTINCT added) AS tos
        FROM `bugs_activity` ba
        LEFT JOIN fielddefs ON fielddefs.`id` = fieldid
        LEFT JOIN profiles p ON who = userid
        GROUP BY bug_id, bug_when
        HAVING COUNT(DISTINCT who) > 1;
     *
     * Bugs known to need fallback ordering: 40139
     *
     *
     * @param bug @rw The bug to prepend new predecessor versions to.
     * @param candidates The candidates whose "TO" state each descibes a new version to add.
     * @param facetState @rw The facet state is modified as the activities are rewound.
     * @param flagState @rw The flag state that is modified as the activities are rewound.
     *                  After the last activity has been processed, it can be used to add a
     *                  creation/initial revision.
     *
     * @throws KettleValueException
     */
    private void processCandidates(final Bug bug,
                                   final LinkedList<Input.Row> candidates,
                                   final EnumMap<Fields.Facet, String> facetState,
                                   final Map<String, Flag> flagState)
    throws KettleValueException
    {
        final int n = candidates.size();
        Assert.check(n > 0);
        final Date modificationDate
            = candidates.get(0).cell(Fields.Version.MODIFICATION_DATE).dateValue();
        if (n > 3) Assert.unreachable(
            "There are %d simultaneous changes to bug #%d at %s?! Must be an error.\n",
            n, bug.id(), modificationDate
        );
        if (n > 1) conflictCount.incrementAndGet(n);

        LinkedList<Input.Row> activities = orderConsistently(candidates, facetState, flagState);
        if (n >= 3 || activities == null || (DEBUG_REORDER && n >= 2)) {
            printReorderDebugInformation(bug, candidates, facetState);
        }
        if (activities == null) {
            System.out.format("REORDER ACTIVITIES: Using fallback ordering for bug %s.\n", bug);
            fallbackCount.incrementAndGet(n);
            activities = orderByUser(candidates);
        }

        Assert.nonNull(activities);
        Assert.check(activities.size() == n);
        int i = n;
        for (Input.Row activity : activities) {
            final String author = activity.cell(Fields.Version.MODIFIED_BY).stringValue();
            final Version v;
            final StringBuilder details = new StringBuilder();
            Date date = modificationDate;
            if (n > 1) {
                // Bump by ten milliseconds. This would allow to always put the expiration date
                // at one millisecond before the next modification date to avoid any overlap.
                details.append(i).append('/').append(n).append(" concurrent ");
                date = new Date(modificationDate.getTime() + ((i-1) * deltaMs));
            }

            if (bug.numVersions() == 0) {
                // Special case: appending the first (most recent) activity.
                details.append("most recent version");
                v = Version.latest(bug, facetState, author, date, annotation(details));
            }
            else {
                // Append version as predecessor to a more recent version.
                v = bug.iterator().next().predecessor(facetState, author, date, annotation(details));
            }
            bug.prepend(v);
            invertActivity(activity, facetState, flagState);
            --i;
        }
    }

    /**
     * @throws KettleValueException
     * @const
     *
     * Order activities from most-recent to least-recent. The time is actually the same, but the
     * most recent activities will have their "FROM" time bumped by 10 milliseconds each (by us).
     *
     * Workhorse for processCandidates (esp. n >=2). Determines the correct permutation of
     * activities without modifying shared facet/flag state or creating actual versions.
     *
     * Works recursively:
     * From the candidates, check each activity that can possibly be the last activity (agreeing
     * with the target state). Then repeat the process for the list of remaining activities. If no
     * order is valid, return null. The first order that is possible is used.
     */
    private LinkedList<Input.Row> orderConsistently(final LinkedList<Input.Row> candidates,
                                                    final EnumMap<Fields.Facet, String> facetState,
                                                    final Map<String, Flag> flagState)
    throws KettleValueException {
        if (candidates.size() <= 1) return candidates;

        for(Pair<Integer, Input.Row> next : Pair.enumerate(candidates)) {
            final int i = next.first();
            final Input.Row candidate = next.second();

            final EnumMap<Fields.Facet, String> restFacetState = facetState.clone();
            final Map<String, Flag> restFlagState = new HashMap<String, Flag>(flagState);
            if (!invertActivity(candidate, restFacetState, restFlagState)) continue;

            final LinkedList<Input.Row> rest = new LinkedList<Input.Row>();
            rest.addAll(candidates.subList(0, i));
            rest.addAll(candidates.subList(i+1, candidates.size()));
            final LinkedList<Input.Row> list = orderConsistently(rest, restFacetState, restFlagState);
            if (list != null) {
                list.addFirst(candidate);
                return list;
            }
        }
        return null;
    }

    /** :TODO: Use the numeric user id for this once available. */
    private LinkedList<Row> orderByUser(final LinkedList<Input.Row> candidates) {
        LinkedList<Input.Row> activities = new LinkedList<Input.Row>(candidates);
        Comparator<Input.Row> comp = new Comparator<Input.Row>() {
            public static final String NOBODY = "nobody@mozilla.org";
            @Override
            public int compare(Input.Row rowA, Input.Row rowB) {
                String valueA = null;
                String valueB = null;
                try {
                    valueA = rowA.cell(Fields.Version.MODIFIED_BY).stringValue();
                    valueB = rowB.cell(Fields.Version.MODIFIED_BY).stringValue();
                    if (valueA.equals(NOBODY)) return -1;
                    if (valueB.equals(NOBODY)) return 1;
                    System.out.format("REORDER ACTIVITIES: Weak result with fallback ordering.\n");
                }
                catch (KettleValueException vE) { Assert.unreachable(); return 0; }
                return valueA.compareTo(valueB);
            }
        };
        Collections.sort(activities, comp);
        return activities;
    }

    /**
     * @const
     * Produce the next older version of the facets from the given activity row.
     * Do this by cloning the more recent version and replaying in the direction
     * <tt>TO</tt> to <tt>FROM</tt>.
     * While doesActivityLeadTo only checks whether a transition is possible, this
     * method actually does it and yields the corresponding facets.
     *
     * @param activity the activity to rewind
     * @param facetState @rw The facets, updated by this step to yield the FROM state.
     *                   If you want the results out-of-place, clone before passing in.
     * @param flagState @rw A read/write parameter to get and update the curent flag collection
     *                  without parsing the successor&apos;s field.
     *
     * @return <tt>true</tt> if the activity could be rewound, <tt>false</tt> if the given
     *         facet state does not match the target state. Observe that the state parameters
     *         will contain garbage now.
     */
    private boolean invertActivity(final Input.Row activity,
                                   final EnumMap<Fields.Facet, String> facetState,
                                   final Map<String, Flag> flagState)
    throws KettleValueException {
        boolean isConsistent = true;
        for (Fields.Facet facet : Fields.Facet.values()) {
            if (facet.isComputed) continue;
            final String toValue = activity.cell(facet, Fields.Facet.Column.TO).stringValue();
            final String fromValue = activity.cell(facet, Fields.Facet.Column.FROM).stringValue();
            if (fromValue.isEmpty() && toValue.isEmpty()) continue;
            if (facet == Fields.Facet.FLAGS) {
                // successor.to() is the complete set for the successor state, while fromValue and
                // toValue describe an incremental change leading to it. We apply it in reverse to
                // obtain the predecessor value.
                for (Flag toFlag : Converters.FLAGS.parse(toValue)) {
                    if (!flagState.containsKey(toFlag.name())) {
                        isConsistent = false;
                        System.out.format("Inconsistent change on bug %s: set flag missing: '%s'\n",
                                          activity.cell(Fields.Version.BUG_ID).longValue(), toFlag);
                    }
                    else {
                        flagState.remove(toFlag.name());
                    }
                }

                for (Flag fromFlag : Converters.FLAGS.parse(fromValue)) {
                    if (flagState.containsKey(fromFlag.name())) {
                        System.out.format("Inconsistent change on bug %s: flag unwarranted: '%s'\n",
                                          activity.cell(Fields.Version.BUG_ID).longValue(), fromFlag);
                        isConsistent = false;
                    }
                    flagState.put(fromFlag.name(), fromFlag);
                }

                List<Flag> completeFromFlags = new java.util.LinkedList<Flag>(flagState.values());
                facetState.put(facet, Converters.FLAGS.format(completeFromFlags));
                continue;
            }
            if (!toValue.equals(facetState.get(facet))) {
                isConsistent = false;
                System.out.format(
                    "Inconsistent change on bug %s field %s: expected '%s' (but got '%s')\n",
                    activity.cell(Fields.Version.BUG_ID).longValue(), facet.name().toLowerCase(),
                    facetState.get(facet), toValue
                );
            }
            facetState.put(facet, fromValue);
        }
        return isConsistent;
    }

    private String annotation(CharSequence maybeDetails) {
        StringBuilder annotation = new StringBuilder();
        annotation.append("IMPORTED").append(" at ").append(isoFormat.format(now));
        if (maybeDetails != null && maybeDetails.length() > 0) {
            annotation.append(" (").append(maybeDetails).append(")");
        }
        return annotation.toString();
    }

    private void printReorderDebugInformation(Bug bug,
                                              List<Input.Row> candidates,
                                              EnumMap<Fields.Facet, String> facetState)
    throws KettleValueException {
        System.out.format("\nREORDER ACTIVITIES (bug #%s)\n", bug.id());
        System.out.format("\nREORDER ACTIVITIES, #reordered: %d\n", candidates.size());
        System.out.format("\nREORDER ACTIVITIES, target:\n");
        for (Fields.Facet facet : Fields.Facet.values()) {
            System.out.format("%s=%s \n", facet, facetState.get(facet));
        }
        System.out.format("\n");
        System.out.format("REORDER ACTIVITIES, Candidates:\n");
        int i = 0;
        for (Input.Row row : candidates) {
            ++i;
            System.out.format("activity %d (author: %s)\n",
                              i, row.cell(Fields.Version.MODIFIED_BY).stringValue());
            for (Fields.Facet facet : Fields.Facet.values()) {
                if (facet.isComputed) continue;
                String from = row.cell(facet, Fields.Facet.Column.FROM).stringValue();
                String to = row.cell(facet, Fields.Facet.Column.TO).stringValue();
                if (from.isEmpty() && to.isEmpty()) continue;
                System.out.format("%d from: %s=%s\n", i, facet, from);
                System.out.format("%d to:   %s=%s\n", i, facet, to);
            }
        }
    }

    /** Collect counts for acitivities in general. */
    public static final Counter counter = new Counter("Bug history rebuilder");

    /** Collect counts for up to four simultaneous activities. The index 0 is not used. */
    public static final AtomicIntegerArray conflictCount = new AtomicIntegerArray(4);
    public static final AtomicIntegerArray fallbackCount = new AtomicIntegerArray(4);
    public static void printConflictCounts() {
        System.out.print("Concurrent modifications: \n");
        for (int i = 1; i < conflictCount.length(); ++i) {
            if (conflictCount.get(i) == 0) continue;
            System.out.format("%d simultaneous modifications: %d times", i, conflictCount.get(i));
            System.out.format(" (%d among these needed fallback ordering)\n", fallbackCount.get(i));
        }
    }

    /**
     * The date the transformation is run.
     * :TODO: Get this from an external input.
     */
    private final Date now = new Date();

    private final int deltaMs = 10;
    private static final DateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm z");
    private final Map<String, String> majorStatusTable;

}
