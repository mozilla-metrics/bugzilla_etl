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

import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mozilla.bugzilla_etl.base.Converters.Converter;
import com.mozilla.bugzilla_etl.base.Fields.Facet;
import com.mozilla.bugzilla_etl.base.Fields.Measurement;

/** A bug with its invariant properties and all of its versions. */
public class Bug implements Iterable<Version> {

    public Bug(Long id, String reporter, Date creationDate) {
        Assert.nonNull(id, reporter);
        this.id = id;
        this.reporter = reporter;
        this.creationDate = creationDate;
        versions = new LinkedList<Version>();
    }

    private static final long DAY = 24*60*60*1000;
    private static final boolean DEBUG_INCREMENTAL_UPDATE = true;

    private final Long id;
    private final String reporter;
    private final Date creationDate;
    private final LinkedList<Version> versions;

    private final UpdateHelper helper = new UpdateHelper();

    public Long id() { return id; }
    public String reporter() { return reporter; }
    public Date creationDate() { return creationDate; }
    public int numVersions() { return versions.size(); }

    @Override
    public Iterator<Version> iterator() { return versions.iterator(); }

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

    /**
     * Steal all versions of the given bug and prepend them to this bug.
     * Both bugs must be for the same bugzilla bug id, the existing bug's versions should be
     * persistent.
     */
    public void baseUpon(final Bug existingBug) {
        Assert.check(id.equals(existingBug.id()));
        final LinkedList<Version> existingVersions = existingBug.versions;

        if (DEBUG_INCREMENTAL_UPDATE) {
            System.out.print("\n");
            System.out.format("[REBASE  ] Checking bug #%d\n", id);
        }

        final Version mostRecentExistingVersion = existingVersions.getLast();
        if (!versions.getLast().from().after(mostRecentExistingVersion.from())) {
            System.out.format("Repo version of bug #%d newer than version to import (OK).\n", id);
        }

        // Tell me a ton about this so I can make sure it works
        if (DEBUG_INCREMENTAL_UPDATE) {
            System.out.format("[REBASE >] %s\n", this);
            for (Version version : this) System.out.format("[NEW     ] %s\n", version);
            System.out.format("[UPON >>>] %s\n", existingBug);
            for (Version version : existingBug) System.out.format("[EXISTING] %s\n", version);
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

        ListIterator<Version> reverse = existingVersions.listIterator(existingVersions.size());
        boolean isMostRecent = true;
        while (reverse.hasPrevious()) {
            Version previous = reverse.previous();
            if (isMostRecent && !versions.isEmpty()) {
                previous = previous.update("", versions.getFirst().from());
            }
            isMostRecent = false;
            if (DEBUG_INCREMENTAL_UPDATE) System.out.format("[.PREPEND] %s\n", previous);
            prepend(previous);
        }

        if (DEBUG_INCREMENTAL_UPDATE) {
            for (Version version : this) System.out.format("[RESULT  ] %s\n", version);
        }
    }

    public void prepend(Version earlierVersion) {
        Assert.nonNull(earlierVersion);
        Assert.check(versions.isEmpty() || !versions.peek().from().before(earlierVersion.to()));
        versions.addFirst(earlierVersion);
    }

    public void append(Version laterVersion) {
        Assert.nonNull(laterVersion);
        Assert.check(versions.isEmpty() || !versions.getLast().to().after(laterVersion.from()));
        versions.addLast(laterVersion);
    }

    public boolean neverSaved() {
        return versions.getFirst().persistenceState() == PersistenceState.NEW;
    }

    @Override
    public String toString() {
        final StringBuilder versionsVis = new StringBuilder(versions.size());
        for (final Version version : versions) {
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
     * Traverse the bug history from past to present and figure out what the value of individual
     * computed facets and measurements should be.
     *
     * Flags are changed from their incremental representation to their full representation.
     *
     * @param majorStatusTable A mapping from status names to major status names.
     */
    public void updateFacetsAndMeasurements(final Map<String, String> majorStatusTable,
                                            final Date now) {

        // First version will have -1 days for previous_status, previous_major_status
        long number = 1;

        // Latest version will have -1 days for status, major_status
        // and no change for days_open_accumulated
        boolean isLatest;

        Map<Facet, String> previousFacets = Version.createFacets();
        Date statusLastChanged = creationDate;
        Date majorStatusLastChanged = creationDate;
        long msInStatus = 0;
        long msInMajorStatus = 0;
        long msOpenAccumulated = 0;
        int timesReopened = 0;

        for (Version version : this) {

            isLatest = version.to().after(now);

            final Map<Facet, String> facets = version.facets();
            final Map<Measurement, Long> measurements = version.measurements();

            String status = facets.get(Facet.STATUS);
            String majorStatus = majorStatusTable.get(status);
            if (majorStatus == null) {
                status = helper.fixKnownBrokenStatus(id).name();
                majorStatus = helper.fixKnownBrokenMajorStatus(id).name();
            }

            // Previous status and major status are equal to the version before....
            if (previousFacets.get(Facet.PREVIOUS_STATUS) != null) {
                facets.put(Facet.PREVIOUS_STATUS,
                           previousFacets.get(Facet.PREVIOUS_STATUS));
                if (previousFacets.get(Facet.PREVIOUS_MAJOR_STATUS) != null) {
                    facets.put(Facet.PREVIOUS_MAJOR_STATUS,
                               previousFacets.get(Facet.PREVIOUS_MAJOR_STATUS));
                }
            }

            // ...unless something changes to this version.
            long previousStatusDays = -1;
            long previousMajorStatusDays = -1;
            if (number > 1 && !status.equals(previousFacets.get(Facet.STATUS))) {
                facets.put(Facet.PREVIOUS_STATUS, previousFacets.get(Facet.STATUS));
                previousStatusDays = msInStatus/DAY;
                msInStatus = 0;
                statusLastChanged = version.from();
                if (!majorStatus.equals(previousFacets.get(Facet.MAJOR_STATUS))) {
                    facets.put(Facet.PREVIOUS_MAJOR_STATUS, previousFacets.get(Facet.MAJOR_STATUS));
                    facets.put(Facet.MAJOR_STATUS, majorStatus);
                    if (Status.valueOf(status) == Status.REOPENED) ++timesReopened;
                    previousMajorStatusDays = msInMajorStatus/DAY;
                    msInMajorStatus = 0;
                    majorStatusLastChanged = version.from();
                }
            }

                        facets.put(Facet.STATUS_LAST_CHANGED_DATE,
                       Converters.DATE.format(statusLastChanged));
            facets.put(Facet.MAJOR_STATUS_LAST_CHANGED_DATE,
                       Converters.DATE.format(majorStatusLastChanged));


            // Itemize the status whiteboard so that users do not have to worry about brackets.
            {
                final String whiteboard = facets.get(Facet.STATUS_WHITEBOARD);
                final List<String> items = helper.whiteboardItems(whiteboard);
                facets.put(Facet.STATUS_WHITEBOARD_ITEMS,
                           Converters.STATUS_WHITEBOARD_ITEMS.format(items));
            }

            // The modified fields are computed after all other facets have been processed.
            final Pair<String, String> changes = helper.changes(previousFacets, facets);
            facets.put(Facet.CHANGES, changes.first());
            facets.put(Facet.MODIFIED_FIELDS, changes.second());

            // Compute remaining measurements
            final long duration = version.to().getTime() - version.from().getTime();
            msInStatus += duration;
            msInMajorStatus += duration;
            if (!isLatest && Status.Major.OPEN == Status.Major.valueOf(majorStatus)) {
                msOpenAccumulated += duration;
            }

            measurements.put(Measurement.DAYS_IN_PREVIOUS_STATUS,
                             Long.valueOf(previousStatusDays));
            measurements.put(Measurement.DAYS_IN_PREVIOUS_MAJOR_STATUS,
                             Long.valueOf(previousMajorStatusDays));
            measurements.put(Measurement.DAYS_IN_STATUS,
                             Long.valueOf(isLatest ? -1 : msInStatus/DAY));
            measurements.put(Measurement.DAYS_IN_MAJOR_STATUS,
                             Long.valueOf(isLatest ? -1 : msInMajorStatus/DAY));
            measurements.put(Measurement.DAYS_OPEN_ACCUMULATED,
                             Long.valueOf(msOpenAccumulated/DAY));
            measurements.put(Measurement.TIMES_REOPENED,
                             Long.valueOf(timesReopened));
            measurements.put(Measurement.NUMBER,
                             Long.valueOf(number));

            previousFacets = facets;
            ++number;
        }

    }

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

        /**
         * While modified_fields will just contain field names, this is a list of actual changes.
         * For single value fields, it produces items like this:
         * "status:RESOLVED"
         * The "TO" values for single value fields are not included.
         * For multivalue fields it produces elements like:
         * "-flags=previous-flag"
         * "+keywords=new_keyword"
         */
        public Pair<String, String> changes(final Map<Facet, String> from,
                                            final Map<Facet, String> to) {
            final List<String> changes = new LinkedList<String>();
            final List<String> modified = new java.util.LinkedList<String>();
            for (final Facet facet : Facet.values()) {
                switch (facet) {
                    case MODIFIED_FIELDS:
                    case STATUS_LAST_CHANGED_DATE:
                    case MAJOR_STATUS_LAST_CHANGED_DATE:
                        continue;
                    default:
                        if (equals(from, to)) continue;
                }
                if (equals(from, to)) continue;

                final String key = facet.name().toLowerCase();
                if (facet != Facet.STATUS_WHITEBOARD_ITEMS) {
                    modified.add(key);
                }

                final Converter<List<String>> csvConverter = new Converters.CsvConverter();
                if (facet == Facet.KEYWORDS || facet == Facet.FLAGS) {
                    final List<String> fromItems = csvConverter.parse(from.get(facet));
                    final List<String> toItems = csvConverter.parse(from.get(facet));
                    final Set<String> fromLookup = new HashSet<String>(fromItems);
                    final Set<String> toLookup = new HashSet<String>(toItems);
                    for (final String item : fromItems) {
                        if (!toLookup.contains(item)) changes.add("-" + key + "=" + item);
                    }
                    for (final String item : toItems) {
                        if (!fromLookup.contains(item)) changes.add("+" + key + "=" + item);
                    }
                }
                else {
                    changes.add(key + "=" + from.get(facet));
                }
            }
            return new Pair<String, String>(Converters.CHANGES.format(changes),
                                            Converters.MODIFIED_FIELDS.format(modified));
        }

        public final boolean equals(Object a, Object b) {
            if (a == null) return b != null;
            return a.equals(b);
        }

        // This happens for five very old bugs, so we just handle them by their known ids.
        public final Status fixKnownBrokenStatus(Long id) {
            // :BMO: This should go to a configuration file so as to be mozilla-only.
            switch ((int)id.longValue()) {
                case 11720: case 11721: case 20015: return Status.NEW;
                case 19936: case 19952:             return Status.CLOSED;
                default:
                    Assert.unreachable("Missing status for bug %s", id);
                    return null;
            }
        }

        public final Status.Major fixKnownBrokenMajorStatus(Long id) {
            switch ((int)id.longValue()) {
                case 11720: case 11721: case 20015: return Status.Major.OPEN;
                case 19936: case 19952:             return Status.Major.CLOSED;
                default:
                    Assert.unreachable("Missing status for bug %s", id);
                    return null;
            }
        }

    }
}

