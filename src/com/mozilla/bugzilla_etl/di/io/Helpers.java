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
import com.mozilla.bugzilla_etl.model.attachment.AttachmentFields;
import com.mozilla.bugzilla_etl.model.bug.BugFields;


/**
 * Provides the field helpers that are needed for the Input/Output api.
 * This class does for PDI integration roughly what Types does for Lily
 * integration. Whenever groups of fields ("families") are added/removed, this
 * class should be checked.
 *
 * Using various EnumMaps for the individual field types causes some
 * boilerplate code, but allows for constant time access (without hashing) and
 * for a certain degree of type safety.
 */
class Helpers {

    <T extends Enum<T> & Field> FieldHelper helper(T field) {
        if (!familyHelpers(field).containsKey(field)) {
            familyHelpers(field).put(field, new FieldHelper(rowMeta, field.columnName()));
        }
        return familyHelpers(field).get(field);
    }

    public FieldHelper helper(BugFields.Facet facet,
                              BugFields.Facet.Column column) {
        if (!facetHelpers.get(facet).containsKey(column)) {
            facetHelpers.get(facet).put(column,
                                        new FieldHelper(rowMeta, facet.columnNames.get(column)));
        }
        return facetHelpers.get(facet).get(column);
    }

    public FieldHelper helper(AttachmentFields.Facet facet,
                              AttachmentFields.Facet.Column column) {
        if (!attachmentFacetHelpers.get(facet).containsKey(column)) {
            attachmentFacetHelpers.get(facet).put(column,
                                        new FieldHelper(rowMeta, facet.columnNames.get(column)));
        }
        return attachmentFacetHelpers.get(facet).get(column);
    }

    Helpers(RowMetaInterface rowMeta) {
        Assert.nonNull(rowMeta);
        this.rowMeta = rowMeta;

        // Ensure that we create maps for all families.
        for (Family family : Family.values()) {
            switch (family) {
                case BUG:         prepare(BugFields.Bug.class,         family); break;
                case BUG_ACTIVITY:     prepare(BugFields.Activity.class,    family); break;
                case MEASUREMENT: prepare(BugFields.Measurement.class, family); break;
                case FACET:       prepare(BugFields.Facet.class,       family); break;
                case ATTACHMENT:     prepare(AttachmentFields.Attachment.class, family); break;
                case ATTACH_FACET:   prepare(AttachmentFields.Facet.class, family); break;
                case ATTACH_MEASURE: prepare(AttachmentFields.Measurement.class, family); break;
                case ATTACH_ACTIVITY: prepare(AttachmentFields.Activity.class, family); break;
                default: Assert.unreachable();
            }
        }

        // Also create maps for the columns specific to facets in advance.
        for (BugFields.Facet facet : BugFields.Facet.values()) {
            Map<BugFields.Facet.Column, FieldHelper> cache =
                new EnumMap<BugFields.Facet.Column, FieldHelper>(BugFields.Facet.Column.class);
            facetHelpers.put(facet, cache);
        }
    }

    private <T extends Enum<T> & Field> void prepare(Class<T> cls, Family family) {
        EnumMap<T, FieldHelper> cache = new EnumMap<T, FieldHelper>(cls);
        helpers.put(family, cache);
    }

    /**
     * We know this works, we've put the stuff in by family in the constructor. Of course,
     * all implementations of Fields.Field need to play along and not use the same family twice.
     */
    @SuppressWarnings("unchecked")
    private <T extends Enum<T> & Field> EnumMap<T, FieldHelper> familyHelpers(T field) {
        return (EnumMap<T, FieldHelper>) helpers.get(field.family());
    }

    // Contains all helpers (except for facets):
    private EnumMap<Family, EnumMap<? extends Enum<?>, FieldHelper>> helpers =
        new EnumMap<Family, EnumMap<? extends Enum<?>, FieldHelper>>(Family.class);

    // Helpers for facets (which can have multiple input columns):
    private final Map<BugFields.Facet, Map<BugFields.Facet.Column, FieldHelper>> facetHelpers =
        new EnumMap<BugFields.Facet, Map<BugFields.Facet.Column, FieldHelper>>(BugFields.Facet.class);

    // Helpers for attachment facets.
    private final Map<AttachmentFields.Facet, Map<AttachmentFields.Facet.Column, FieldHelper>> attachmentFacetHelpers =
        new EnumMap<AttachmentFields.Facet, Map<AttachmentFields.Facet.Column, FieldHelper>>(AttachmentFields.Facet.class);

    private final RowMetaInterface rowMeta;
}
