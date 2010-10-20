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
import java.util.List;
import java.util.Map.Entry;

import org.joda.time.DateTime;
import org.lilycms.repository.api.FieldTypeNotFoundException;
import org.lilycms.repository.api.Record;
import org.lilycms.repository.api.RecordException;
import org.lilycms.repository.api.RecordExistsException;
import org.lilycms.repository.api.RecordId;
import org.lilycms.repository.api.RecordNotFoundException;
import org.lilycms.repository.api.RecordType;
import org.lilycms.repository.api.RecordTypeNotFoundException;
import org.lilycms.repository.api.RepositoryException;
import org.lilycms.repository.api.TypeException;
import org.lilycms.repository.api.VersionNotFoundException;

import com.mozilla.bugzilla_etl.base.Assert;
import com.mozilla.bugzilla_etl.base.Bug;
import com.mozilla.bugzilla_etl.base.Converters;
import com.mozilla.bugzilla_etl.base.Counter;
import com.mozilla.bugzilla_etl.base.Destination;
import com.mozilla.bugzilla_etl.base.Fields;
import com.mozilla.bugzilla_etl.base.PersistenceState;
import com.mozilla.bugzilla_etl.base.Version;
import com.mozilla.bugzilla_etl.base.Converters.Converter;
import com.mozilla.bugzilla_etl.lily.Types.Params;

public class BugDestination
extends AbstractLilyClient implements Destination<Bug, RepositoryException> {

    public BugDestination(PrintStream log, String lilyConnection) {
        super(log, lilyConnection);
        bugType = types.bugType();
        csvConverter = new Converters.CsvConverter();
    }

    /**
     * Save this bug into the repository.
     * :TODO: Only use LilyCMS versioning when indexing becomes available independently from vtags.
     * @throws TypeException
     * @throws VersionNotFoundException
     * @throws RecordException
     * @throws FieldTypeNotFoundException
     * @throws RecordTypeNotFoundException
     * @throws RecordNotFoundException
     */
    @Override
    public void send(Bug bug) throws RepositoryException {

        // Fetch or create the record.
        final RecordId id = ids.id(bug);
        Record record = null;
        if (bug.iterator().next().persistenceState() == PersistenceState.NEW) {
            record = repository.newRecord();
            record.setRecordType(bugType.getName(), null);
            record.setId(id);
            record.setField(types.bugParams.get(Fields.Bug.ID).qname, bug.id());
            record.setField(types.bugParams.get(Fields.Bug.REPORTED_BY).qname, bug.reporter());
            log.format("SEND {bug id='%s'} is new.\n", id);
        }
        else {
            log.format("SEND {bug id='%s'} has already been created -> incremental.\n", id);
        }

        // Create the (new) versions.
        // Create the real bug so it can be queried by its own ID for future incremental updates.
        long currentVersion = 0;
        for (Version version : bug) {
            ++currentVersion;
            switch (version.persistenceState()) {
                case SAVED:
                    continue;
                case DIRTY:
                    record = repository.read(id, currentVersion);
                    setVersionMutableFields(record, version);
                    record = repository.update(record, true, true);
                    log.format("Updated {bug id='%s'} (# v%d).\n", id, record.getVersion());
                    continue;
                case NEW:
                    if (record == null ||
                        (record.getVersion() != null && record.getVersion() == currentVersion-1)) {
                        record = repository.read(id);
                    }
                    setVersionFields(record, version);
                    setVersionMutableFields(record, version);
                    if (currentVersion == 1) {
                        record = repository.create(record);
                        log.format("Created {bug id='%s'} (* v%d).\n", id, record.getVersion());
                    }
                    else {
                        record = repository.update(record);
                        log.format("Updated {bug id='%s'} (* v%d).\n", id, record.getVersion());
                    }
                    continue;
                default:
                    Assert.unreachable();
            }
        }
        counter.count(bug);
        String latest = "<not retrieved>";
        if (record != null && record.getVersion() != null) {
            latest = record.getVersion().toString();
        }
        log.format("SEND done {bug id='%s', versions='%d', latest='%s'}\n",
                   id, currentVersion, latest);

        // Create the fake versions so all historic versions are indexed.
        // This should be removed once we are able to index versions without vtags.
        for (Version version : bug) send(bug, version);
        log.format("SEND done with historic versions {bug id='%s', versions='%d'}\n",
                   bug.id(), bug.numVersions());
    }

    /**
     * As long as we do not have indexing on all versions, simply add an individual bug record for
     * each version.
     * :TODO: Remove this once indexing for all versions is available.
     */
    private void send(Bug bug, Version version) throws RepositoryException {

        Assert.nonNull(bug.id(), version.from(), version.to(), version.annotation());

        final RecordId id = ids.id(version);
        log.format("SEND {bug historic, id='%s'}\n", id.toString());
        if (version.persistenceState() == PersistenceState.SAVED) {
            // Incremental update: we already know this version.
            log.format("SEND {bug historic, id='%s'} is already persisted. Skipping.\n", id);
            return;
        }
        if (version.persistenceState() == PersistenceState.DIRTY) {
            log.format("SEND historic#%s TRYING TO UPDATE {bug historic, id='%s'}:\n", bug.id(), id);
            log.format("SEND historic#%s TRYING TO UPDATE %s.\n", bug.id(), version);
            Record record = null;
            record = repository.read(id, 1L);
            record.setRecordType(bugType.getName(), null);
            setVersionMutableFields(record, version);
            log.format("SEND historic#%s (v: %d) EXPIRATION DATE AFTER UPDATE: %s.\n", bug.id(),
                       record.getVersion(),
                       record.getField(types.versionParams.get(Fields.Version.EXPIRATION_DATE).qname));
            record = repository.update(record, true, true);
            historicCounter.increment(Counter.Item.OLD_ZERO);
            return;
        }

        // OK, new version.
        Record record = repository.newRecord();
        record.setId(id);
        record.setField(types.bugParams.get(Fields.Bug.ID).qname, version.bug().id());
        record.setField(types.bugParams.get(Fields.Bug.REPORTED_BY).qname, bug.reporter());
        setVersionFields(record, version);
        setVersionMutableFields(record, version);
        record.setField(types.vTagParams.get(Types.VTag.HISTORY).qname, 1L);
        try {
            record = repository.create(record);
            historicCounter.increment(Counter.Item.NEW_ZERO);
        }
        catch (RecordExistsException exists) {
            log.format("SEND {bug historic, id='%s'} already exists! Previous run cancelled?\n", id);
        }
    }

    private void setVersionMutableFields(Record record, Version version) {
        record.setField(types.versionParams.get(Fields.Version.EXPIRATION_DATE).qname,
                        new DateTime(version.to().getTime()));
        record.setField(types.versionParams.get(Fields.Version.ANNOTATION).qname,
                        version.annotation());
    }

    private void setVersionFields(Record record, Version version) {
        record.setRecordType(bugType.getName(), null);
        record.setField(types.versionParams.get(Fields.Version.MODIFIED_BY).qname,
                        version.author());
        record.setField(types.versionParams.get(Fields.Version.MODIFICATION_DATE).qname,
                        new DateTime(version.from().getTime()));
        for (final Entry<Fields.Facet, String> entry : version.facets().entrySet()) {
            final Fields.Facet facet = entry.getKey();
            final Params params = types.facetParams.get(facet);
            if (params.type == types.stringlists) {
                List<String> values = csvConverter.parse(entry.getValue());
                record.setField(params.qname, values);
                continue;
            }
            String value = entry.getValue();
            if (value == null) value = "<none>";
            record.setField(params.qname, value);
        }
        for (final Entry<Fields.Measurement, Long> entry : version.measurements().entrySet()) {
            Long value = entry.getValue();
            Assert.nonNull(value);
            record.setField(types.measurementParams.get(entry.getKey()).qname, value);
        }
    }

    public static final Counter counter = new Counter("Bugs");
    public static final Counter historicCounter = new Counter("History Records");
    private final RecordType bugType;
    private final Converter<List<String>> csvConverter;

}
