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

package com.mozilla.bugzilla_etl.es;

import java.io.PrintStream;
import java.util.EnumMap;
import java.util.Map;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.xcontent.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHitField;
import org.elasticsearch.search.SearchHits;

import com.mozilla.bugzilla_etl.base.Assert;
import com.mozilla.bugzilla_etl.base.Bug;
import com.mozilla.bugzilla_etl.base.Fields;
import com.mozilla.bugzilla_etl.base.PersistenceState;
import com.mozilla.bugzilla_etl.base.Version;
import com.mozilla.bugzilla_etl.es.Mapping.BugMapping;


public class BugLookup extends AbstractEsClient implements IBugLookup {
    
    public BugLookup(final PrintStream log, final String esQuorum) {
        super(log, esQuorum);
    }
    
    @Override
    public Bug find(Long bugzillaId) {
        
        final String index = index();
        final String type = BugMapping.TYPE;

        final SearchResponse response = 
            client.prepareSearch(index).setTypes(type)
            .setSearchType(SearchType.QUERY_AND_FETCH)
            .setQuery(QueryBuilders.fieldQuery(Fields.Bug.ID.columnName(),
                                               bugzillaId.toString()))
            .setFrom(0).setSize(Integer.MAX_VALUE).setExplain(false)
            .execute()
            .actionGet();
        
        final SearchHits hits = response.getHits();

        // Now we have only the "latest" bug versions, sorted ascending.
        log.format("LOOKUP: Reconstructing bug %d from %s versions\n",
                   bugzillaId, hits.totalHits());

        return reconstruct(hits);
    }

    
    /** Construct a Bug from its elasticsearch version records. */
    private Bug reconstruct(final SearchHits hits) {
        Assert.nonNull(hits);
        Assert.check(hits.hits().length > 0);
        
        final Map<String, SearchHitField> bugFields = 
            hits.hits()[0].getFields();

        final Bug bug = new Bug(
            BUG.integer(Fields.Bug.ID, bugFields),
            BUG.string(Fields.Bug.REPORTED_BY, bugFields),
            BUG.date(Fields.Bug.CREATION_DATE, bugFields)
        );

        for (final SearchHit hit : hits) {
            final Map<String, SearchHitField> fields = hit.getFields();
            
            final EnumMap<Fields.Facet, String> facets = Version.createFacets();
            for (Fields.Facet facet : Fields.Facet.values()) {
                facets.put(facet, FACET.string(facet, fields));
            }

            final EnumMap<Fields.Measurement, Long> measurements = Version.createMeasurements();
            for (Fields.Measurement measurement : Fields.Measurement.values()) {
                measurements.put(measurement,
                                 MEASURE.integer(measurement, fields));
            }

            bug.append(new Version(
                bug,
                facets,
                measurements,
                VERSION.string(Fields.Version.MODIFIED_BY, fields),
                VERSION.string(Fields.Version.ANNOTATION, fields),
                VERSION.date(Fields.Version.MODIFICATION_DATE, fields),
                VERSION.date(Fields.Version.EXPIRATION_DATE, fields),
                PersistenceState.SAVED
            ));
        }
        return bug;
    }


}
