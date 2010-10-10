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

/**
 * Test import rebasing.
 * It all makes sense after visiting drhorrible.com
 */
package com.mozilla.bugzilla_etl.base;


import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.TimeZone;

import com.mozilla.bugzilla_etl.base.Fields.Facet;

import static com.mozilla.bugzilla_etl.base.Fields.Facet.*;
import static org.junit.Assert.*;

/**
 * @author michael
 */
public abstract class VersionTest {

    private static final TimeZone pacificTimeZone = TimeZone.getTimeZone("America/Los_Angeles");

    private static final DateFormat iso8601 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS z");

    enum Cast {
        BILLY("billy@example.com"),
        MOIST("moist@example.com"),
        PENNY("penny@example.com"),
        JOHNNY("johnny.snow@example.com"),
        BADHRSE("bad.horse@example.com"),
        HAMMER("cptn.hammer@example.com"),
        HRRBLE("dr.hrrble@example.com"),
        NOBODY("nobody@mozilla.org");
        Cast(String addy) { this.addy = addy; }
        public final String addy;
    }

    class State {
        final String author;
        final Date date;
        final EnumMap<Facet, String> state;
        State(Cast author, Calendar cal, EnumMap<Facet, String> state) {
            this.author = author.addy;
            this.date = cal.getTime();
            this.state = state.clone();
        }
    }

    protected final Map<String, String> majorStatusTable = new java.util.HashMap<String, String>();

    /** The bug id as "extracted" from a record in the bugs table. */
    private final Long BUG_ID = 4711L;
    /** The bug reporter as "extracted" from a record in the bugs table. */
    private final String REPORTER = Cast.BILLY.name();

    /** Describes the states either found looking at the bugs table, or rebuilding activities. */
    protected final List<State> states;

    protected final Date longAgo;
    protected final Date now;
    protected final Date importTime;
    protected final Date updateTime1;
    protected final Date updateTime2;
    protected final Date updateTime3;

    protected enum Instant {
        SOME_TIME_AGO, SOME_TIME_AGO_2, SOME_TIME_AGO_3,
        LAUNDRY_DAY,
        BAD_HORSE_CALL,
        HEIST_DAY, SECOND_BAD_HORSE_CALL,
        FROZEN_YOGHURT,
        BRAND_NEW_DAY, BRAND_NEW_DAY_2, /* BRAND_NEW_DAY_3, */
        MURDER, WRAPUP
    }

    /**
     * Prepare the test with a lot of predefined ready-to-use history information.
     */
    public VersionTest() {
        final Calendar calendar = Calendar.getInstance(pacificTimeZone);
        calendar.set(1971,  1,  1,  0,  0,  0);
        calendar.clear(Calendar.MILLISECOND);
        longAgo = calendar.getTime();

        majorStatusTable.put("UNCONFIRMED", "OPEN");
        majorStatusTable.put("NEW",         "OPEN");
        majorStatusTable.put("ASSIGNED",    "OPEN");
        majorStatusTable.put("REOPENED",    "OPEN");
        majorStatusTable.put("RESOLVED",    "CLOSED");
        majorStatusTable.put("VERIFIED",    "CLOSED");
        majorStatusTable.put("CLOSED",      "CLOSED");

        EnumMap<Fields.Facet, String> facets =
            new java.util.EnumMap<Fields.Facet, String>(Fields.Facet.class);

        final State[] defs = new State[Instant.values().length];

        // INITIAL IMPORT
        {
            // THREE REVISIONS WITH SAME TIMESTAMP:
            // 1)
            facets.put(ASSIGNED_TO, Cast.BILLY.addy);
            facets.put(COMPONENT, "Freeze Ray");
            facets.put(FLAGS, "");
            facets.put(KEYWORDS, "stops world");
            facets.put(MAJOR_STATUS, "");
            facets.put(OPSYS, "wonderflonium OS");
            facets.put(PREVIOUS_MAJOR_STATUS, "");
            facets.put(PREVIOUS_STATUS, "");
            facets.put(PRIORITY, "P4");
            facets.put(PRODUCT, "Evil League of Evil Membership");
            facets.put(RESOLUTION, "<none>");
            facets.put(SEVERITY, "Major");
            facets.put(STATUS, "UNCONFIRMED");
            facets.put(STATUS_WHITEBOARD, "");
            facets.put(TARGET_MILESTONE, "pretty soon");
            facets.put(VERSION, "0.5");
            calendar.set(2010,  7,  1, 22, 22, 22);
            defs[Instant.SOME_TIME_AGO.ordinal()] = new State(Cast.BILLY, calendar, facets);

            // 2)
            facets.put(FLAGS, "death-ray?");
            defs[Instant.SOME_TIME_AGO_2.ordinal()] = new State(Cast.MOIST, calendar, facets);

            // 3)
            facets.put(FLAGS, "ice-beam?");
            defs[Instant.SOME_TIME_AGO_3.ordinal()] = new State(Cast.NOBODY, calendar, facets);

            facets.put(STATUS, "NEW");
            facets.put(FLAGS, "blocking-worldchange+");
            facets.put(FLAGS, "blocking-worldchange+, ice-beam-, death-ray-");
            facets.put(PRIORITY, "P2");
            calendar.set(2010,  7,  2, 17, 01, 40);
            defs[Instant.LAUNDRY_DAY.ordinal()] = new State(Cast.BILLY, calendar, facets);

            facets.put(STATUS_WHITEBOARD, "needs-heist");
            calendar.set(2010,  7,  3, 10, 11,  5);
            defs[Instant.BAD_HORSE_CALL.ordinal()] = new State(Cast.BADHRSE, calendar, facets);

            calendar.set(2010,  7,  4,  0,  0,  0);
            importTime = calendar.getTime();
        }

        // INCREMENTAL UPDATE 1
        {
            // TWO REVISIONS WITH SAME TIMESTAMP:
            // 1)
            facets.put(STATUS, "RESOLVED");
            facets.put(RESOLUTION, "FIXED");
            calendar.set(2010,  7,  5, 10, 11, 30);
            defs[Instant.HEIST_DAY.ordinal()] = new State(Cast.BILLY, calendar, facets);

            // 2)
            facets.put(STATUS, "REOPENED");
            facets.put(RESOLUTION, "<none>");
            facets.put(STATUS_WHITEBOARD, "needs-murder");
            facets.put(FLAGS, "blocking-worldchange+, ice-beam-, death-ray?");
            defs[Instant.SECOND_BAD_HORSE_CALL.ordinal()] = new State(Cast.BADHRSE, calendar, facets);

            calendar.set(2010,  7,  6,  0,  0,  0);
            updateTime1 = calendar.getTime();
        }

        // INCREMENTAL UPDATE 2
        {
            facets.put(PRIORITY, "P5");
            facets.put(TARGET_MILESTONE, "<none>");
            calendar.set(2010,  7,  7, 16, 30,  0);
            defs[Instant.FROZEN_YOGHURT.ordinal()] = new State(Cast.PENNY, calendar, facets);

            // TWO REVISIONS WITH SAME TIMESTAMP
            // 1)
            facets.put(PRIORITY, "P1");
            calendar.set(2010,  7,  9, 10, 23,  5);
            defs[Instant.BRAND_NEW_DAY.ordinal()] = new State(Cast.HAMMER, calendar, facets);

            // 2)
            facets.put(TARGET_MILESTONE, "next tuesday");
            facets.put(SEVERITY, "Critical");
            defs[Instant.BRAND_NEW_DAY_2.ordinal()] = new State(Cast.HRRBLE, calendar, facets);

            calendar.set(2010,  7, 10, 0,  0,  0);
            updateTime2 = calendar.getTime();
        }


        /// REVISION THAT IS INTRODUCED & BACKDATED UPDATE, TO A DATE BEFORE THAT UPDATE
        /// making it the third revision on that timestamp.
        ///
        /// Commented out because this is something we'll need to think about some more.
        /// Fortunately we do not have this kind of crazy thing in BMO.
        ///
        /// state.put(FLAGS, "blocking-worldchange+, ice-beam-, death-ray+");
        /// calendar.set(2010,  7,  9, 10, 23,  5);
        /// // 3)
        /// versionDefinitions.set(Instant.BRAND_NEW_DAY_3.ordinal(),
        ///                        new VersionDefinition(Cast.BADHRSE, calendar, state));

        // INCREMENTAL UPDATE 3
        {
            facets.put(STATUS, "RESOLVED");
            facets.put(RESOLUTION, "FIXED");
            calendar.set(2010,  7, 14, 10, 19,  5);
            defs[Instant.MURDER.ordinal()] = new State(Cast.HRRBLE, calendar, facets);

            facets.put(STATUS_WHITEBOARD, "");
            facets.put(STATUS, "VERIFIED");
            calendar.set(2010,  7, 14, 22, 35, 47);
            defs[Instant.WRAPUP.ordinal()] = new State(Cast.BADHRSE, calendar, facets);

            calendar.set(2010,  7, 20, 0,  0,  0);
            updateTime3 = calendar.getTime();
        }

        calendar.set(2010,  7, 28, 0,  0,  0);
        now = calendar.getTime();

        states = Arrays.asList(defs);
    }

    protected void assertBugsEquals(Bug expected, Bug actual) {
        final Iterator<Version> as = expected.iterator(), bs = actual.iterator();
        for (int i = 1; as.hasNext() && bs.hasNext(); ++i) {
            final Version vExpected = as.next();
            final Version vActual = bs.next();
            if (vExpected.equals(vActual)) continue;
            System.out.format("\n\nComparison of bugs failed due to mismatch at version %d.\n", i);
            System.out.format("\nExpected versions:\n");
            int j = 0;
            for (Version vE : expected) System.out.format("%2d %s\n", ++j, vE);
            System.out.format("\nActual versions:\n");
            j = 0;
            for (Version vA : actual) System.out.format("%2d %s\n", ++j, vA);
            fail();
        }
        if (as.hasNext()) Assert.unreachable("Too few versions! Next should be: %s", as.next());
        if (bs.hasNext()) Assert.unreachable("Too many versions! Superflouus: %s", bs.next());
        assertEquals(expected, actual);
    }


    /** Get bug at time of initial import */
    protected Bug importOnly(Date start, Date end) {
        final Bug bug = simulateImport(start, end);
        bug.updateFacetsAndMeasurements(majorStatusTable, end);
        return bug;
    }

    /** Simulate a rebuild during an incremental import. */
    protected Bug simulateImport(final Date start, final Date end) {
        System.out.format("<import \n   from=%s\n     to=%s\n",
                          iso8601.format(start), iso8601.format(end));

        final ListIterator<State> iterator =
            states.listIterator(states.size());
        Bug bug = null;
        Version leastRecentChange = null;

        while (iterator.hasPrevious()) {
            final State def = iterator.previous();
            if (def.date.after(end)) continue;
            if (def.date.before(start)) break;
            if (bug == null || leastRecentChange == null) {
                bug = new Bug(BUG_ID, REPORTER);
                leastRecentChange = Version.latest(bug, def.state, def.author, def.date, "first");
                bug.prepend(leastRecentChange);
                continue;
            }
            leastRecentChange = leastRecentChange.predecessor(def.state, def.author, def.date, "");
            bug.prepend(leastRecentChange);
        }

        System.out.format("    bug=%s />\n\n", bug);
        return bug;
    }

}
