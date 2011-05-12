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

package com.mozilla.bugzilla_etl.di.bug;

import java.util.Date;
import java.util.EnumMap;

import org.pentaho.di.core.RowSet;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.exception.KettleStepException;
import org.pentaho.di.core.exception.KettleValueException;
import org.pentaho.di.trans.steps.userdefinedjavaclass.TransformClassBase;

import com.mozilla.bugzilla_etl.base.Counter;
import com.mozilla.bugzilla_etl.di.AbstractSource;
import com.mozilla.bugzilla_etl.model.Fields;
import com.mozilla.bugzilla_etl.model.PersistenceState;
import com.mozilla.bugzilla_etl.model.bug.Bug;
import com.mozilla.bugzilla_etl.model.bug.BugFields;
import com.mozilla.bugzilla_etl.model.bug.BugVersion;

/**
 * Receive bug entities from another PDI step.
 *
 * The expected representation is not the same as the one fed into the Rebuilder. Instead it is the
 * sanitized, complete representation including computed facets and measurements (history is rebuilt
 * already).
 *
 * Flags are expected as the complete set of flags in each version, not only the changes.
 *
 * The useful thing is that it allows to pass objects around in PDI very closely to what they
 * will look like in LilyCMS.
 */
public class BugSource extends AbstractSource<Bug> {

    public BugSource(TransformClassBase toStep, RowSet inRows) throws KettleStepException {
        super(toStep, inRows);
    }

    @Override
    public Bug receive() throws KettleException {
        final Long bugId = input.cell(BugFields.Bug.ID).longValue();

        final Bug bug = new Bug(bugId,
                                input.cell(BugFields.Bug.REPORTED_BY).stringValue(),
                                input.cell(BugFields.Bug.CREATION_DATE).dateValue());

        bug.append(versionFromRow(bug));
        while (input.next() && bugId.equals(input.cell(BugFields.Bug.ID).longValue())) {
            bug.append(versionFromRow(bug));
        }

        counter.count(bug, bug.isNew());
        return bug;
    }

    private BugVersion versionFromRow(Bug bug) throws KettleValueException {
        final String author = input.cell(Fields.Activity.MODIFIED_BY).stringValue();
        final String annotation = input.cell(Fields.Activity.ANNOTATION).stringValue();
        final Date from = input.cell(Fields.Activity.MODIFICATION_DATE).dateValue();
        final Date to = input.cell(Fields.Activity.EXPIRATION_DATE).dateValue();
        final PersistenceState persistenceState
            = input.cell(Fields.Activity.PERSISTENCE_STATE).enumValue(PersistenceState.class);

        final EnumMap<BugFields.Facet, String> facets = BugVersion.createFacets();
        for (BugFields.Facet field : BugFields.Facet.values()) {
            facets.put(field, input.cell(field, BugFields.Facet.Column.RESULT).stringValue());
        }
        final EnumMap<BugFields.Measurement, Long> measurements = BugVersion.createMeasurements();
        for (BugFields.Measurement field : BugFields.Measurement.values()) {
            measurements.put(field, input.cell(field).longValue());
        }

        BugVersion version
            = new BugVersion(bug, facets, measurements, author, annotation, from, to, persistenceState);
        return version;
    }

    public static final Counter counter = new Counter("PDI bug source");
}
