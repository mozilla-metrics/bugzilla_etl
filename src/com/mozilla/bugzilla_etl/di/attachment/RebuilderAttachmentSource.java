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

package com.mozilla.bugzilla_etl.di.attachment;

import java.util.Date;
import java.util.EnumMap;

import org.pentaho.di.core.RowSet;
import org.pentaho.di.core.exception.KettleStepException;
import org.pentaho.di.core.exception.KettleValueException;
import org.pentaho.di.trans.steps.userdefinedjavaclass.TransformClassBase;

import com.mozilla.bugzilla_etl.base.Counter;
import com.mozilla.bugzilla_etl.base.Lookup;
import com.mozilla.bugzilla_etl.di.AbstractSource;
import com.mozilla.bugzilla_etl.di.Converters;
import com.mozilla.bugzilla_etl.di.Rebuilder;
import com.mozilla.bugzilla_etl.di.io.Input;
import com.mozilla.bugzilla_etl.model.attachment.Attachment;
import com.mozilla.bugzilla_etl.model.attachment.AttachmentFields;
import com.mozilla.bugzilla_etl.model.attachment.AttachmentVersion;
import com.mozilla.bugzilla_etl.model.attachment.Request;


/** Does the work for the Rebuild-History Step (attachments). */
public class RebuilderAttachmentSource extends AbstractSource<Attachment> {

    public RebuilderAttachmentSource(TransformClassBase step,
                                     RowSet bugs,
                                     RowSet majorStatusLookup,
                                     final Lookup<Attachment, ? extends Exception> lookup)
    throws KettleStepException {
        super(step, bugs);
        rebuilder = new AttachmentRebuilder(step, input, majorStatusLookup, lookup);
    }

    /** Are there more bugs in the input? */
    @Override
    public boolean hasMore() {
        return !input.empty();
    }

    /** Assemble a versioned bug from bug state plus activities. */
    @Override
    public Attachment receive() throws KettleValueException, KettleStepException {
        Attachment attachment = rebuilder.fromRows();
        // FIXME: get useful value for isNew
        boolean isNew = false;
        counter.count(attachment, isNew);
        return attachment;
    }

    private final AttachmentRebuilder rebuilder;
    private final Counter counter = new Counter(getClass().getSimpleName());

    public void printDiagnostics() {
        counter.print();
        rebuilder.printConflictCounts();
    }

    static class AttachmentRebuilder extends Rebuilder<Attachment, AttachmentVersion,
                                                       AttachmentFields.Facet, Request> {

        public AttachmentRebuilder(TransformClassBase step, Input input, RowSet majorStatusLookup,
                                   Lookup<Attachment, ? extends Exception> lookup) {
            super(input, lookup, Converters.REQUESTS);
        }

        @Override protected Creation base(final Input input) throws KettleValueException {
            return new Creation() {{
                id = input.cell(AttachmentFields.Attachment.ID).longValue();
                date = input.cell(AttachmentFields.Attachment.SUBMISSION_DATE).dateValue();
                creator = input.cell(AttachmentFields.Attachment.SUBMITTED_BY).stringValue();
            }};
        }

        @Override protected Attachment get(final Creation base, final Input input)
        throws KettleValueException {
            System.out.format("AttachmentRebuilder: processing attachment %s\n", base.id);
            final Long bugId = input.cell(AttachmentFields.Attachment.BUG_ID).longValue();
            return new Attachment(base.id, bugId, base.creator, base.date);
        }

        @Override protected void updateFacetsAndMeasurements(Attachment attachment, Date now) {
            attachment.updateFacetsAndMeasurements(now);
        }

        @Override protected EnumMap<AttachmentFields.Facet, String> createFacets() {
            return AttachmentVersion.createFacets();
        }

        @Override protected boolean isComputed(AttachmentFields.Facet facet) {
            return facet.isComputed;
        }

        @Override protected AttachmentFields.Facet flagsFacet() {
            return AttachmentFields.Facet.REQUESTS;
        }

    }
}