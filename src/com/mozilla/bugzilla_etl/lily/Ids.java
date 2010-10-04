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

package com.mozilla.bugzilla_etl.lily;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.lilycms.repository.api.IdGenerator;
import org.lilycms.repository.api.RecordId;
import org.lilycms.repository.api.Repository;

import com.mozilla.bugzilla_etl.base.Bug;
import com.mozilla.bugzilla_etl.base.Flag;
import com.mozilla.bugzilla_etl.base.Version;

/** Encapsultes the patterns by which repository ids are generated. */
class Ids {

    private final IdGenerator generator;
    private static final SimpleDateFormat isoFormat
        = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss,SSS");

    public Ids(Repository repository) {
        generator = repository.getIdGenerator();
    }

    /** Generate an id for this bug (no matter if already persisted or not). */
    final RecordId id(Bug bug) {
        return forBug(bug.id());
    }

    /** Generate id for this historic bug version record (no matter if already persisted or not). */
    final RecordId id(Version version) {
        return forVersion(version.bug().id(), version.from());
    }

    /** Generate an id for this flag (no matter if already persisted or not). */
    final RecordId id(Flag flag) {
        final StringBuilder buffer = new StringBuilder();
        return generator.newRecordId(buffer.append(flag.status().indicator)
                                           .append(' ')
                                           .append(flag.id()).toString());
    }

    final RecordId forBug(final Long bugzillaBugId) {
        return generator.newRecordId("#" + bugzillaBugId);
    }

    final RecordId forVersion(final Long bugzillaBugId, final Date modificationDate) {
        final String isoDate = isoFormat.format(modificationDate);
        return generator.newRecordId("#" + bugzillaBugId + " @ " + isoDate);
    }

}
