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

import java.io.PrintStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.lilyproject.repository.api.QName;
import org.lilyproject.repository.api.Record;
import org.lilyproject.repository.api.RecordId;
import org.lilyproject.repository.api.RecordNotFoundException;
import org.lilyproject.repository.api.RepositoryException;
import org.lilyproject.repository.api.ValueType;

import com.mozilla.bugzilla_etl.base.Assert;
import com.mozilla.bugzilla_etl.base.Bug;
import com.mozilla.bugzilla_etl.base.Converters;
import com.mozilla.bugzilla_etl.base.Fields;
import com.mozilla.bugzilla_etl.base.PersistenceState;
import com.mozilla.bugzilla_etl.base.Version;
import com.mozilla.bugzilla_etl.base.Converters.Converter;
import com.mozilla.bugzilla_etl.lily.Types.Params;

public class LilyBugLookup extends AbstractLilyClient implements BugLookup {

    private final Converter<List<String>> csvConverter = new Converters.CsvConverter(true);

    private Comparator<Record> versionComparator = new Comparator<Record>() {
        public int compare(Record a, Record b) {
            return a.getVersion().compareTo(b.getVersion());
        }
    };

    final List<QName> emptyList = new LinkedList<QName>();

    public LilyBugLookup(final PrintStream log, final String lilyConnection) {
        super(log, lilyConnection);
    }

    @Override
    public Bug find(Long bugzillaId) throws RepositoryException {
        Record record;
        RecordId id = ids.forBug(bugzillaId);
        try {
            record = repository.read(id);
        }
        catch (RecordNotFoundException e) {
            return null;
        }
        catch (Throwable e) {
            log.format("LOOKUP: Unexpected Error Trying to read bug %s using record id %s\n",
                       bugzillaId, id);
            do {
                e.printStackTrace(log);
                e = e.getCause();
            } while (e != null);
            return null;
        }

        final List<Record> versionRecords = repository.readVersions(id, new Long(1),
                                                                    record.getVersion(), emptyList);

        // Ugly workaround: when deleting and re-creating a record, lily restores previous versions,
        //                  even after an hbase compaction (soft-delete). We are only interested in
        //                  the latest versions though (otherwise we'll have dupes).
        Collections.sort(versionRecords, versionComparator);
        final List<Record> versionsToUse = new java.util.ArrayList<Record>(versionRecords.size());
        final Types.Params numberParams = types.measurementParams.get(Fields.Measurement.NUMBER);
        int maxNumber = 1;
        for (Record r : versionRecords) {
            final int number = ((Long) r.getField(numberParams.qname)).intValue();
            if (versionsToUse.size() < number) {
                versionsToUse.add(number - 1, r);
            }
            else {
                versionsToUse.set(number - 1, r);
            }
            maxNumber = Math.max(maxNumber, number);
        }

        // Now we have only the "latest" bug versions, sorted ascending.
        log.format("LOOKUP: Reconstructing bug %d from %s/%s versions\n",
                   bugzillaId, versionsToUse.size(), versionRecords.size());

        return reconstruct(versionsToUse);
    }

    /** Map a bunch of lily bug records back to a Bug entity. */
    private Bug reconstruct(final List<Record> versionRecords) {
        final Map<QName, Object> bugFields = versionRecords.get(0).getFields();

        final Bug bug = new Bug(
            (Long) bugFields.get(types.bugParams.get(Fields.Bug.ID).qname),
            (String) bugFields.get(types.bugParams.get(Fields.Bug.REPORTED_BY).qname),
            getDate(bugFields, types.bugParams.get(Fields.Bug.CREATION_DATE))
        );

        for (final Record versionRecord : versionRecords) {
            final Map<QName, Object> fields = versionRecord.getFields();

            final EnumMap<Fields.Facet, String> facets = Version.createFacets();
            for (Fields.Facet facet : Fields.Facet.values()) {
                Params facetParams = types.facetParams.get(facet);
                ValueType valueType = facetParams.type;
                if (types.facetParams.get(facet).type == types.strings) {
                    facets.put(facet, getString(fields, facetParams));
                    continue;
                }
                if (valueType == types.stringlists) {
                    facets.put(facet, getCsv(fields, facetParams));
                    continue;
                }
                if (valueType == types.dates) {
                    final Date dateValue = getDate(fields, facetParams);
                    facets.put(facet, Converters.DATE.format(dateValue));
                    continue;
                }
                Assert.unreachable("Unhandled valueType %s used for facet: %s", valueType, facet);
            }

            final EnumMap<Fields.Measurement, Long> measurements = Version.createMeasurements();
            for (Fields.Measurement measurement : Fields.Measurement.values()) {
                measurements.put(measurement,
                                 getLong(fields, types.measurementParams.get(measurement)));
            }

            bug.append(new Version(
                bug,
                facets,
                measurements,
                getString(fields, types.versionParams.get(Fields.Version.MODIFIED_BY)),
                getString(fields, types.versionParams.get(Fields.Version.ANNOTATION)),
                getDate(fields, types.versionParams.get(Fields.Version.MODIFICATION_DATE)),
                getDate(fields, types.versionParams.get(Fields.Version.EXPIRATION_DATE)),
                PersistenceState.SAVED
            ));
        }
        return bug;
    }

    private Date getDate(Map<QName, Object> fields, Params params) {
        Assert.check(params.type == types.dates);
        if (!fields.containsKey(params.qname) || fields.get(params.qname) == null) return null;
        return ((org.joda.time.DateTime) fields.get(params.qname)).toDate();
    }

    private String getString(Map<QName, Object> fields, Params params) {
        Assert.check(params.type == types.strings);
        if (!fields.containsKey(params.qname) || fields.get(params.qname) == null) return null;
        return (String) fields.get(params.qname);
    }

    private Long getLong(Map<QName, Object> fields, Params params) {
        Assert.check(params.type == types.longs);
        if (!fields.containsKey(params.qname) || fields.get(params.qname) == null) return null;
        return (Long) fields.get(params.qname);
    }

    /** Flatten string list to csv. The cast is guarded by the check on the lily value type. */
    @SuppressWarnings("unchecked")
    private String getCsv(Map<QName, Object> fields, Params fieldParams) {
        Assert.check(fieldParams.type == types.stringlists);
        List<String> values = (List<String>) fields.get(fieldParams.qname);
        return csvConverter.format(values);
    }
}
