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

import java.util.EnumMap;
import java.util.Map;

import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.trans.steps.userdefinedjavaclass.FieldHelper;

import com.mozilla.bugzilla_etl.base.Assert;
import com.mozilla.bugzilla_etl.model.Family;
import com.mozilla.bugzilla_etl.model.Field;
import com.mozilla.bugzilla_etl.model.Fields;
import com.mozilla.bugzilla_etl.model.Fields.Column;


/**
 * Provides and caches field helpers to conveniently access PDI stream cells
 * for the Fields used by our model.
 */
class Helpers {

    // We know that Fields are always enums (java does not allow us to state this).
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public FieldHelper helper(Field field, Fields.Column column) {
        if (!helpers.containsKey(field.family())) {
            helpers.put(field.family(), new FieldHelpers(field.family().fields));
        }
        return helpers.get(field.family()).get(field, column);
    }

    public FieldHelper helper(Field field) {
        return helper(field, Column.LATEST);
    }

    Helpers(RowMetaInterface rowMeta) {
        Assert.nonNull(rowMeta);
        this.rowMeta = rowMeta;
    }

    private class FieldHelpers<T extends Enum<T>> {
        final EnumMap<T, ColumnHelpers> helpers;

        FieldHelpers(Class<T> fieldType) {
            helpers = new EnumMap<T, ColumnHelpers>(fieldType);
        }

        @SuppressWarnings("unchecked")
        FieldHelper get(final Field field, final Column column) {
            // We know that Fields are always enums (java does not allow us to state this):
            @SuppressWarnings("rawtypes") Map map = helpers;
            if (!helpers.containsKey(field)) map.put(field, new ColumnHelpers(field));
            return helpers.get(field).get(column);
        }
    }

    private class ColumnHelpers {
        final EnumMap<Column, FieldHelper> helpers = new EnumMap<Column, FieldHelper>(Column.class);
        final String columnName;

        private ColumnHelpers(Field field) {
            columnName = field.columnName();
        }

        FieldHelper get(final Column column) {
            if (helpers.get(column) == null)
                helpers.put(column, new FieldHelper(rowMeta, columnName));
            return helpers.get(column);
        }
    }

    private EnumMap<Family, FieldHelpers<? extends Enum<?>>> helpers =
        new EnumMap<Family, FieldHelpers<? extends Enum<?>>>(Family.class);

    private final RowMetaInterface rowMeta;
}
