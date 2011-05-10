package com.mozilla.bugzilla_etl.model;

import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;

import com.mozilla.bugzilla_etl.base.Assert;


/**
 * Generic versioned entity.
 * Something that has a unique ID, a creator, and versions.
 */
public abstract class Entity<E extends Entity<E, V>,
                             V extends Version<E, V>> implements Iterable<V> {

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

    /**
     * Steal all versions of the given entity and prepend them to this one.
     * Both entities must be for the same bugzilla id, the existing entity's
     * versions should be persisted already (hence "existing").
     */
    public void baseUpon(final E existing) {
        Assert.check(id.equals(existing.id()));
        final LinkedList<V> existingVersions = existing.versions;

        if (DEBUG_INCREMENTAL_UPDATE) {
            System.out.print("\n");
            System.out.format("[REBASE  ] Checking bug #%d\n", id);
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

        // Discard any versions we might have rebuilt locally that are also in the existing bug.
        // this can happen if an incremental update is run for a start time that was already
        // included in a (incremental) import.
        //
        while (!versions.isEmpty() &&
               !versions.getFirst().from().after(mostRecentExistingVersion.from())) {
            if (DEBUG_INCREMENTAL_UPDATE) System.out.format("[.DELETE ] %s\n", versions.getFirst());
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
            if (DEBUG_INCREMENTAL_UPDATE) System.out.format("[.PREPEND] %s\n", previous);
            prepend(previous);
        }

        if (DEBUG_INCREMENTAL_UPDATE) {
            for (V version : this) System.out.format("[RESULT  ] %s\n", version);
        }
    }

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

    public boolean neverSaved() {
        return versions.getFirst().persistenceState() == PersistenceState.NEW;
    }

}