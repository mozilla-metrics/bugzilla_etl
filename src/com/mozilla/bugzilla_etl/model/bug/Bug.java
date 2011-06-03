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

import java.util.Date;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mozilla.bugzilla_etl.base.Assert;
import com.mozilla.bugzilla_etl.base.Pair;
import com.mozilla.bugzilla_etl.di.Converters;
import com.mozilla.bugzilla_etl.model.Entity;
import com.mozilla.bugzilla_etl.model.PersistenceState;
import com.mozilla.bugzilla_etl.model.Version;
import com.mozilla.bugzilla_etl.model.bug.BugFields.Facet;
import com.mozilla.bugzilla_etl.model.bug.BugFields.Measurement;


/** A bug with its invariant properties and all of its versions. */
public class Bug extends Entity<Bug, BugVersion, BugFields.Facet> {

    public Bug(Long id, String creator, Date creationDate) {
        super(id, creator, creationDate);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + id.hashCode();
        result = prime * result + reporter.hashCode();
        result = prime * result + versions.hashCode();
        return result;
    }

    @Override
    public boolean equals(final Object object) {
        if (object == this) return true;
        if (object == null) return false;
        if (!(object instanceof Bug)) return false;
        Bug other = (Bug) object;
        if (!id.equals(other.id)) return false;
        if (!reporter.equals(other.reporter)) return false;
        if (versions.size() != other.versions.size()) return false;
        return versions.equals(other.versions);
    }

    @Override
    public String toString() {
        final StringBuilder versionsVis = new StringBuilder(versions.size());
        for (final BugVersion version : versions) {
            switch (version.persistenceState()) {
                case SAVED: versionsVis.append('.'); break;
                case DIRTY: versionsVis.append('#'); break;
                case NEW: versionsVis.append('*'); break;
                default: Assert.unreachable();
            }
        }
        return String.format("{bug id=%s, reporter=%s, versions=%s (.saved #dirty *new)}",
                             id, reporter, versionsVis.toString());
    }

    /**
     * Traverse the bug history from past to present and figure out what the
     * value of individual computed facets and measurements should be.
     *
     * Flags are changed from their incremental representation to their full
     * representation.
     *
     * @param majorStatusTable Mapping from status names to major status names
     */
    public void updateFacetsAndMeasurements(final Map<String, String> majorStatusTable,
                                            final Date now) {

        // First version will have -1 days for previous_status, previous_major_status
        long number = 1;

        // Latest version will have -1 days for status, major_status
        // and no change for days_open_accumulated
        boolean isLatest;

        EnumMap<BugFields.Facet, String> previousFacets = createFacets();
        Date statusLastChanged = creationDate;
        Date majorStatusLastChanged = creationDate;
        long msInStatus = 0;
        long msInMajorStatus = 0;
        long msOpenAccumulated = 0;
        int timesReopened = 0;

        for (BugVersion version : this) {

            isLatest = version.to().after(now);

            final EnumMap<BugFields.Facet, String> facets = version.facets();
            final EnumMap<BugFields.Measurement, Long> measurements = version.measurements();

            String status = facets.get(BugFields.Facet.STATUS);
            String majorStatus = majorStatusTable.get(status);
            if (majorStatus == null) {
                status = helper.fixKnownBrokenStatus(id).name();
                majorStatus = helper.fixKnownBrokenMajorStatus(id).name();
            }

            // Previous status and major status are equal to the version before....
            if (previousFacets.get(BugFields.Facet.PREVIOUS_STATUS) != null) {
                facets.put(BugFields.Facet.PREVIOUS_STATUS,
                           previousFacets.get(BugFields.Facet.PREVIOUS_STATUS));
                if (previousFacets.get(BugFields.Facet.PREVIOUS_MAJOR_STATUS) != null) {
                    facets.put(BugFields.Facet.PREVIOUS_MAJOR_STATUS,
                               previousFacets.get(BugFields.Facet.PREVIOUS_MAJOR_STATUS));
                }
            }

            // ...unless something changes to this version.
            long previousStatusDays = -1;
            long previousMajorStatusDays = -1;
            if (number > 1 && !status.equals(previousFacets.get(BugFields.Facet.STATUS))) {
                facets.put(BugFields.Facet.PREVIOUS_STATUS, previousFacets.get(BugFields.Facet.STATUS));
                previousStatusDays = msInStatus/DAY;
                msInStatus = 0;
                statusLastChanged = version.from();
                if (!majorStatus.equals(previousFacets.get(BugFields.Facet.MAJOR_STATUS))) {
                    facets.put(BugFields.Facet.PREVIOUS_MAJOR_STATUS, previousFacets.get(BugFields.Facet.MAJOR_STATUS));
                    if (Status.valueOf(status) == Status.REOPENED) ++timesReopened;
                    previousMajorStatusDays = msInMajorStatus/DAY;
                    msInMajorStatus = 0;
                    majorStatusLastChanged = version.from();
                }
            }
            facets.put(BugFields.Facet.MAJOR_STATUS, majorStatus);
            facets.put(BugFields.Facet.STATUS_LAST_CHANGED_DATE,
                       Converters.DATE.format(statusLastChanged));
            facets.put(BugFields.Facet.MAJOR_STATUS_LAST_CHANGED_DATE,
                       Converters.DATE.format(majorStatusLastChanged));


            // Itemize the status whiteboard so that users do not have to worry about brackets.
            {
                final String whiteboard = facets.get(BugFields.Facet.STATUS_WHITEBOARD);
                final List<String> items = helper.whiteboardItems(whiteboard);
                facets.put(BugFields.Facet.STATUS_WHITEBOARD_ITEMS,
                           Converters.STATUS_WHITEBOARD_ITEMS.format(items));
            }

            // The modified fields are computed after all other facets have been processed.
            final Pair<String, String> changes = changes(previousFacets, facets);
            facets.put(BugFields.Facet.CHANGES, changes.first());
            facets.put(BugFields.Facet.MODIFIED_FIELDS, changes.second());

            // Compute remaining measurements
            final long duration = version.to().getTime() - version.from().getTime();
            msInStatus += duration;
            msInMajorStatus += duration;
            if (!isLatest && Status.Major.OPEN == Status.Major.valueOf(majorStatus)) {
                msOpenAccumulated += duration;
            }

            measurements.put(BugFields.Measurement.DAYS_IN_PREVIOUS_STATUS,
                             Long.valueOf(previousStatusDays));
            measurements.put(BugFields.Measurement.DAYS_IN_PREVIOUS_MAJOR_STATUS,
                             Long.valueOf(previousMajorStatusDays));
            measurements.put(BugFields.Measurement.DAYS_IN_STATUS,
                             Long.valueOf(isLatest ? -1 : msInStatus/DAY));
            measurements.put(BugFields.Measurement.DAYS_IN_MAJOR_STATUS,
                             Long.valueOf(isLatest ? -1 : msInMajorStatus/DAY));
            measurements.put(BugFields.Measurement.DAYS_OPEN_ACCUMULATED,
                             Long.valueOf(msOpenAccumulated/DAY));
            measurements.put(BugFields.Measurement.TIMES_REOPENED,
                             Long.valueOf(timesReopened));
            measurements.put(BugFields.Measurement.NUMBER,
                             Long.valueOf(number));

            previousFacets = facets;
            ++number;
        }

    }


    public BugVersion latest(EnumMap<BugFields.Facet, String> facets,
                             String creator, Date from, String annotation) {
        Assert.nonNull(facets, creator, from);
        return new BugVersion(this, facets,
                              new EnumMap<BugFields.Measurement, Long>(BugFields.Measurement.class),
                              creator, annotation, from, Version.TO_FUTURE, PersistenceState.NEW);
    }

    @SuppressWarnings("serial")
    public EnumMap<BugFields.Facet, String> createFacets() {
        return new EnumMap<BugFields.Facet, String>(BugFields.Facet.class) {{
            for (Facet facet : BugFields.Facet.values()) put(facet, null);
        }};
    }

    @SuppressWarnings("serial")
    public EnumMap<BugFields.Measurement, Long> createMeasurements() {
        return new EnumMap<BugFields.Measurement, Long>(BugFields.Measurement.class) {{
            for (Measurement measure : BugFields.Measurement.values()) put(measure, null);
        }};
    }



    private final UpdateHelper helper = new UpdateHelper();

    public static class UpdateHelper {

        private static final Pattern WHITEBOARD_ITEMIZER =
            Pattern.compile("\\[([^\\]]+)(?=\\])|(?:[\\s,\\]]+|^)([^\\s\\[]+|$)");

        public final List<String> whiteboardItems(final String statusWhiteboard) {
            final Matcher matcher = WHITEBOARD_ITEMIZER.matcher(statusWhiteboard);
            final LinkedList<String> results = new LinkedList<String>();
            while (matcher.find()) {
                for (int i = 1; i<=matcher.groupCount(); ++i) {
                    final String group = matcher.group(i);
                    if (group == null || group.equals("")) continue;
                    if (group.equals("(?)")) {
                        if (results.size() == 0) continue;
                        results.set(results.size() - 1, results.getLast() + "?");
                        continue;
                    }
                    results.add(group);
                }
            }
            return results;
        }

        // This happens for five very old bugs, so we just handle them by their known ids.
        public final Status fixKnownBrokenStatus(Long id) {
            // :BMO: This should go to a configuration file...
            switch ((int)id.longValue()) {
                case 11720: case 11721: case 20015: return Status.NEW;
                case 19936: case 19952:             return Status.CLOSED;
                default:
                    Assert.unreachable("Missing status for bug %s", id);
                    return null;
            }
        }

        public final Status.Major fixKnownBrokenMajorStatus(Long id) {
            // :BMO: This should go to a configuration file...
            switch ((int)id.longValue()) {
                case 11720: case 11721: case 20015: return Status.Major.OPEN;
                case 19936: case 19952:             return Status.Major.CLOSED;
                default:
                    Assert.unreachable("Missing status for bug %s", id);
                    return null;
            }
        }

    }

    @Override protected boolean includeInModifiedFields(BugFields.Facet facet) {
        return facet != BugFields.Facet.STATUS_WHITEBOARD_ITEMS;
    }

    @Override protected boolean includeInChanges(BugFields.Facet facet) {
        switch (facet) {
            case CHANGES:
            case MODIFIED_FIELDS:
            case STATUS_LAST_CHANGED_DATE:
            case MAJOR_STATUS_LAST_CHANGED_DATE:
                return false;
            default:
                return true;
        }
    }

    @Override protected boolean isMultivalue(BugFields.Facet facet) {
        switch (facet) {
            case KEYWORDS:
            case GROUPS:
            case FLAGS:
            case STATUS_WHITEBOARD_ITEMS:
                return true;
            default:
                return false;
        }
    }

}

