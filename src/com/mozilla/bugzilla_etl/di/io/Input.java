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

import java.lang.reflect.Method;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.pentaho.di.core.RowSet;
import org.pentaho.di.core.exception.KettleStepException;
import org.pentaho.di.core.exception.KettleValueException;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.trans.steps.userdefinedjavaclass.FieldHelper;
import org.pentaho.di.trans.steps.userdefinedjavaclass.TransformClassBase;

import com.mozilla.bugzilla_etl.base.Assert;
import com.mozilla.bugzilla_etl.base.Pair;
import com.mozilla.bugzilla_etl.model.Field;
import com.mozilla.bugzilla_etl.model.Fields;
import com.mozilla.bugzilla_etl.model.attachment.AttachmentFields;
import com.mozilla.bugzilla_etl.model.bug.BugFields;


/** Access to input columns goes through this. */
public class Input {

    public Input(TransformClassBase step, RowSet rowSet) throws KettleStepException {
        Assert.nonNull(step, rowSet);
        this.step = step;
        this.rowSet = rowSet;
        // Apparently we can only access the row-meta after reading the first row.
        next = step.getRowFrom(rowSet);
        step.incrementLinesInput();
        this.next();
        RowMetaInterface rowMeta = rowSet.getRowMeta();
        if (rowMeta != null) this.helpers = new Helpers(rowMeta);
    }

    /**
     * Fetches the next row and returns it, or <tt>null</tt> if there is none.
     * @throws KettleStepException If getting the next row fails.
     */
    public boolean next() throws KettleStepException {
        current = next;
        if (current != null) {
            next = step.getRowFrom(rowSet);
            step.incrementLinesInput();
        }
        return current != null;
    }

    public boolean empty() {
        return current == null;
    }

    /** Allows to keep a handle to a row. */
    public Row row() {
        return new Row(current);
    }

    public class Row {
        private final Object[] values;
        Row(final Object[] data) {
            Assert.check(data != null);
            values = data;
        }

        public <T extends Enum<T> & Field> Cell cell(T field) {
            return new Cell(values, helpers.helper(field));
        }

        public Cell cell(Field field, Fields.Column column) {
            return new Cell(values, helpers.helper(field, column));
        }

    }

    public <T extends Enum<T> & Field> Cell cell(T field) {
        return new Cell(current, helpers.helper(field));
    }

    public Cell cell(BugFields.Facet field, Fields.Column column) {
        return new Cell(current, helpers.helper(field, column));
    }

    public Cell cell(AttachmentFields.Facet field, Fields.Column column) {
        return new Cell(current, helpers.helper(field, column));
    }

    public class Cell {
        private final FieldHelper helper;
        private final Object[] values;
        public Cell(final Object[] data, final FieldHelper helper) {
            Assert.nonNull(data, helper);
            this.values = data;
            this.helper = helper;
        }

        public Long longValue() throws KettleValueException {
            return helper.getInteger(values);
        }

        /**
         * @return the cell value as a string.
         *         If <tt>null</tt>, the empty string is returned as PDI cannot distinguish
         *         these anyway.
         */
        public String stringValue() throws KettleValueException {
            final String value = helper.getString(values);
            if (value == null) return "";
            return value;
        }

        public Date dateValue() throws KettleValueException {
            return helper.getDate(values);
        }

        public boolean booleanValue() throws KettleValueException {
            Boolean value = helper.getBoolean(values);
            if (value == null) return false;
            return value.booleanValue();
        }

        /** Yeah, this is about as unsafe as it gets. */
        @SuppressWarnings({ "rawtypes", "unchecked" })
        public <T extends Enum<T>> T enumValue(Class<T> type) throws KettleValueException {
            final String name = helper.getString(values);
            if (name == "") return null;
            final Pair<Class<?>, String> key = new Pair(type, name);
            if (!enumCache.containsKey(key)) {
                try {
                    final Method valueOf = type.getDeclaredMethod("valueOf", String.class);
                    enumCache.put(key, valueOf.invoke(null, name));
                } catch (Exception e) {
                    e.printStackTrace();
                    Assert.unreachable("Cannot decode enum of type %s from name '%s'.", type, name);
                }
            }
            return (T) enumCache.get(key);
        }
    }

    /** Avoid unnecessary reflection. */
    final Map<Pair<Class<?>, String>, Object> enumCache
        = new HashMap<Pair<Class<?>, String>, Object>();

    private final TransformClassBase step;
    private final RowSet rowSet;
    Helpers helpers;
    private Object[] current;
    private Object[] next;
}


