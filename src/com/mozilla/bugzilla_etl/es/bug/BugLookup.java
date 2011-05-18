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

package com.mozilla.bugzilla_etl.es.bug;

import static com.mozilla.bugzilla_etl.es.Mappings.VERSION;
import static com.mozilla.bugzilla_etl.es.bug.Mappings.BUG;
import static com.mozilla.bugzilla_etl.es.bug.Mappings.FACET;
import static com.mozilla.bugzilla_etl.es.bug.Mappings.MEASURE;

import java.io.PrintStream;
import java.util.EnumMap;
import java.util.Map;

import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;

import com.mozilla.bugzilla_etl.base.Assert;
import com.mozilla.bugzilla_etl.di.bug.IBugLookup;
import com.mozilla.bugzilla_etl.es.AbstractLookup;
import com.mozilla.bugzilla_etl.model.Fields;
import com.mozilla.bugzilla_etl.model.PersistenceState;
import com.mozilla.bugzilla_etl.model.bug.Bug;
import com.mozilla.bugzilla_etl.model.bug.BugFields;
import com.mozilla.bugzilla_etl.model.bug.BugVersion;

public class BugLookup extends AbstractLookup<Bug, BugVersion, BugFields.Facet>
implements IBugLookup {

    public BugLookup(final PrintStream log, final String esNodes, final String esCluster) {
        super(log, esNodes, esCluster);
    }

    /** Construct a Bug from its elasticsearch version records. */
    protected Bug reconstruct(final SearchHits hits) {
        Assert.nonNull(hits);
        Assert.check(hits.hits().length > 0);

        final Map<String, Object> bugFields =
            hits.hits()[0].getSource();

        final Bug bug = new Bug(
            BUG.integer(BugFields.Bug.ID, bugFields),
            BUG.string(BugFields.Bug.REPORTED_BY, bugFields),
            BUG.date(BugFields.Bug.CREATION_DATE, bugFields)
        );

        for (final SearchHit hit : hits) {
            final Map<String, Object> fields = hit.getSource();

            final EnumMap<BugFields.Facet, String> facets = bug.createFacets();
            for (BugFields.Facet facet : BugFields.Facet.values()) {
                facets.put(facet, FACET.string(facet, fields));
            }

            final EnumMap<BugFields.Measurement, Long> measurements = bug.createMeasurements();
            for (BugFields.Measurement measurement : BugFields.Measurement.values()) {
                measurements.put(measurement,
                                 MEASURE.integer(measurement, fields));
            }

            bug.append(new BugVersion(
                bug,
                facets,
                measurements,
                VERSION.string(Fields.Activity.MODIFIED_BY, fields),
                VERSION.string(Fields.Activity.ANNOTATION, fields),
                VERSION.date(Fields.Activity.MODIFICATION_DATE, fields),
                VERSION.date(Fields.Activity.EXPIRATION_DATE, fields),
                PersistenceState.SAVED
            ));
        }
        return bug;
    }

    @Override protected String type() { return "bug"; }
    @Override protected String index() { return "bugs"; }
    @Override protected String idColumn() { return BugFields.Bug.ID.columnName(); }
    @Override protected String numberColumn() { return BugFields.Measurement.NUMBER.columnName(); }

}
