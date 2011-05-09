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

import org.lilyproject.repository.api.IdGenerator;
import org.lilyproject.repository.api.RecordId;
import org.lilyproject.repository.api.Repository;

import com.mozilla.bugzilla_etl.base.Assert;
import com.mozilla.bugzilla_etl.model.bug.Bug;
import com.mozilla.bugzilla_etl.model.bug.BugVersion;

/** Encapsultes the patterns by which repository ids are generated. */
class Ids {

    private final IdGenerator generator;

    public Ids(Repository repository) {
        generator = repository.getIdGenerator();
    }

    /** Generate an id for this bug (no matter if already persisted or not). */
    final RecordId id(Bug bug) {
        Assert.nonNull(bug);
        return forBug(bug.id());
    }

    /** Generate id for this historic bug version record (no matter if already persisted or not). */
    final RecordId id(BugVersion version) {
        Assert.nonNull(version);
        Long bugId = version.entity().id();
        return forVersion(bugId, version.from().getTime());
    }

    protected String id(final Long bugId, final Long versionIdentifier) {
        final String prefix = String.format("%06d", bugId);
        final String suffix = (versionIdentifier != null) ? versionIdentifier.toString() : "";
        final StringBuilder dest = new StringBuilder(prefix.length() + 1 + suffix.length());
        for (int i = prefix.length() - 1; i >= 0; i--) dest.append(prefix.charAt(i));
        dest.append('#');
        dest.append(suffix);
        return dest.toString();
      }

    final RecordId forBug(final Long bugzillaBugId) {
        Assert.nonNull(bugzillaBugId);
        return generator.newRecordId(id(bugzillaBugId, null));
    }

    private final RecordId forVersion(final Long bugzillaBugId, final Long versionIdentifier) {
        Assert.nonNull(bugzillaBugId, versionIdentifier);
        return generator.newRecordId(id(bugzillaBugId, versionIdentifier));
    }

}
