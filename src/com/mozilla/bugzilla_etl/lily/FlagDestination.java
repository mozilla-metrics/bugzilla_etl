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

import org.lilycms.repository.api.Record;
import org.lilycms.repository.api.RecordId;
import org.lilycms.repository.api.RecordNotFoundException;
import org.lilycms.repository.api.RecordType;
import org.lilycms.repository.api.RepositoryException;

import com.mozilla.bugzilla_etl.base.Assert;
import com.mozilla.bugzilla_etl.base.Destination;
import com.mozilla.bugzilla_etl.base.Fields;
import com.mozilla.bugzilla_etl.base.Flag;

public class FlagDestination
extends AbstractLilyClient implements Destination<Flag, RepositoryException> {

    public FlagDestination(PrintStream log, String lilyConnection) {
        super(log, lilyConnection);
        flagType = types.flagType();
    }

    public void send(Flag flag) throws RepositoryException {

        Assert.nonNull(flag.id(), flag.name(), flag.status());

        RecordId id = ids.id(flag);
        Record record = null;
        try {
            record = repository.read(id);
            log.format("Record {flag id='%s'} has already been created. Skipping.\n", id);
            return;
        }
        catch (RecordNotFoundException notFound) { /* fine, we'll create it */ }

        // OK, new version.
        record = repository.newRecord();
        record.setRecordType(flagType.getId(), null);
        record.setId(id);
        record.setField(types.flagParams.get(Fields.Flag.ID).qname, flag.id());
        record.setField(types.flagParams.get(Fields.Flag.NAME).qname, flag.name());
        record.setField(types.flagParams.get(Fields.Flag.STATUS).qname, ""+flag.status().indicator);
        record = repository.create(record);
        record.setField(types.vTagParams.get(Types.VTag.HISTORY).qname, record.getVersion());
        repository.update(record);

        log.format("Done with flag {flag id='%s', versions='%d'}\n",
                   id, record.getVersion());
    }

    private final RecordType flagType;

}
