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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.Date;

import org.junit.Before;
import org.junit.Test;

import com.mozilla.bugzilla_etl.model.bug.Bug;

public class BaseUponTest extends VersionTest {

    private Bug initial;
    private Bug complete;

    @Before
    public void setUp() {
        complete = importOnly(longAgo, now);
        initial = importOnly(longAgo, importTime);
    }

    @Test
    public void testIncrementalUpdate1() {
        final Bug reference = importOnly(longAgo, updateTime1);
        final Bug updated1 = update(updateTime1, importTime, initial);
        assertBugsEquals(reference, updated1);
        assertFalse(complete.equals(updated1));
    }

    @Test
    public void testIncrementalUpdate2() {
        final Bug reference = importOnly(longAgo, updateTime2);
        final Bug updated2 = update(updateTime2, importTime, initial);
        assertBugsEquals(reference, updated2);
        assertFalse(complete.equals(updated2));
    }

    @Test
    public void testIncrementalUpdate3() {
        final Bug reference = importOnly(longAgo, updateTime3);
        final Bug updated3 = update(updateTime3, importTime, initial);
        assertBugsEquals(reference, updated3);
        assertEquals(complete, updated3);
    }

    @Test
    public void testIncrementalUpdate1to2() {
        final Bug updated1    = update(updateTime1, importTime,  initial);
        final Bug updated1to2 = update(updateTime2, updateTime1, updated1);
        final Bug updated2    = update(updateTime2, importTime,  initial);
        assertBugsEquals(updated2, updated1to2);
        assertFalse(complete.equals(updated1to2));
    }

    @Test
    public void testIncrementalUpdate1to3() {
        final Bug updated1    = update(updateTime1, importTime,  initial);
        final Bug updated1to3 = update(updateTime3, updateTime1, updated1);
        final Bug updated3    = update(updateTime3, importTime,  initial);
        assertBugsEquals(updated3, updated1to3);
        assertEquals(complete, updated1to3);
    }

    @Test
    public void testIncrementalUpdate2to3() {
        final Bug updated2    = update(updateTime2, importTime,  initial);
        final Bug updated2to3 = update(updateTime3, updateTime2, updated2);
        final Bug updated3    = update(updateTime3, importTime,  initial);
        assertBugsEquals(updated3, updated2to3);
        assertEquals(complete, updated2to3);
    }

    @Test
    public void testOverlappingUpdate() {
        final Bug imported3   = importOnly(longAgo, updateTime3);
        final Bug imported2   = importOnly(longAgo, updateTime2);
        final Bug updated1to3 = update(updateTime3, updateTime1, imported2);
        assertBugsEquals(imported3, updated1to3);
        assertFalse(imported3.equals(imported2));
    }

    private Bug update(Date end, Date start, Bug existingBug) {
        Bug newBug = simulateImport(start, end);
        newBug.baseUpon(existingBug);
        newBug.updateFacetsAndMeasurements(majorStatusTable, end);
        return newBug;
    }
}
