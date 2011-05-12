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

package com.mozilla.bugzilla_etl.di.bug.steps;                                                   // snip
                                                                                             // snip
                                                                                             // snip
import org.pentaho.di.core.exception.KettleException;                                        // snip
import org.pentaho.di.core.exception.KettleStepException;                                    // snip
import org.pentaho.di.core.row.RowMeta;                                                      // snip
import org.pentaho.di.trans.step.StepDataInterface;                                          // snip
import org.pentaho.di.trans.step.StepMetaInterface;                                          // snip
import org.pentaho.di.trans.steps.userdefinedjavaclass.TransformClassBase;                   // snip
import org.pentaho.di.trans.steps.userdefinedjavaclass.UserDefinedJavaClass;                 // snip
import org.pentaho.di.trans.steps.userdefinedjavaclass.UserDefinedJavaClassData;             // snip
import org.pentaho.di.trans.steps.userdefinedjavaclass.UserDefinedJavaClassMeta;             // snip
                                                                                             // snip
                                                                                             // snip
public class RebuildVersionsStep extends TransformClassBase {                                // snip
                                                                                             // snip
    public RebuildVersionsStep(UserDefinedJavaClass parent,                                  // snip
                               UserDefinedJavaClassMeta meta,                                // snip
                               UserDefinedJavaClassData data) throws KettleStepException {   // snip
        super(parent, meta, data);                                                           // snip
    }                                                                                        // snip

    /**
     * source: {@link com.mozilla.bugzilla_etl.di.steps.RebuildVersionsStep}
     *
     * This class corresponds to the actual contents of the UserDefinedJavaClass
     * step editor window. Remove the lines ending in "// snip" when pasting the
     * contents there.
     *
     * Because it is compiled at Runtime by Janino, the language constructs are
     * restricted: http://docs.codehaus.org/display/JANINO/Home#Home-limitations
     *
     * For incremental update of existing bugs, a lookup is used:
     * - If "IS_IMPORT" is set, an empty dummy lookup is used.
     * - If "ES_NODES" is set, the elasticsearch lookup is used.
     * - If "LILY_ZK_NODES" is set, the lily repository will be used.
     * - Otherwise an error is raised.
     *
     * Input: A stream of bugs and bug activities.
     *        The activities of each bug are grouped together.
     * Output: A stream of complete bug versions.
     */
    private static final String IN_STEP = "Prepare History Input";
    private static final String IN_STEP_STATUS_LOOKUP = "Get Major Status Lookup";
    private com.mozilla.bugzilla_etl.di.bug.BugDestination destination;
    private com.mozilla.bugzilla_etl.di.bug.RebuilderBugSource source;
    private com.mozilla.bugzilla_etl.di.bug.IBugLookup lookup;

    public boolean processRow(StepMetaInterface smi, StepDataInterface sdi) throws KettleException {
        if (first) {
            first = false;
            final RowMeta outputRowMeta = new RowMeta();
            data.outputRowMeta = outputRowMeta;
            meta.getFields(data.outputRowMeta, getStepname(), null, null, parent);

            java.io.PrintStream log = System.out;
            String isInitialImport = getParameter("IS_IMPORT");
            String esNodes = getParameter("ES_NODES");
            String lilyZkNodes = getParameter("LILY_ZK_NODES");

            logBasic(String.format(
                    "Params: IS_IMPORT=%s, ES_NODES=%s, LILY_ZK_NODES=%s",
                    new Object[]{isInitialImport, esNodes, lilyZkNodes}
            ));
            if ("true".equals(isInitialImport)) {
                log.println("Rebuilder Lookup: none (initial_import)");
                lookup = new com.mozilla.bugzilla_etl.di.bug.EmptyBugLookup();
            }
            else if (esNodes != null && esNodes.length() > 0) {
                log.println("Rebuilder Lookup: elasticsearch");
                lookup =
                    new com.mozilla.bugzilla_etl.es.BugLookup(log, esNodes);
            }
            else if (lilyZkNodes != null && lilyZkNodes.length() > 0) {
                log.println("Rebuilder Lookup: Lily repository");
                lookup =
                    new com.mozilla.bugzilla_etl.lily.LilyBugLookup(log,
                                                                    lilyZkNodes);
            }
            else {
                logError("RebuildVersionStep Lookup Configuration Invalid!");
                com.mozilla.bugzilla_etl.base.Assert.unreachable();
            }
            source = new com.mozilla.bugzilla_etl.di.bug.RebuilderBugSource(
                         this,
                         findInputRowSet(IN_STEP),
                         findInputRowSet(IN_STEP_STATUS_LOOKUP),
                         lookup
            );

            destination = new com.mozilla.bugzilla_etl.di.bug.BugDestination(this, outputRowMeta);
        }
        if (!source.hasMore()) {
            setOutputDone();
            source.printDiagnostics();
            return false;
        }
        destination.send(source.receive());
        return true;
    }

    public static String[] getInfoSteps() {
        return new String[] { IN_STEP_STATUS_LOOKUP };
    }

}                                                                                            // snip
