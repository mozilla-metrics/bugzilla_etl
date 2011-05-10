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
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Date;

import org.joda.time.DateTime;
import org.lilyproject.repository.api.FieldTypeNotFoundException;
import org.lilyproject.repository.api.QName;
import org.lilyproject.repository.api.Record;
import org.lilyproject.repository.api.RecordException;
import org.lilyproject.repository.api.RecordExistsException;
import org.lilyproject.repository.api.RecordId;
import org.lilyproject.repository.api.RecordLockedException;
import org.lilyproject.repository.api.RecordNotFoundException;
import org.lilyproject.repository.api.RecordType;
import org.lilyproject.repository.api.RecordTypeNotFoundException;
import org.lilyproject.repository.api.RepositoryException;
import org.lilyproject.repository.api.TypeException;
import org.lilyproject.repository.api.VersionNotFoundException;

import com.mozilla.bugzilla_etl.base.Assert;
import com.mozilla.bugzilla_etl.base.Counter;
import com.mozilla.bugzilla_etl.base.Destination;
import com.mozilla.bugzilla_etl.di.Converters;
import com.mozilla.bugzilla_etl.di.Converters.Converter;
import com.mozilla.bugzilla_etl.lily.Types.Params;
import com.mozilla.bugzilla_etl.model.PersistenceState;
import com.mozilla.bugzilla_etl.model.bug.Bug;
import com.mozilla.bugzilla_etl.model.bug.BugFields;
import com.mozilla.bugzilla_etl.model.bug.BugVersion;

public class BugDestination
extends AbstractLilyClient implements Destination<Bug, RepositoryException> {

    private boolean DEBUG_SEND = false;

    final Object waitLock = new Object();

    final List<QName> emptyList = new LinkedList<QName>();

    public BugDestination(PrintStream log, String lilyConnection) {
        super(log, lilyConnection);
        bugType = types.bugType();
        csvConverter = new Converters.CsvConverter();
    }

    /**
     * Save this bug into the repository.
     * :TODO: Use only LilyCMS versioning when indexing becomes available independently from vtags.
     * @throws InterruptedException 
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
        log.format("SEND: Sending bug %s (record: [bug id='%s']).\n", bug.id(), id);

        if (bug.iterator().next().persistenceState() == PersistenceState.NEW) {
            record = newBugRecord(bug, id);
            if (DEBUG_SEND) log.format("SEND [bug id='%s'] is new.\n", id);
        }
        else {
            if (DEBUG_SEND) log.format("SEND [bug id='%s'] has already been created -> incremental.\n", id);
        }

        try {
            // Create the (new) versions.
            // Create the real bug so it can be queried by its own ID for future incremental updates.
            long currentVersion = 0;
            for (BugVersion version : bug) {
                ++currentVersion;
                switch (version.persistenceState()) {
                    case SAVED:
                        continue;
                    case DIRTY:
                        record = bugAtNumber(id, currentVersion);
                        setVersionMutableFields(record, version);
                        doReplace(record, id);
                        continue;
                    case NEW:
                        if (record == null ||
                            (record.getVersion() != null && record.getVersion() == currentVersion-1)) {
                            record = repository.read(id);
                        }
                        setVersionFields(record, version);
                        setVersionMutableFields(record, version);
                        if (currentVersion == 1) {
                            doCreate(record, id);
                        }
                        else {
                            doAppend(record, id);
                        }
                        continue;
                    default:
                        Assert.unreachable();
                }
            }
            counter.count(bug);
            String latest = "<N.A.>";
            if (record != null && record.getVersion() != null) {
                latest = record.getVersion().toString();
            }
            log.format("SEND DONE [bug id='%s', versions='%d', latest='%s']\n",
                                       id, currentVersion, latest);
    
            // Create the fake versions so all historic versions are indexed.
            // This should be removed once we are able to index versions without vtags.
            for (BugVersion version : bug) send(bug, version);
        }
        catch(InterruptedException e) {
            log.format("Got interrupted trying to send [bug id='%s']\n", id);
            throw new RuntimeException(e);
        }
        log.format("SEND DONE with historic versions {bug id='%s', versions='%d'}\n",
                   bug.id(), bug.numVersions());
    }

    void doCreate(final Record record, final RecordId id) 
    throws RepositoryException, InterruptedException {
        waitForIt(new Failable<RepositoryException>() {
            public void tryIt() throws RepositoryException, InterruptedException {
                try { repository.create(record); }
                catch (RecordExistsException e) { repository.update(record); }
                if (DEBUG_SEND) log.format("Created %s (* v=1).\n", this);
            }
            public String toString() { return String.format("[bug id='%s']", id); }
        });
    }

    void doAppend(final Record record, final RecordId id) 
    throws RepositoryException, InterruptedException {
        waitForIt(new Failable<RepositoryException>() {
            public void tryIt() throws RepositoryException, InterruptedException {
                repository.update(record, false, true);
                if (DEBUG_SEND) log.format("Appended %s (* v=%d).\n", this, record.getVersion());
            }
            public String toString() { return String.format("[bug id='%s']", id); }
        });
    }

    void doReplace(final Record record, final RecordId id) 
    throws RepositoryException, InterruptedException {
        waitForIt(new Failable<RepositoryException>() {
            public void tryIt() throws RepositoryException, InterruptedException {
                repository.update(record, true, true);
                historicCounter.increment(Counter.Item.NEW_ZERO);
                if (DEBUG_SEND) log.format("Updated %s (# v=%d).\n", this, record.getVersion());
            }
            public String toString() { return String.format("[bug id='%s']", id); }
        });
    }

    /**
     * As long as we do not have indexing on all versions, simply add an individual bug record for
     * each version.
     * :TODO: Remove this once indexing for all versions is available.
     */
    private void send(final Bug bug, final BugVersion version) 
    throws RepositoryException, InterruptedException {

        Assert.nonNull(bug.id(), version.from(), version.to(), version.annotation());

        final RecordId id = ids.id(version);
        if (DEBUG_SEND) log.format("SEND [version, id='%s']\n", id.toString());
        if (version.persistenceState() == PersistenceState.SAVED) {
            // Incremental update: we already know this version.
            if (DEBUG_SEND) log.format("SEND [version, id='%s'] skipped existing version.\n", id);
            return;
        }
        if (version.persistenceState() == PersistenceState.DIRTY) {
            if (DEBUG_SEND) log.format("SEND historic#%s TRYING TO UPDATE %s.\n", bug.id(), version);
            Record record = null;
            record = repository.read(id);
            record.setRecordType(bugType.getName(), null);
            setVersionMutableFields(record, version);
            log.format("SEND historic#%s (v: %d) EXPIRATION DATE AFTER UPDATE: %s.\n", bug.id(),
                       record.getVersion(),
                       record.getField(types.versionParams.get(BugFields.Activity.EXPIRATION_DATE).qname));
            record = repository.update(record, true, true);
            historicCounter.increment(Counter.Item.OLD_ZERO);
            return;
        }

        // OK, new version.
        final Record record = newBugRecord(bug, id);
        setVersionFields(record, version);
        setVersionMutableFields(record, version);
        record.setField(types.vTagParams.get(Types.VTag.HISTORY).qname, 1L);

        waitForIt(new Failable<RepositoryException>() {
            public void tryIt() throws RepositoryException, InterruptedException {
                try { repository.create(record); }
                catch (RecordExistsException exists) { repository.update(record); }
                historicCounter.increment(Counter.Item.NEW_ZERO);
            }
            public String toString() { return String.format("[bug historic, id='%s']", id); }
        });
    }

    private void waitForIt(Failable<RepositoryException> failable)
    throws RepositoryException, InterruptedException {
        final int MAX_RETRIES = 20;
        final int WAIT_MS = 1000;
        int retries = MAX_RETRIES;
        synchronized (waitLock) {
            while (retries > 0) {
                try {
                    failable.tryIt();
                    retries = 0;
                }
                catch (RecordLockedException exception) {
                    if (retries == 0) {
                        log.format("SEND %s -- RecordException: Retries exhausted.\n", failable);
                        throw exception;
                    }
                    log.format("SEND %s RecordException (see below): Retrying.\n", failable);
                    exception.printStackTrace(log);
                    --retries;
                    waitLock.wait(WAIT_MS);
                }
            }
        }
    }

    private Record newBugRecord(final Bug bug, final RecordId id) throws RecordException {
        Record record = repository.newRecord();
        record.setId(id);
        record.setRecordType(bugType.getName(), null);
        record.setField(types.bugParams.get(BugFields.Bug.ID).qname, bug.id());
        record.setField(types.bugParams.get(BugFields.Bug.REPORTED_BY).qname, bug.reporter());
        Assert.nonNull(types.bugParams.get(BugFields.Bug.CREATION_DATE),
                       types.bugParams.get(BugFields.Bug.CREATION_DATE).qname,
                       bug.creationDate());
        record.setField(types.bugParams.get(BugFields.Bug.CREATION_DATE).qname,
                        new DateTime(bug.creationDate().getTime()));
        return record;
    }

    private void setVersionMutableFields(Record record, BugVersion version) {
        record.setField(types.versionParams.get(BugFields.Activity.EXPIRATION_DATE).qname,
                        new DateTime(version.to().getTime()));
        record.setField(types.versionParams.get(BugFields.Activity.ANNOTATION).qname,
                        version.annotation());
    }

    private void setVersionFields(Record record, BugVersion version) {
        record.setRecordType(bugType.getName(), null);
        record.setField(types.versionParams.get(BugFields.Activity.MODIFIED_BY).qname,
                        version.author());
        record.setField(types.versionParams.get(BugFields.Activity.MODIFICATION_DATE).qname,
                        new DateTime(version.from().getTime()));
        for (final Entry<BugFields.Facet, String> entry : version.facets().entrySet()) {
            final BugFields.Facet facet = entry.getKey();
            final Params params = types.facetParams.get(facet);
            if (params.type == types.strings) {
                String value = entry.getValue();
                if (value == null) value = "<none>";
                record.setField(params.qname, value);
                continue;
            }
            if (params.type == types.stringlists) {
                List<String> values = csvConverter.parse(entry.getValue());
                record.setField(params.qname, values);
                continue;
            }
            if (params.type == types.dates) {
                Date value = Converters.DATE.parse(entry.getValue());
                if (value != null) record.setField(params.qname, new DateTime(value.getTime()));
                continue;
            }
        }
        for (final Entry<BugFields.Measurement, Long> entry : version.measurements().entrySet()) {
            Long value = entry.getValue();
            Assert.nonNull(value);
            record.setField(types.measurementParams.get(entry.getKey()).qname, value);
        }
    }

    /**
     * Workaround:
     * Due to Lily’s delete behavior (no versions are actually deleted), there can be multiple
     * lily-versions with the same bugzilla version-number.
     *
     * Retrieve all records and return the latest version with the given number.
     *
     * This is needed to update the correct DIRTY version after a bug record has been recreated.
     * @throws InterruptedException 
     */
    private Record bugAtNumber(final RecordId id, final long number)
    throws RepositoryException, InterruptedException {
        Record latest = repository.read(id);
        List<Record> all = repository.readVersions(id, number, latest.getVersion(), emptyList);
        long recordNumber;
        int i = all.size();
        QName numberOName = types.measurementParams.get(BugFields.Measurement.NUMBER).qname;
        do {
            --i;
            final Record record = all.get(i);
            recordNumber = (Long) record.getField(numberOName);
            if (recordNumber == number) return record;
        } while (i > 0);
        Assert.unreachable("The version %d for record %s does not exist.", number, id);
        return null;
    }

    public static final Counter counter = new Counter("Bugs");
    public static final Counter historicCounter = new Counter("History Records");
    private final RecordType bugType;
    private final Converter<List<String>> csvConverter;

    @Override public void flush() throws RepositoryException { }

}
