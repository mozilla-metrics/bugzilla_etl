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

package com.mozilla.bugzilla_etl.di.steps;                                                   // snip
                                                                                             // snip
                                                                                             // snip
import org.pentaho.di.core.RowSet;                                                           // snip
import org.pentaho.di.core.exception.KettleException;                                        // snip
import org.pentaho.di.core.exception.KettleStepException;                                    // snip
import org.pentaho.di.trans.step.StepDataInterface;                                          // snip
import org.pentaho.di.trans.step.StepMetaInterface;                                          // snip
import org.pentaho.di.trans.steps.userdefinedjavaclass.TransformClassBase;                   // snip
import org.pentaho.di.trans.steps.userdefinedjavaclass.UserDefinedJavaClass;                 // snip
import org.pentaho.di.trans.steps.userdefinedjavaclass.UserDefinedJavaClassData;             // snip
import org.pentaho.di.trans.steps.userdefinedjavaclass.UserDefinedJavaClassMeta;             // snip
                                                                                             // snip
                                                                                             // snip
// Allow casts for Janino compatibility (no generic collections).                            // snip
@SuppressWarnings("cast")                                                                    // snip
public class WriteFlagsToLilyStep extends TransformClassBase {                               // snip
                                                                                             // step
    public WriteFlagsToLilyStep(UserDefinedJavaClass parent,                                 // snip
                                UserDefinedJavaClassMeta meta,                               // snip
                                UserDefinedJavaClassData data) throws KettleStepException {  // snip
        super(parent, meta, data);                                                           // snip
    }                                                                                        // step

    /**
     * source: {@link com.mozilla.bugzilla_etl.di.steps.WriteFlagsToLilyStep}
     *
     * Input step(s):
     *    * 0: A stream of flags (normalized)
     * Output step(s):
     *    (none)
     */
    private com.mozilla.bugzilla_etl.di.FlagSource source;
    private com.mozilla.bugzilla_etl.lily.FlagDestination destination;
    private int count = 0;

    public boolean processRow(StepMetaInterface smi, StepDataInterface sdi) throws KettleException {
        if (first) {
            first = false;
            RowSet input = (RowSet)this.getInputRowSets().get(0);
            source = new com.mozilla.bugzilla_etl.di.FlagSource(this, input);
            final String lilyConnectString = getParameter("lily_zk_connect_string");
            System.out.print("Connecting to LilyCMS (zookeeper at '" + lilyConnectString + "')\n");
            destination = new com.mozilla.bugzilla_etl.lily.FlagDestination(System.out,
                                                                            lilyConnectString);
        }
        if (!source.hasMore()) {
            setOutputDone();
            System.out.print("Wrote " + count + " flag records to Lily.");
            return false;
        }
        try {
            destination.send(source.receive());
            ++count;
            System.out.print("Wrote " + count + " flag records to Lily.");
            incrementLinesWritten();
        }
        catch (org.lilycms.repository.api.RepositoryException error) {
            error.printStackTrace(System.out);
            System.out.format("Repository Error (%s) (see stack trace above),",
                              new Object[] { error.getClass().getSimpleName() });
            throw new RuntimeException(error);
        }
        return true;
    }

}                                                                                            // snip
