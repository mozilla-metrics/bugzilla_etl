package com.mozilla.bugzilla_etl.model;

import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import com.mozilla.bugzilla_etl.base.Assert;
import com.mozilla.bugzilla_etl.base.Pair;
import com.mozilla.bugzilla_etl.di.Converters;
import com.mozilla.bugzilla_etl.di.Converters.Converter;


/**
 * Generic versioned entity.
 * Something that has a unique ID, a creator, and versions.
 */
public abstract class Entity<E extends Entity<E, V, FACET>,
                             V extends Version<E, V, FACET>,
                             FACET extends Enum<FACET> & Field> implements Iterable<V> {

    protected static final long DAY = 24*60*60*1000;
    private static final boolean DEBUG_INCREMENTAL_UPDATE = false;
    protected final Long id;
    protected final String reporter;
    protected final Date creationDate;
    protected final LinkedList<V> versions;

    public Entity(Long id, String reporter, Date creationDate) {
        Assert.nonNull(id, reporter);
        this.id = id;
        this.reporter = reporter;
        this.creationDate = creationDate;
        versions = new LinkedList<V>();
    }

    public Long id() { return id; }
    public String reporter() { return reporter; }
    public Date creationDate() { return creationDate; }

    public int numVersions() { return versions.size(); }

    public Iterator<V> iterator() { return versions.iterator(); }

    public void prepend(V earlierVersion) {
        Assert.nonNull(earlierVersion);
        Assert.check(versions.isEmpty() || !versions.peek().from().before(earlierVersion.to()));
        versions.addFirst(earlierVersion);
    }

    public void append(V laterVersion) {
        Assert.nonNull(laterVersion);
        Assert.check(versions.isEmpty() || !versions.getLast().to().after(laterVersion.from()));
        versions.addLast(laterVersion);
    }

    public boolean isNew() {
        return versions.getFirst().persistenceState() == PersistenceState.NEW;
    }

    public abstract V latest(EnumMap<FACET, String> facets,
                             String creator, Date from, String annotation);

    /**
     * Steal all versions of the given entity and prepend them to this one.
     * Both entities must be for the same bugzilla id, the existing entity's
     * versions should be persisted already (hence "existing").
     * The last existing version might be changed during this process. In this
     * case it is marked as dirty.
     */
    public void baseUpon(final E existing) {
        Assert.check(id.equals(existing.id()));
        final LinkedList<V> existingVersions = existing.versions;

        if (DEBUG_INCREMENTAL_UPDATE) {
            System.out.print("\n");
            System.out.format("[REBASE  ] Checking #%d\n", id);
        }

        final V mostRecentExistingVersion = existingVersions.getLast();
        if (versions.getLast().from().before(mostRecentExistingVersion.from())) {
            System.out.format("Index version of bug #%d newer than version to import (OK).\n", id);
        }

        // Tell me a ton about this so I can make sure it works
        if (DEBUG_INCREMENTAL_UPDATE) {
            System.out.format("[REBASE >] %s\n", this);
            for (V version : this) System.out.format("[NEW     ] %s\n", version);
            System.out.format("[UPON >>>] %s\n", existing);
            for (V version : existing) System.out.format("[EXISTING] %s\n", version);
        }

        // Discard any versions we might have rebuilt locally that are also in
        // the existing bug. This can happen if an incremental update is run
        // for a start time that is overlapped by a previous update/import run.
        while (!versions.isEmpty() &&
               !versions.getFirst().from().after(mostRecentExistingVersion.from())) {
            if (DEBUG_INCREMENTAL_UPDATE) {
                System.out.format("[.DELETE ] %s\n", versions.getFirst());
            }
            versions.removeFirst();
        }

        ListIterator<V> reverse = existingVersions.listIterator(existingVersions.size());
        boolean isMostRecent = true;
        while (reverse.hasPrevious()) {
            V previous = reverse.previous();
            if (isMostRecent && !versions.isEmpty()) {
                previous = previous.update("", versions.getFirst().from());
            }
            isMostRecent = false;
            if (DEBUG_INCREMENTAL_UPDATE) {
                System.out.format("[.PREPEND] %s\n", previous);
            }
            prepend(previous);
        }

        if (DEBUG_INCREMENTAL_UPDATE) {
            for (V version : this) System.out.format("[RESULT  ] %s\n", version);
        }
    }




    /**
     * While modified_fields will just contain field names, changes is a
     * list of actual changes. For single value fields, it produces items
     * like this: "status:RESOLVED" for "FROM" values. The "TO" values
     * are stored in the individual facets anyway.
     *
     * For multivalue fields it produces elements like:
     * "-flags=previous-flag" "+keywords=new_keyword"
     *
     * @return a pair of strings. The first string is the facet "changes",
     *         the second is the facet "modified_fields".
     */
    protected Pair<String, String> changes(final EnumMap<FACET, String> fromFacets,
                                           final EnumMap<FACET, String> toFacets) {
        final List<String> changes = new LinkedList<String>();
        final List<String> modified = new java.util.LinkedList<String>();
        for (final FACET facet : fromFacets.keySet()) {
            if (!includeInChanges(facet)) continue;

            final String from = fromFacets.get(facet);
            final String to = toFacets.get(facet);
            if (equals(from, to)) continue;

            final String name = facet.name().toLowerCase();
            if (includeInModifiedFields(facet)) {
                modified.add(name);
            }

            final Converter<List<String>> csvConverter = new Converters.CsvConverter();
            if (isMultivalue(facet)) {
                List<String> fromItems = Collections.emptyList();
                List<String> toItems = Collections.emptyList();
                if (from != null) fromItems = csvConverter.parse(from);
                if (to != null) toItems = csvConverter.parse(to);
                final Set<String> fromLookup = new HashSet<String>(fromItems);
                final Set<String> toLookup = new HashSet<String>(toItems);
                for (final String item : fromItems) {
                    if (!toLookup.contains(item)) changes.add("-" + name + "=" + item);
                }
                for (final String item : toItems) {
                    if (!fromLookup.contains(item)) changes.add("+" + name + "=" + item);
                }
            }
            else  if (from != null && !from.equals("<empty>")) {
                changes.add(name + "=" + from);
            }
        }
        return new Pair<String, String>(Converters.CHANGES.format(changes),
                                        Converters.MODIFIED_FIELDS.format(modified));
    }

    public final boolean equals(Object a, Object b) {
        if (a == null) return b == null;
        return a.equals(b);
    }

    protected abstract boolean includeInModifiedFields(FACET facet);
    protected abstract boolean includeInChanges(FACET facet);
    protected abstract boolean isMultivalue(FACET field);
}