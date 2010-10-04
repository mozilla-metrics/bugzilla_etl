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

package com.mozilla.bugzilla_etl.di;

import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.row.RowMeta;
import org.pentaho.di.trans.steps.userdefinedjavaclass.TransformClassBase;

import com.mozilla.bugzilla_etl.base.Bug;
import com.mozilla.bugzilla_etl.base.Fields;
import com.mozilla.bugzilla_etl.base.Version;

public class BugDestination extends AbstractDestination<Bug> {

    public BugDestination(TransformClassBase fromStep, RowMeta out) { super(fromStep, out); }

    public void send(Bug bug) throws KettleException {
        for (Version version : bug) {
            output.cell(Fields.Bug.ID).set(bug.id());
            output.cell(Fields.Bug.REPORTED_BY).set(bug.reporter());
            output.cell(Fields.Version.MODIFICATION_DATE).set(version.from());
            output.cell(Fields.Version.EXPIRATION_DATE).set(version.to());
            output.cell(Fields.Version.ANNOTATION).set(version.annotation());
            output.cell(Fields.Version.MODIFIED_BY).set(version.author());
            output.cell(Fields.Version.PERSISTENCE_STATE).set(version.persistenceState());

            for (Fields.Facet facet : Fields.Facet.values()) {
                output.cell(facet).set(version.facets().get(facet));
            }
            for (Fields.Measurement measurement : Fields.Measurement.values()) {
                output.cell(measurement).set(version.measurements().get(measurement));
            }
            output.next();
        }
    }

}
