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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import org.lilyproject.client.LilyClient;
import org.lilyproject.repository.api.FieldType;
import org.lilyproject.repository.api.FieldTypeExistsException;
import org.lilyproject.repository.api.FieldTypeNotFoundException;
import org.lilyproject.repository.api.QName;
import org.lilyproject.repository.api.RecordType;
import org.lilyproject.repository.api.RecordTypeNotFoundException;
import org.lilyproject.repository.api.Repository;
import org.lilyproject.repository.api.Scope;
import org.lilyproject.repository.api.TypeException;
import org.lilyproject.repository.api.TypeManager;
import org.lilyproject.repository.api.ValueType;

import com.mozilla.bugzilla_etl.base.Assert;
import com.mozilla.bugzilla_etl.model.Fields;
import com.mozilla.bugzilla_etl.model.bug.BugFields;

/** Building on {@link Fields} the lily record types are defined herein. */
class Types {

    static final String NS_BETL = "com.mozilla.bugzilla_etl";
    static final String NS_VTAG = "org.lilyproject.vtag";

    enum VTag { LAST, HISTORY }

    static final QName BUG = new QName(NS_BETL, "Bug");
    static final QName FLAG = new QName(NS_BETL, "Flag");

    static final class Params {
        final ValueType type;
        final QName qname;
        final Scope scope;
        final boolean mandatory;

        /**
         * Creates parameters that can be used to create a Lily field type.
         * If one of the fields defined in {@link Fields} should not have a lily field-type,
         * use {@link Params#UNUSED}.
         *
         * @param ns    The LilyCMS namespace to use (also in lily-conf: indexer/indexerconf.xml)
         * @param type  One of the primitive Lily value-types.
         * @param field The enum on whose name to base the field name (will be lowercased).
         * @param scope The LilyCMS versioning scope to use for this field.
         * @param mandatory Are values for this field mandatory (or optional)?
         */
        Params(String ns, ValueType type, Enum<?> field, Scope scope, boolean mandatory) {
            Assert.nonNull(ns, type, field, scope);
            this.type = type;
            this.qname = new QName(ns, field.name().toLowerCase());
            this.scope = scope;
            this.mandatory = mandatory;
        }

        private Params() {
            type = null;
            qname = null;
            scope = null;
            mandatory = false;
        }

        /** These special params indicate no Lily field-type should be created. */
        static final Params UNUSED = new Params();
    }

    final EnumMap<Types.VTag, Params> vTagParams;
    final EnumMap<BugFields.Bug, Params> bugParams;
    final EnumMap<Fields.Activity, Params> versionParams;
    final EnumMap<BugFields.Facet, Params> facetParams;
    final EnumMap<BugFields.Measurement, Params> measurementParams;
    final ValueType longs;
    final ValueType strings;
    final ValueType stringlists;
    final ValueType dates;

    private final PrintStream log;
    private final TypeManager typeManager;


    Types(PrintStream log, Repository repository) {
        typeManager = repository.getTypeManager();
        try {
            longs = typeManager.getValueType("LONG", false, false);
            strings = typeManager.getValueType("STRING", false, false);
            dates = typeManager.getValueType("DATETIME", false, false);
            stringlists = typeManager.getValueType("STRING", true, false);
        } catch (TypeException e) {
            e.printStackTrace();
            log.format("Fatal: value type not found.");
            throw new RuntimeException(e);
        }

        vTagParams = vTagParams();
        bugParams = bugParams();
        versionParams = versionParams();
        facetParams = createFacetParams();
        measurementParams = createMeasurementParams();
        this.log = log;
    }

    /** Get the latest version of the record type for bugs, creating it if not present. */
    RecordType bugType() {
        final Map<Class<? extends Enum<?>>, EnumMap<? extends Enum<?>, Params>> allParams =
            new HashMap<Class<? extends Enum<?>>, EnumMap<? extends Enum<?>, Params>>();
        allParams.put(BugFields.Bug.class, bugParams);
        allParams.put(Fields.Activity.class, versionParams);
        allParams.put(BugFields.Facet.class, facetParams);
        allParams.put(BugFields.Measurement.class, measurementParams);
        allParams.put(Types.VTag.class, vTagParams);
        return createOrGetRecordType(BUG, allParams);
    }

    /** Get the latest version of the flag type for bugs, creating it if not present.
     * @param <T>*/
    RecordType flagType() {
        final Map<Class<? extends Enum<?>>, EnumMap<? extends Enum<?>, Params>> allParams =
            new HashMap<Class<? extends Enum<?>>, EnumMap<? extends Enum<?>, Params>>();
        allParams.put(Types.VTag.class, vTagParams);
        return createOrGetRecordType(FLAG, allParams);
    }


    private RecordType
    createOrGetRecordType(QName typeName,
                          Map<Class<? extends Enum<?>>, EnumMap<? extends Enum<?>, Params>> all) {
        try {
            return typeManager.getRecordTypeByName(typeName, null);
        }
        catch (RecordTypeNotFoundException notFound) { /* that's fine, let's create it... */ }
        catch (Exception error) {
            error.printStackTrace(log);
            log.format("Error: Unexpected problem accessing type (%s).\n", typeName);
            throw new RuntimeException(error);
        }

        log.format("Type (%s) does not exist, creating it.\n", typeName);

        // Actually create the fields and the type.
        final RecordType type;
        try {
            type = typeManager.newRecordType(typeName);
            for (Map.Entry<Class<? extends Enum<?>>, EnumMap<? extends Enum<?>, Params>> pair :
                    all.entrySet()) {
                final EnumMap<?, Params> fields = pair.getValue();
                for (final Enum<?> field : pair.getKey().getEnumConstants()) {
                    final Params params = fields.get(field);
                    // Ensure that every enum constant is handled.
                    if(params == null) {
                        Assert.unreachable("Missing params for field %s.%s -- aborting.",
                                           field.getClass().getSimpleName(),
                                           field.name());
                        break;
                    }
                    if (fields.get(field) == Params.UNUSED) continue;
                    type.addFieldTypeEntry(createOrGetFieldType(params).getId(),
                                           params.mandatory);
                }
            }
            typeManager.createRecordType(type);
        } catch (Exception error) {
            error.printStackTrace();
            log.format("Fatal: Unexpected error creating Bug record type.\n");
            throw new RuntimeException(error);
        }

        return type;
    }

    private FieldType createOrGetFieldType(Params params) {
        FieldType fieldType;
        try {
            try {
                fieldType = typeManager.getFieldTypeByName(params.qname);
            }
            catch (FieldTypeNotFoundException notFound) {
                fieldType = typeManager.newFieldType(params.type, params.qname, params.scope);
                fieldType = typeManager.createFieldType(fieldType);
            }
        }
        catch (FieldTypeExistsException alreadyExists) {
            log.format("Error: Concurrent modification to field type (%s)?\n",
                       params.qname.getName());
            throw new RuntimeException(alreadyExists);
        }
        catch (TypeException typeException) {
            log.format("Error: Unexpected problem with managing field type (%s).\n",
                       params.qname.getName());
            throw new RuntimeException(typeException);
        }
        catch (InterruptedException error) {
            log.println("Fatal: Got interrupted creating field type:");
            error.printStackTrace(log);
            throw new RuntimeException(error);
        }
        return fieldType;
    }

    private void add(EnumMap<VTag, Params> map, VTag field, ValueType type, Scope scope) {
        map.put(field, new Params(NS_VTAG, type, field, scope, false));
    }

    private <T extends Enum<T>> void add(EnumMap<T, Params> map,
                                         T field, ValueType type,
                                         Scope scope,
                                         boolean mandatory) {
        map.put(field, new Params(NS_BETL, type, field, scope, mandatory));
    }


    // General purpose:
    private EnumMap<VTag, Params> vTagParams() {
        final EnumMap<VTag, Params> vTagParams = new EnumMap<VTag, Params>(VTag.class);
        add(vTagParams, VTag.LAST,    longs, Scope.NON_VERSIONED);
        add(vTagParams, VTag.HISTORY, longs, Scope.NON_VERSIONED);
        return vTagParams;
    }

    // Record: Bug
    private EnumMap<BugFields.Bug, Params> bugParams() {
        final EnumMap<BugFields.Bug, Params> bugParams =
            new EnumMap<BugFields.Bug, Params>(BugFields.Bug.class);
        add(bugParams, BugFields.Bug.ID,            longs,   Scope.NON_VERSIONED, true);
        add(bugParams, BugFields.Bug.REPORTED_BY,   strings, Scope.NON_VERSIONED, true);
        add(bugParams, BugFields.Bug.CREATION_DATE, dates, Scope.NON_VERSIONED, true);
        return bugParams;
    }

    private EnumMap<Fields.Activity, Params> versionParams() {
        final EnumMap<Fields.Activity, Params> versionParams =
            new EnumMap<Fields.Activity, Params>(Fields.Activity.class);
        versionParams.put(Fields.Activity.ENTITY_ID, Params.UNUSED);
        versionParams.put(Fields.Activity.PERSISTENCE_STATE, Params.UNUSED);
        add(versionParams, Fields.Activity.ANNOTATION,        strings, Scope.VERSIONED_MUTABLE, false);
        add(versionParams, Fields.Activity.MODIFIED_BY,       strings, Scope.VERSIONED, true);
        add(versionParams, Fields.Activity.MODIFICATION_DATE, dates,   Scope.VERSIONED, true);
        add(versionParams, Fields.Activity.EXPIRATION_DATE,   dates,   Scope.VERSIONED_MUTABLE, true);
        return versionParams;
    }

    private EnumMap<BugFields.Facet, Params> createFacetParams() {
        final EnumMap<BugFields.Facet, Params> facetParams =
            new EnumMap<BugFields.Facet, Params>(BugFields.Facet.class);
        add(facetParams, BugFields.Facet.KEYWORDS,                       stringlists, Scope.VERSIONED, false);
        add(facetParams, BugFields.Facet.GROUPS,                         stringlists, Scope.VERSIONED, false);
        add(facetParams, BugFields.Facet.FLAGS,                          stringlists, Scope.VERSIONED, false);
        add(facetParams, BugFields.Facet.MODIFIED_FIELDS,                stringlists, Scope.VERSIONED, false);
        add(facetParams, BugFields.Facet.STATUS_WHITEBOARD_ITEMS,        stringlists, Scope.VERSIONED, false);
        add(facetParams, BugFields.Facet.CHANGES,                        stringlists, Scope.VERSIONED, false);
        add(facetParams, BugFields.Facet.MAJOR_STATUS_LAST_CHANGED_DATE, dates,       Scope.VERSIONED, false);
        add(facetParams, BugFields.Facet.STATUS_LAST_CHANGED_DATE,       dates,       Scope.VERSIONED, false);
        // The others are strings:
        for (BugFields.Facet field : BugFields.Facet.values()) {
            if (facetParams.containsKey(field)) continue;
            add(facetParams, field, strings, Scope.VERSIONED, false);
        }
        return facetParams;
    }

    private EnumMap<BugFields.Measurement, Params> createMeasurementParams() {
        final EnumMap<BugFields.Measurement, Params> measurementParams =
            new EnumMap<BugFields.Measurement, Params>(BugFields.Measurement.class);
        for (BugFields.Measurement field : BugFields.Measurement.values()) {
            add(measurementParams, field, longs, Scope.VERSIONED_MUTABLE, false);
        }
        return measurementParams;
    }


    /**
     * Recreate record types and field types.
     * Run this to allow starting lily with a non-empty indexerxonf.
     */
    public static void main(final String[] arguments) {
        if (arguments.length != 1 || arguments[0].equals("--help")) {
            System.err.println("Usage java " + Types.class.getName() + " lilyhost:lilyport");
            return;
        }
        LilyClient client;
        try {
            client = new LilyClient(arguments[0], AbstractLilyClient.ZK_TIMEOUT_MS);
            Repository repo = client.getRepository();
            Types types = new Types(System.out, repo);
            types.bugType();
            types.flagType();
        } catch (Exception error) {
            error.printStackTrace();
            System.err.format("Error (%s) creating field- and record types.", error);
            throw new RuntimeException(error);
        }
    }
}
