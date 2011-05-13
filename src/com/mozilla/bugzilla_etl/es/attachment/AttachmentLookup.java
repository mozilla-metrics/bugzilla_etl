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

package com.mozilla.bugzilla_etl.es.attachment;

import static com.mozilla.bugzilla_etl.es.Mappings.VERSION;
import static com.mozilla.bugzilla_etl.es.attachment.Mappings.ATTACHMENT;
import static com.mozilla.bugzilla_etl.es.attachment.Mappings.FACET;
import static com.mozilla.bugzilla_etl.es.attachment.Mappings.MEASURE;

import java.io.PrintStream;
import java.util.EnumMap;
import java.util.Map;

import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;

import com.mozilla.bugzilla_etl.base.Assert;
import com.mozilla.bugzilla_etl.di.attachment.IAttachmentLookup;
import com.mozilla.bugzilla_etl.es.AbstractLookup;
import com.mozilla.bugzilla_etl.model.Fields;
import com.mozilla.bugzilla_etl.model.PersistenceState;
import com.mozilla.bugzilla_etl.model.attachment.Attachment;
import com.mozilla.bugzilla_etl.model.attachment.AttachmentVersion;
import com.mozilla.bugzilla_etl.model.attachment.AttachmentFields;

public class AttachmentLookup
extends AbstractLookup<Attachment, AttachmentVersion, AttachmentFields.Facet>
implements IAttachmentLookup {

    public AttachmentLookup(final PrintStream log, final String esNodes) {
        super(log, esNodes);
    }

    /** Construct a Bug from its elasticsearch version records. */
    protected Attachment reconstruct(final SearchHits hits) {
        Assert.nonNull(hits);
        Assert.check(hits.hits().length > 0);

        final Map<String, Object> attachmentFields = hits.hits()[0].getSource();

        final Attachment attachment = new Attachment(
            ATTACHMENT.integer(AttachmentFields.Attachment.ID, attachmentFields),
            ATTACHMENT.integer(AttachmentFields.Attachment.BUG_ID, attachmentFields),
            ATTACHMENT.string(AttachmentFields.Attachment.SUBMITTED_BY, attachmentFields),
            ATTACHMENT.date(AttachmentFields.Attachment.SUBMISSION_DATE, attachmentFields)
        );

        for (final SearchHit hit : hits) {
            final Map<String, Object> fields = hit.getSource();

            final EnumMap<AttachmentFields.Facet, String> facets = attachment.createFacets();
            for (AttachmentFields.Facet facet : AttachmentFields.Facet.values()) {
                facets.put(facet, FACET.string(facet, fields));
            }

            final EnumMap<AttachmentFields.Measurement, Long> measurements =
                attachment.createMeasurements();
            for (AttachmentFields.Measurement measurement : AttachmentFields.Measurement.values()) {
                measurements.put(measurement,
                                 MEASURE.integer(measurement, fields));
            }

            attachment.append(new AttachmentVersion(
                attachment,
                facets,
                measurements,
                VERSION.string(Fields.Activity.MODIFIED_BY, fields),
                VERSION.string(Fields.Activity.ANNOTATION, fields),
                VERSION.date(Fields.Activity.MODIFICATION_DATE, fields),
                VERSION.date(Fields.Activity.EXPIRATION_DATE, fields),
                PersistenceState.SAVED
            ));
        }
        return attachment;
    }

    @Override protected String type() { return "attachment"; }
    @Override protected String idColumn() { return AttachmentFields.Attachment.ID.columnName(); }
    @Override protected String numberColumn() { return AttachmentFields.Measurement.NUMBER.columnName(); }

}
