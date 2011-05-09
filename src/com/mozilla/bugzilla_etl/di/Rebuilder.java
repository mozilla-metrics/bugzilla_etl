package com.mozilla.bugzilla_etl.di;

import java.io.PrintStream;
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

import org.pentaho.di.core.exception.KettleStepException;
import org.pentaho.di.core.exception.KettleValueException;

import com.mozilla.bugzilla_etl.base.Assert;
import com.mozilla.bugzilla_etl.base.Lookup;
import com.mozilla.bugzilla_etl.base.Pair;
import com.mozilla.bugzilla_etl.di.Converters.Converter;
import com.mozilla.bugzilla_etl.di.io.Input;
import com.mozilla.bugzilla_etl.di.io.Input.Row;
import com.mozilla.bugzilla_etl.model.Entity;
import com.mozilla.bugzilla_etl.model.Field;
import com.mozilla.bugzilla_etl.model.Fields;
import com.mozilla.bugzilla_etl.model.Version;
import com.mozilla.bugzilla_etl.model.bug.Flag;


/**
 * @param <E> The entity type to be rebuilt from the activity table.
 * @param <V> The associated version type.
 * @param <FACET> The enumeration of facets associated with E.
 */
public abstract class Rebuilder<E extends Entity<E, V, FACET>,
                                V extends Version<E, V, FACET>,
                                FACET extends Enum<FACET> & Field,
                                FLAG extends Flag> {

    public Rebuilder(final Input input,
                     final Lookup<E, ? extends Exception> lookup,
                     final Converter<List<FLAG>> flagsConverter) {
        this.input = input;
        this.lookup = new Lookup<E, KettleStepException>() {
            @Override
            public E find(Long id) throws KettleStepException {
                try { return lookup.find(id); }
                catch (Exception e) {
                    String message = String.format("Trouble looking up bug %s: %s \n",
                                                   id, e.getClass().getSimpleName());
                    System.out.print(message);
                    e.printStackTrace(System.out);
                    throw new KettleStepException(message, e);
                }
            }
        };
        this.flagConverter = flagsConverter;
    }


    public static class Creation { public Long id; public String creator; public Date date; };
    class State { EnumMap<FACET, String> facets; Map<String, FLAG> flags; };


    /**
     * Construct entity using the current row, and all subsequent rows that are
     * referring to the same entity, advancing the current input row past all
     * rows belonging to this entity.
     */
    public E fromRows() throws KettleValueException, KettleStepException {

        Creation creation = base(input);
        if (creation.id == null) {
            Assert.unreachable("%s: id=null from entities table!", getClass().getSimpleName());
        }
        if (creation.creator == null) {
            Assert.unreachable("%s, id=%s: creator=null from entities table!",
                               getClass().getSimpleName(), creation.id);
        }

        final E entity = get(creation, input);

        // Keep current facet state and mutate it while walking the activities.
        // Keep flags state separate so it does not have to be parsed for every comparison.
        final State state = stateFromEntityTable(entity, input.row());
        boolean rowContainsActivity = input.cell(Fields.Activity.ENTITY_ID).longValue() != null;
        if (rowContainsActivity) {
            processActivitiesRows(entity, state);
            if (!applyAnyPersistedState(entity)) {
                // Initial import: Add creation revision as "bottom".
                final V first = entity.iterator().next();
                Date safeDate = !first.from().equals(creation.date)
                                ? creation.date
                                : new Date(creation.date.getTime() - safetyDeltaMs);
                final V newFirst = first.predecessor(state.facets, creation.creator, safeDate,
                                                     annotation("initial"));
                entity.prepend(newFirst);
            }
        }
        else {
            // No activities yet: The date from the entity table is the only (new) version.
            // If this is an update append any existing versions. Advance to the next bug.
            entity.prepend(entity.latest(state.facets, creation.creator, creation.date,
                                         annotation("0 new activities")));
            input.next();
            applyAnyPersistedState(entity);
        }

        updateFacetsAndMeasurements(entity, now);
        return entity;
    }


    /**
     * Initialize versions (except creation) from the activities table.
     * Facet/flag state is modified in place from most recent to least recent version.
     */
    private void processActivitiesRows(final E bug, final State state)
    throws KettleValueException, KettleStepException {
        final LinkedList<Input.Row> candidates = new java.util.LinkedList<Input.Row>();
        Date candidateDate = null;
        do {
            final Date currentDate = input.cell(Fields.Activity.MODIFICATION_DATE).dateValue();
            if (!candidates.isEmpty() && !currentDate.equals(candidateDate)) {
                // Next date. Process all activities with the previous (more recent) date.
                processCandidates(bug, candidates, state);
                candidates.clear();
            }
            candidateDate = currentDate;
            candidates.add(input.row());
        } while (input.next() && bug.id().equals(input.cell(Fields.Activity.ENTITY_ID).longValue()));

        // the last set of candidates is not processed yet
        processCandidates(bug, candidates, state);
    }


    private boolean DEBUG_REORDER = false;


    /**
     * Create predecessor versions from the given activity rows.
     *
     * If there are multiple candidate activities (same timestamp), order them so that their
     * operations are consistent with the given state. If that fails, the change caused by the
     * lower userid wins (probably nobody@mozilla.com in case of a batch modification).
     *
     * :BMO: Bugs known to need the latter treatment (for testing): 40139
     *
     * It is expected that in >99% of the cases, candidates will be of size 1, as usually activity
     * order should be ensured by bugzilla collision detection.
     *
     *
     * @param entity @rw The bug to prepend new predecessor versions to.
     * @param candidates The candidates whose "TO" state each descibes a new version to add.
     * @param state Modified as activities are "rewound"
     *
     * @throws KettleValueException
     */
    private void processCandidates(final E entity,
                                   final LinkedList<Input.Row> candidates,
                                   final State state)
    throws KettleValueException
    {
        final int n = candidates.size();
        Assert.check(n > 0);
        final Date modificationDate
            = candidates.get(0).cell(Fields.Activity.MODIFICATION_DATE).dateValue();
        if (n > 4) Assert.unreachable(
            "There are %d simultaneous changes to bug #%d at %s?! Must be an error.\n",
            n, entity.id(), modificationDate
        );
        if (n > 1) conflictCount.incrementAndGet(n);

        LinkedList<Input.Row> activities = orderConsistently(candidates, state);
        if (n >= 3 || activities == null || (DEBUG_REORDER && n >= 2)) {
            printReorderDebugInformation(entity, candidates, state);
        }
        if (activities == null) {
            System.out.format("REORDER ACTIVITIES: Using fallback ordering for bug %s.\n", entity);
            fallbackCount.incrementAndGet(n);
            activities = orderByUser(candidates);
        }

        Assert.nonNull(activities);
        Assert.check(activities.size() == n);
        int i = n;
        for (Input.Row activity : activities) {
            final String author = activity.cell(Fields.Activity.MODIFIED_BY).stringValue();
            final V v;
            final StringBuilder details = new StringBuilder();
            Date date = modificationDate;
            if (n > 1) {
                // Bump by ten milliseconds. This would allow to always put the expiration date
                // at one millisecond before the next modification date to avoid any overlap.
                details.append(i).append('/').append(n).append(" concurrent ");
                date = new Date(modificationDate.getTime() + ((i-1) * safetyDeltaMs));
            }

            if (entity.numVersions() == 0) {
                // Special case: the first (most recent) activity.
                details.append("most recent version");
                v = entity.latest(state.facets, author, date, annotation(details));
            }
            else {
                // Append version as predecessor to a more recent version.
                v = entity.iterator().next().predecessor(state.facets, author, date,
                                                         annotation(details));
            }
            entity.prepend(v);
            invertActivity(activity, state);
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
                                                    final State state)
    throws KettleValueException {
        if (candidates.size() <= 1) return candidates;

        for(Pair<Integer, Input.Row> next : Pair.enumerate(candidates)) {
            final int i = next.first();
            final Input.Row candidate = next.second();

            final State tryout = new State() {{
                facets = state.facets.clone();
                flags = new HashMap<String, FLAG>(state.flags);
            }};

            if (!invertActivity(candidate, tryout)) continue;

            final LinkedList<Input.Row> rest = new LinkedList<Input.Row>();
            rest.addAll(candidates.subList(0, i));
            rest.addAll(candidates.subList(i+1, candidates.size()));
            final LinkedList<Input.Row> list = orderConsistently(rest, tryout);
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
                    valueA = rowA.cell(Fields.Activity.MODIFIED_BY).stringValue();
                    valueB = rowB.cell(Fields.Activity.MODIFIED_BY).stringValue();
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
     * Produce the next older state from the given activity row by applying the activity backwards.
     *
     * @param activity the activity to invert
     * @param state @rw The state to rewind from (will be modified)
     *
     * @return <tt>true</tt> if the activity could be rewound, <tt>false</tt> if the given
     *         facet state does not match the target state. Happens when products, components etc.
     *         were modified without touching the activity table.
     */
    private boolean invertActivity(final Input.Row activity, final State state)
    throws KettleValueException {
        boolean isConsistent = true;
        final PrintStream out = System.out;
        for (FACET facet : state.facets.keySet()) {
            if (isComputed(facet)) continue;
            final String toValue = activity.cell(facet, Fields.Column.TO).stringValue();
            final String fromValue = activity.cell(facet, Fields.Column.FROM).stringValue();
            if (fromValue.isEmpty() && toValue.isEmpty()) continue;
            if (facet == flagsFacet()) {
                for (FLAG toFlag : flagConverter.parse(toValue)) {
                    if (state.flags.containsKey(toFlag.name())) {
                        state.flags.remove(toFlag.name());
                        continue;
                    }
                    isConsistent = false;
                    out.format("Inconsistent change on bug %s: set flag missing: '%s'\n",
                               activity.cell(Fields.Activity.ENTITY_ID).longValue(), toFlag);
                }

                for (FLAG fromFlag : flagConverter.parse(fromValue)) {
                    if (state.flags.containsKey(fromFlag.name())) {
                        out.format("Inconsistent change on bug %s: flag unwarranted: '%s'\n",
                                   activity.cell(Fields.Activity.ENTITY_ID).longValue(),
                                   fromFlag);
                        isConsistent = false;
                    }
                    state.flags.put(fromFlag.name(), fromFlag);
                }

                List<FLAG> completeFromFlags = new LinkedList<FLAG>(state.flags.values());
                state.facets.put(facet, flagConverter.format(completeFromFlags));
                continue;
            }
            if (!toValue.equals(state.facets.get(facet))) {
                isConsistent = false;
                out.format(
                    "Inconsistent change on bug %s field %s: expected '%s' (but got '%s')\n",
                    activity.cell(Fields.Activity.ENTITY_ID).longValue(), facet.name().toLowerCase(),
                    state.facets.get(facet), toValue
                );
            }
            state.facets.put(facet, fromValue);
        }
        return isConsistent;
    }


    private void printReorderDebugInformation(E entity, List<Input.Row> candidates, State state) {
        final PrintStream out = System.out;
        out.format("\nREORDER ACTIVITIES (bug #%s)\n", entity.id());
        out.format("\nREORDER ACTIVITIES, #reordered: %d\n", candidates.size());
        out.format("\nREORDER ACTIVITIES, target:\n");
        for (FACET f : state.facets.keySet()) out.format("%s=%s \n", f, state.facets.get(f));
        out.format("\n");
    }


    /** Collect counts for up to four simultaneous activities. The index 0 is not used. */
    private final AtomicIntegerArray conflictCount = new AtomicIntegerArray(4);
    private final AtomicIntegerArray fallbackCount = new AtomicIntegerArray(4);
    public void printConflictCounts() {
        for (int i = 1; i < conflictCount.length(); ++i) {
            if (conflictCount.get(i) == 0) continue;
            System.out.format("%d simultaneous modifications: %d times", i, conflictCount.get(i));
            System.out.format(" (%d needed fallback ordering)\n", fallbackCount.get(i));
        }
    }

    protected abstract void updateFacetsAndMeasurements(E entity, Date now);
    protected abstract boolean isComputed(FACET facet);
    protected abstract FACET flagsFacet();

    protected abstract E get(Creation base, Input input) throws KettleValueException;
    protected abstract Creation base(Input input) throws KettleValueException;

    /**
     * If there is a persisted version of this entity, rebase it on that.
     * @return <tt>true</tt> if the entity has been rebased, <tt>false</tt> otherwise.
     */
    private boolean applyAnyPersistedState(final E entity) throws KettleStepException {
        final E persistentEntity = lookup.find(entity.id());
        if (persistentEntity == null) return false;
        entity.baseUpon(persistentEntity);
        return true;
    }

    /**
     * @const
     * @return The current state, from the entity table and referenced tables.
     */
    State stateFromEntityTable(E entity, Input.Row row) throws KettleValueException {
        // Use the "left leg" (latest bug state from bugzilla) to construct the "current" version.
        final EnumMap<FACET, String> facetState = entity.createFacets();
        final Map<String, FLAG> flagState = new HashMap<String, FLAG>();
        for (FACET facet : facetState.keySet()) {
            if (isComputed(facet)) continue;
            String value = row.cell(facet).stringValue();
            facetState.put(facet, value);
            if (facet == flagsFacet() && value != null) {
                for (FLAG flag : flagConverter.parse(value)) {
                    flagState.put(flag.name(), flag);
                }
            }
        }
        return new State(){{ facets = facetState; flags = flagState; }};
    }


    private String annotation(CharSequence maybeDetails) {
        StringBuilder annotation = new StringBuilder();
        annotation.append("IMPORTED").append(" at ").append(isoFormat.format(now));
        if (maybeDetails != null && maybeDetails.length() > 0) {
            annotation.append(" (").append(maybeDetails).append(")");
        }
        return annotation.toString();
    }


    private final Input input;
    private final Lookup<E, KettleStepException> lookup;
    private final Converter<List<FLAG>> flagConverter;

    /** The date the transformation is run. */
    private final Date now = new Date();
    private static final DateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm z");
    private final int safetyDeltaMs = 10;
}
