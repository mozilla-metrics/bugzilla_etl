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

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicIntegerArray;

import com.mozilla.bugzilla_etl.model.Entity;


/** Special purpose counters for step statistics and diagnostics. */
public class Counter {

    public enum Item {

        NEW_ZERO(true, 0, "new with 0 activity"),
        OLD_ZERO(false, 0, "old with 0 activity"),
        NEW_LOW(true, 5, "new with 1-5 activity"),
        OLD_LOW(false, 5, "old with 1-5 activity"),
        NEW_MEDIUM(true, 10, "new with 6-10 activity"),
        OLD_MEDIUM(false, 10, "old with 6-10 activity"),
        NEW_HIGH(true, Integer.MAX_VALUE, "new with 10+ activity"),
        OLD_HIGH(false, Integer.MAX_VALUE, "old with 10+ activity"),
        NEW_TOTAL(true, -1, "New"),
        OLD_TOTAL(false, -1, "Old");

        boolean isNew;
        int threshold;
        String description;
        Item(boolean isNew, int threshold, String description) {
            this.isNew = isNew;
            this.threshold = threshold;
            this.description = description;
        }

    }

    public Counter(final String label) {
        Assert.nonNull(label);
        this.label = label;
    }

    public void count(Entity<?, ?, ?> entity, boolean isNew) {
        final int n = entity.numVersions();
        if (isNew) counters.getAndIncrement(Item.NEW_TOTAL.ordinal());
        else counters.getAndIncrement(Item.OLD_TOTAL.ordinal());
        for (Item item : Item.values()) {
            if (isNew == item.isNew && item.threshold >= n-1) {
                counters.getAndIncrement(item.ordinal());
                break;
            }
        }
    }

    /** Manually update a specific item, also incrementing totals. */
    public void increment(Item item) {
        Assert.check(item != Item.NEW_TOTAL, item != Item.OLD_TOTAL);
        if (item.isNew) {
            counters.getAndIncrement(Item.NEW_TOTAL.ordinal());
        }
        else {
            counters.getAndIncrement(Item.OLD_TOTAL.ordinal());
        }
        counters.getAndIncrement(item.ordinal());
    }

    public void print() {
        final StringBuilder format = new StringBuilder("%n%nCOUNTER >> %s << %n");
        final List<Object> arguments = new LinkedList<Object>();
        arguments.add(label);
        for (Item item : Item.values()) {
            format.append("%-23s:%,10d%n");
            arguments.add(item.description);
            arguments.add(counters.get(item.ordinal()));
        }
        System.err.format(format.toString(), arguments.toArray());
    }

    private final String label;

    private final AtomicIntegerArray counters = new AtomicIntegerArray(11);

}