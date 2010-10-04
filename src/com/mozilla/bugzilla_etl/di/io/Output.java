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

package com.mozilla.bugzilla_etl.di.io;

import org.pentaho.di.core.exception.KettleStepException;
import org.pentaho.di.core.row.RowDataUtil;
import org.pentaho.di.core.row.RowMeta;
import org.pentaho.di.trans.steps.userdefinedjavaclass.FieldHelper;
import org.pentaho.di.trans.steps.userdefinedjavaclass.TransformClassBase;

import com.mozilla.bugzilla_etl.base.Assert;
import com.mozilla.bugzilla_etl.base.Fields;

public class Output {

    public Output(TransformClassBase step, RowMeta rowMeta) {
        Assert.nonNull(step, rowMeta);
        this.step = step;
        this.rowMeta = rowMeta;
        this.row = RowDataUtil.allocateRowData(rowMeta.size());
        this.helpers = new Helpers(rowMeta);
    }

    public <T extends Enum<T> & Fields.Field>  Cell cell(T field) {
        return new Cell(helpers.helper(field));
    }

    public class Cell {
        private final FieldHelper helper;
        public Cell(FieldHelper helper) { this.helper = helper; }
        public void set(Enum<?> o) {
            helper.setValue(row, o.name());
        }
        public void set(Object o) {
            helper.setValue(row, o);
        }
    }

    /** Flush current row, setup next. */
    public void next() throws KettleStepException {
        step.putRow(rowMeta, row);
        step.incrementLinesOutput();
        this.row = RowDataUtil.allocateRowData(rowMeta.size());
    }

    private final Helpers helpers;
    private final TransformClassBase step;
    private RowMeta rowMeta;
    private Object[] row;
}
