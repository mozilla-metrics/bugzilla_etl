package com.mozilla.bugzilla_etl.model;

import java.text.ParseException;
import java.util.Date;
import java.util.EnumMap;

import org.apache.commons.lang.time.DateUtils;

import com.mozilla.bugzilla_etl.base.Assert;


public abstract class Version<E extends Entity<E, V, FACET>,
                              V extends Version<E, V, FACET>,
                              FACET extends Enum<FACET> & Field> {

    protected final E entity;

    public Version(E entity, String author, String maybeAnnotation, Date from,
                   Date to, PersistenceState persistenceState) {
        Assert.nonNull(entity, author, from, to, persistenceState);
        this.entity = entity;
        this.author = author;
        this.annotation = maybeAnnotation;
        this.from = from;
        this.to = to;
        this.persistenceState = persistenceState;
    }


    /**
     * @const
     * Create a new version that is valid until this version becomes valid.
     * Measurements, computed facets and flags will still have to be computed.
     */
    public abstract V predecessor(final EnumMap<FACET, String> facets,
                                  final String author,
                                  final Date from,
                                  String maybeAnnotation);

    public E entity() { return entity; }

    public Date from() { return from; }

    public Date to() { return to; }

    public String annotation() { return annotation; }

    public String author() { return author; }

    public PersistenceState persistenceState() { return persistenceState; }

    /**
     * Create a modified version based on this version.
     * This is needed to expire the currently valid version when a new version
     * is added.
     */
    public abstract V update(String newAnnotation, Date newTo);

    protected final Date from;
    protected final Date to;
    protected final String author;
    /** Annotation on how this version was produced. Ignored when comparing for equality. */
    protected final String annotation;
    /** Is this version in the database? Ignored when comparing for equality. */
    protected final PersistenceState persistenceState;


    public static final Date TO_FUTURE;
    static {
        Date date = null;
        try { date = DateUtils.parseDate("2199-12-31", new String[]{"yyyy-MM-dd"}); }
        catch (ParseException e) { Assert.unreachable(); }
        TO_FUTURE = date;
    }
}