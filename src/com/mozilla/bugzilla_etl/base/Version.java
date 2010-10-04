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

package com.mozilla.bugzilla_etl.base;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumMap;

import org.apache.commons.lang.time.DateUtils;

import com.mozilla.bugzilla_etl.base.Fields.Facet;
import com.mozilla.bugzilla_etl.base.Fields.Measurement;

/**
 * A bug state in time.
 * Versions are mostly immutable, except for their facets and measurements which are modified by
 * {@link Bug#updateFacetsAndMeasurements()}.
 */
public class Version {

    /** Given a bug, create a new "latest" version for that bug. */
    public static Version latest(final Bug bug,
                                 final EnumMap<Facet, String> facets,
                                 final String author,
                                 final Date from,
                                 final String maybeAnnotation) {
        Assert.nonNull(bug, facets, author, from);
        return new Version(bug, facets, new EnumMap<Measurement, Long>(Measurement.class), author,
                           maybeAnnotation, from, theFuture, PersistenceState.NEW);
    }

    /** Helps to create fields for new versions. */
    public static EnumMap<Facet, String> createFacets() {
        return new EnumMap<Facet, String>(Facet.class);
    }

    /** Helps to create fields for new versions. */
    public static EnumMap<Measurement, Long> createMeasurements() {
        return new EnumMap<Measurement, Long>(Measurement.class);
    }

    /**
     * Create a new version from a complete set of information.
     */
    public Version(final Bug bug,
                   final EnumMap<Facet, String> facets,
                   final EnumMap<Measurement, Long> measurements,
                   final String author,
                   String maybeAnnotation,
                   final Date from,
                   final Date to,
                   final PersistenceState persistenceState) {
        Assert.nonNull(bug, facets, measurements, author, from, to, persistenceState);
        Assert.check(from.before(to));
        if (maybeAnnotation == null) maybeAnnotation = "";
        this.bug = bug;
        this.facets = facets.clone();
        this.measurements = measurements.clone();
        this.author = author;
        this.annotation = maybeAnnotation;
        this.from = from;
        this.to = to;
        this.persistenceState = persistenceState;
    }

    /**
     * @const
     * Create a new version that is valid until one second before this version becomes valid.
     * Measurements, computed facets and flags will still have to be computed by
     * {@link Bug#updateFacetsAndMeasurements(java.util.Map, Date)}.
     */
    public Version predecessor(final EnumMap<Facet, String> facets,
                               final String author,
                               final Date from,
                               String maybeAnnotation) {
        Assert.nonNull(author, facets, from);
        if (maybeAnnotation == null) maybeAnnotation = "";
        return new Version(this.bug, facets, Version.createMeasurements(), author,
                           maybeAnnotation, from, this.from, PersistenceState.NEW);
    }

    public Bug bug() { return bug; }
    public Date from() { return from; }
    public Date to() { return to; }
    public String annotation() { return annotation; }
    public String author() { return author; }
    public PersistenceState persistenceState() { return persistenceState; }
    public EnumMap<Facet, String> facets() { return facets; }
    public EnumMap<Measurement, Long> measurements() { return measurements; }

    /** Make an updated version of this version. */
    public Version update(String newAnnotation, Date newTo) {
        PersistenceState newState = PersistenceState.NEW;
        if (persistenceState == PersistenceState.SAVED) newState = PersistenceState.DIRTY;
        if (newAnnotation == null) newAnnotation = this.annotation;
        if (newTo == null) newTo = this.to;
        return new Version(bug, facets.clone(), measurements.clone(), author, newAnnotation, from,
                           newTo, newState);
    }

    /**
     * Compare if both versions encapsulate the same state.
     * The fields {@link #persistenceState()} and {@link #annotation()} are ignored.
     */
    public boolean equals(Object object) {
        if (object == this) return true;
        if (object == null) return false;
        if (!(object instanceof Version)) return false;
        final Version other = (Version) object;
        // Only compare the bug-ids (otherwise we have infinite recursion).
        if (!bug.id().equals(other.bug.id()))
            return false;
        if (!author.equals(other.author))
            return false;
        if (!from.equals(other.from))
            return false;
        if (!to.equals(other.to))
            return false;
        if (!facets.equals(other.facets))
            return false;
        if (!measurements.equals(other.measurements))
            return false;
        return true;
    }

    public String toString() {
        return String.format("{version from='%s', to='%s', persisted=%s, author=%s}",
                             format.format(from),
                             format.format(to),
                             persistenceState,
                             author);
    }

    private final Bug bug;
    private final EnumMap<Facet, String> facets;
    private final EnumMap<Measurement, Long> measurements;
    private final Date from;
    private final Date to;
    private final String author;
    /** Annotation on how this version was produced. Ignored when comparing for equality. */
    private final String annotation;
    /** Is this version in the database? Ignored when comparing for equality. */
    private final PersistenceState persistenceState;

    private static final DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS z");
    private static final Date theFuture;
    static {
        Date date = null;
        try { date = DateUtils.parseDate("2199-12-31", new String[]{"yyyy-MM-dd"}); }
        catch (ParseException e) { Assert.unreachable(); }
        theFuture = date;
    }

}

