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

package com.mozilla.bugzilla_etl.model.bug;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumMap;

import org.apache.commons.lang.time.DateUtils;

import com.mozilla.bugzilla_etl.base.Assert;
import com.mozilla.bugzilla_etl.model.PersistenceState;
import com.mozilla.bugzilla_etl.model.Version;


/**
 * A bug state in time.
 * Versions are mostly immutable, except for their facets and measurements which are modified by
 * {@link Bug#updateFacetsAndMeasurements()}.
 */
public class BugVersion extends Version<Bug, BugVersion, BugFields.Facet> {

    /**
     * @const
     * Create a new version that is valid until this version becomes valid.
     * Measurements, computed facets and flags will still have to be computed by
     * {@link Bug#updateFacetsAndMeasurements(java.util.Map, Date)}.
     */
    public BugVersion predecessor(final EnumMap<BugFields.Facet, String> facets,
                                  final String author,
                                  final Date from,
                                  String maybeAnnotation) {
        Assert.nonNull(author, facets, from);
        if (maybeAnnotation == null) maybeAnnotation = "";
        return new BugVersion(this.entity, facets, entity.createMeasurements(),
                              author, maybeAnnotation, from, this.from,
                              PersistenceState.NEW);
    }

    /**
     * Create a new version from a complete set of information.
     */
    public BugVersion(final Bug bug,
                      final EnumMap<BugFields.Facet, String> facets,
                      final EnumMap<BugFields.Measurement, Long> measurements,
                      final String author,
                      String maybeAnnotation,
                      final Date from,
                      final Date to,
                      final PersistenceState persistenceState) {
        super(bug, author, maybeAnnotation, from, to, persistenceState);
        Assert.nonNull(facets, measurements);
        if (!from.before(to)) {
            // :BMO:
            // There is was a timezone bug in bugzilla. This is how I understand the problem:
            // Bugzilla read and wrote datetime fields as if they are using the local timezone
            // (PDT for BMO), while inside of MySQL all datetime fields are UTC, so that an
            // earlier instant always corresponds to a lower timestamp.
            // I am really not sure why the daylight savings time issues with certain bugs (58377,
            // 176975) are aligned with the European DST switch (USA is November, while they are in
            // October). Because the behavior is so fuzzy and affects exactly two older bugs, the
            // solution is hardcoded here for now.
            switch (bug.id().intValue()) {
                case  58377:
                case 176975:
                    final Date oneHourEarlier = DateUtils.addHours(from, -1);
                    from.setTime(oneHourEarlier.getTime());
                    System.out.format("Time mismatch on #%s: Moved FROM back in time!\n", bug.id());
                    break;
                default:
                    Assert.unreachable("Faulty expiration range on #%s! DST bug? From: %s, to: %s",
                                       bug, from, to);
            }
        }
        if (maybeAnnotation == null) maybeAnnotation = "";
        this.facets = facets.clone();
        this.measurements = measurements.clone();
    }

    public EnumMap<BugFields.Facet, String> facets() { return facets; }
    public EnumMap<BugFields.Measurement, Long> measurements() { return measurements; }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + annotation.hashCode();
        result = prime * result + author.hashCode();
        result = prime * result + entity.hashCode();
        result = prime * result + facets.hashCode();
        result = prime * result + from.hashCode();
        result = prime * result + measurements.hashCode();
        result = prime * result + persistenceState.hashCode();
        result = prime * result + to.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object object) {
        if (object == this) return true;
        if (object == null) return false;
        if (!(object instanceof BugVersion)) return false;
        final BugVersion other = (BugVersion) object;
        // Only compare the bug-ids (otherwise we have infinite recursion).
        if (!entity.id().equals(other.entity.id()))
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

    /** Make an updated version of this version. */
    public BugVersion update(String newAnnotation, Date newTo) {
        PersistenceState newState = PersistenceState.NEW;
        if (persistenceState == PersistenceState.SAVED) newState = PersistenceState.DIRTY;
        if (newAnnotation == null) newAnnotation = this.annotation;
        if (newTo == null) newTo = this.to;
        return new BugVersion(entity, facets.clone(), measurements.clone(), author, newAnnotation, from,
                           newTo, newState);
    }

    @Override
    public String toString() {
        return String.format("{version from='%s', to='%s', persisted=%s, author=%s}",
                             format.format(from),
                             format.format(to),
                             persistenceState,
                             author);
    }

    final EnumMap<BugFields.Facet, String> facets;
    final EnumMap<BugFields.Measurement, Long> measurements;

    protected static final Date theFuture;
    private static final DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS z");
    static {
        Date date = null;
        try { date = DateUtils.parseDate("2199-12-31", new String[]{"yyyy-MM-dd"}); }
        catch (ParseException e) { Assert.unreachable(); }
        theFuture = date;
    }

}

