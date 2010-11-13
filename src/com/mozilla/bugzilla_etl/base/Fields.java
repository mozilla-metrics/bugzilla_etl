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

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * This collects all fields we are dealing with in a central place.
 * <ul>
 * <li>Not all of the fields are necessarily used as field types in lily (for those fields see
 *     {@link com.mozilla.bugzilla_etl.lily.Types}).</li>
 * <li>Not all of the fields necessarily occur as columns in bugzilla. These have their column
 *     name set to null.
 */
public class Fields {

    public enum Family { FLAG, USER, BUG, VERSION, FACET, MEASUREMENT }

    public interface Field {
        Family family();
        String columnName();
    }

    /**
     * Fields to import flags into the flag record type.
     * Because we do not want multilevel links into the flags table, we have each flag three
     * times (once for requested, once for approved, once for denied).
     *
     * So, while bugzilla decides between flag and flagtype, we have only the flagtype (but three
     * times as many).
     */
    public enum Flag implements Field {
        /** The flag type ID in the operational database. */
        ID,
        NAME,
        STATUS;

        @Override
        public Family family() { return Family.FLAG; }
        
        @Override
        public String columnName() { return columnName; }
        
        String columnName;
        Flag() { columnName = name().toLowerCase(); }
    }

    /** Fields to import users into the user record type. */
    public static enum User implements Field {
        ID("user_id"),
        CREATION_DATE,
        EMAIL,
        COMPONENTS,
        PRODUCTS,
        NICK;

        public String columnName;
        
        @Override
        public String columnName() { return columnName; }
        
        @Override
        public Family family() { return Family.USER; }
        
        User(String name) { columnName = name; }
        User() { columnName = name().toLowerCase(); }
    }

    /** Fields that provide (meta) data about a bug. All are non-versioned. */
    public static enum Bug implements Field {
        ID("bug_id"),
        REPORTED_BY,
        CREATION_DATE;

        public String columnName;
        
        @Override
        public String columnName() { return columnName; }
        
        @Override
        public Family family() { return Family.BUG; }
        
        Bug(String name) { columnName = name; }
        Bug() { columnName = name().toLowerCase(); }
    }

    /**
     * Fields that provide (meta) data about an activity. These also serve as facets.
     * All of them come directly from the activities table in Bugzilla.
     */
    public static enum Version implements Field {
        BUG_ID("activity_bug_id"),
        MODIFIED_BY,
        MODIFICATION_DATE,
        EXPIRATION_DATE,
        ANNOTATION,
        PERSISTENCE_STATE;

        public String columnName;
        
        @Override
        public String columnName() { return columnName; }
        
        @Override
        public Family family() { return Family.VERSION; }
        
        Version(String name) { columnName = name; }
        Version() { columnName = name().toLowerCase(); }
    }

    /**
     * Simple fields of bugs that can be changed on activity basis in a "from"/"to" fashion.
     * Where available in bugzilla, they come from the bug state and from the "what changed" in
     * the activities table.
     *
     * Computed fields are not part of the input from the online Bugzilla database, but are 
     * generated by {@link com.mozilla.bugzilla_etl.base.Bug#updateFacetsAndMeasurements} during 
     * the version rebuilding. 
     */
    public static enum Facet implements Field {
        ASSIGNED_TO,
        COMPONENT,
        FLAGS,
        KEYWORDS,
        OPSYS,
        MAJOR_STATUS(true),
        MAJOR_STATUS_LAST_CHANGED_DATE(true),
        MODIFIED_FIELDS(true),
        PREVIOUS_MAJOR_STATUS(true),
        PREVIOUS_STATUS(true),
        PRIORITY,
        PRODUCT,
        RESOLUTION,
        SEVERITY,
        STATUS,
        STATUS_LAST_CHANGED_DATE(true),
        STATUS_WHITEBOARD,
        TARGET_MILESTONE,
        VERSION;

        public static enum Column { LATEST, FROM, TO, RESULT }
        public final Map<Facet.Column, String> columnNames;
        
        @Override
        public String columnName() { return columnNames.get(Column.RESULT); }
        
        @Override
        public Family family() { return Family.FACET; }
        
        public final boolean isComputed;

        /**
         * If initialized with a column name of <tt>null</tt>, this facet is computed and not
         * present in activities input.
         * @param columnName
         */
        Facet(boolean isComputed) {
            this.isComputed = isComputed;
            Map<Facet.Column, String> names = new EnumMap<Column, String>(Facet.Column.class);
            final String columnName= name().toLowerCase();
            if (!isComputed) {
                names.put(Column.LATEST, columnName);
                names.put(Column.FROM, columnName + "_from");
                names.put(Column.TO, columnName + "_to");
            }
            names.put(Column.RESULT, columnName);
            columnNames = Collections.unmodifiableMap(names);
        }

        Facet() {
            this(false);
        }
    }

    /**
     * Measurements (facts) are computed after all versions of a bug have been constructed.
     * Day counts are measured at the end (valid to) of the individual versions.
     *
     * When a measurement is not meaningful for a bug version, it has the value -1 by convention.
     * (:TODO: there is no support for NULL in LilyCMS, maybe we could introduce it to SOLR using a
     * custom formatter though?)
     * 
     * All measurements are computed by {@link Bug#updateFacetsAndMeasurements}
     */
    public static enum Measurement implements Field {
        
        /**
         * How long has the bug been OPEN/CLOSED, also counting the current version?
         * Useful to get an impression on how long the "current" bugs have been open.
         *
         * For the latest version of a bug, this field is -1 to indicate that it is not available
         * (yet). All we know is that the true value will be *at least* NOW-modification_date.
         *
         * So whenever you get a min of -1 in a solr stats query, you might want to exclude the
         * current bug state (expiration_date:[* TO NOW]).
         */
        DAYS_IN_MAJOR_STATUS,

        /**
         * How long has the bug been in the current status, also counting the current version?
         * This -1 for the latest bug version (see above).
         */
        DAYS_IN_STATUS,

        /**
         * How long has the bug been OPENED before being CLOSED or CLOSED before being (re-)OPENed.
         * Use it to obtain the (average) time bugs stay OPENED before being closed.
         *
         * This is -1 until there actually is a status change.
         */
        DAYS_IN_PREVIOUS_MAJOR_STATUS,

        /**
         * How long has the been in the previous status? This is <tt>null</tt> for the first version
         * and for versions where the status did not change.
         * Use it to obtain metrics such as the average time bugs stay UNCONFIRMED.
         *
         * This is -1 for the first version of a bug.
         */
        DAYS_IN_PREVIOUS_STATUS,

        /**
         * How long has a bug been OPEN in total (including this version).
         * This is -1 for the latest version (see above).
         */
        DAYS_OPEN_ACCUMULATED,

        /** How often has the bug been reopened (so far)? Defined for all versions.*/
        TIMES_REOPENED,

        /** 
         * A version number from 1 to n (the latest version). Primarily useful to select the 
         * first version.
         */
        NUMBER;

        public String columnName;
        
        @Override
        public String columnName() { return columnName; }
        
        @Override
        public Family family() { return Family.MEASUREMENT; }
        
        Measurement(String name) { columnName = name; }
        Measurement() { columnName = name().toLowerCase(); }
    }

 }
