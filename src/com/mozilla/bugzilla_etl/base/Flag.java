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

/**
 * A flag used by a bug. Has a name and status.
 * Each bug version can have one or more of these.
 */
public class Flag {

    public enum Status {
        REQUESTED('?'),
        APPROVED('+'),
        DENIED('-'),
        
        // Example: "...-needed" flags for maintainance releases in BMO
        NA('/');
        public final char indicator;
        public static Status forIndicator(char indicator) {
            switch (indicator) {
                case '?': return REQUESTED;
                case '+': return APPROVED;
                case '-': return DENIED;
                default: return NA;
            }
        }
        Status(char indicator) { this.indicator = indicator; }
    }

    public Long id() { return id; }
    public String name() { return type; }
    public Status status() { return status; }

    private final String type;
    private final Status status;
    private final Long id;

    public static Flag fromRepresentation(String representation) {
        // Strip any requestee, currently not used.
        final int requesteePos = representation.indexOf('(');
        if (requesteePos != -1) representation = representation.substring(0, requesteePos);
        
        final int indicatorPos = representation.length() -1;
        Assert.check(indicatorPos > 0);        
        final Status status = Status.forIndicator(representation.charAt(indicatorPos));

        if (status == Status.NA) {
            // :BMO: Special case: There is a field overflow in Bug 448640 ("flag-spam").
            if(representation.equals("in-testsu")) return new Flag("in-testsuite", Status.REQUESTED);
            return new Flag(representation, status);
        }
        
        return new Flag(representation.substring(0, indicatorPos), status);
    }

    public Flag(final String flagType, final Status status) {
        Assert.nonNull(flagType, status);
        this.id = null;
        this.type = flagType;
        this.status = status;
    }

    public Flag(final Long id, final String flagType, final Status status) {
        Assert.nonNull(id, flagType, status);
        this.id = id;
        this.type = flagType;
        this.status = status;
    }

    public String representation() {
        return status == Status.NA ? type : type + status.indicator;
    }

    @Override
    public String toString() {
        return representation();
    }
}
