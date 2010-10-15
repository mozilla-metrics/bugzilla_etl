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

import org.lilycms.client.LilyClient;
import org.lilycms.repository.api.FieldType;
import org.lilycms.repository.api.FieldTypeExistsException;
import org.lilycms.repository.api.FieldTypeNotFoundException;
import org.lilycms.repository.api.QName;
import org.lilycms.repository.api.RecordType;
import org.lilycms.repository.api.RecordTypeNotFoundException;
import org.lilycms.repository.api.Repository;
import org.lilycms.repository.api.Scope;
import org.lilycms.repository.api.TypeException;
import org.lilycms.repository.api.TypeManager;
import org.lilycms.repository.api.ValueType;

import com.mozilla.bugzilla_etl.base.Assert;
import com.mozilla.bugzilla_etl.base.Fields;

/** Building on {@link Fields} the lily record types are defined herein. */
class Types {

    static final String NS = "com.mozilla.bugzilla_etl";
    static final String NS_VTAG = "org.lilycms.vtag";

    enum VTag { LAST, HISTORY }

    static final QName BUG = new QName(NS, "Bug");
    static final QName FLAG = new QName(NS, "Flag");

    static final class Params {
        final ValueType type;
        final QName qname;
        final Scope scope;

        /**
         * Creates parameters that can be used to create a Lily field type.
         * If one of the fields defined in {@link Fields} should not have a lily field-type,
         * use {@link Params#UNUSED}.
         *
         * @param ns    The LilyCMS namespace to use (also in lily-conf: indexer/indexerconf.xml)
         * @param type  One of the primitive Lily value-types.
         * @param field The enum on whose name to base the field name (will be lowercased).
         * @param scope The LilyCMS versioning scope to use for this field.
         */
        Params(String ns, ValueType type, Enum<?> field, Scope scope) {
            Assert.nonNull(ns, type, field, scope);
            this.type = type;
            this.qname = new QName(ns, field.name().toLowerCase());
            this.scope = scope;
        }

        private Params() {
            type = null;
            qname = null;
            scope = null;
        }

        /** These special params indicate no Lily field-type should be created. */
        static final Params UNUSED = new Params();
    }

    final EnumMap<Types.VTag, Params> vTagParams;
    final EnumMap<Fields.Bug, Params> bugParams;
    final EnumMap<Fields.Version, Params> versionParams;
    final EnumMap<Fields.Facet, Params> facetParams;
    final EnumMap<Fields.Measurement, Params> measurementParams;
    final EnumMap<Fields.Flag, Params> flagParams;
    final EnumMap<Fields.User, Params> userParams;
    final ValueType longs;
    final ValueType strings;
    final ValueType stringlists;
    final ValueType dates;

    private final PrintStream log;
    private final TypeManager typeManager;


    Types(PrintStream log, Repository repository) {
        typeManager = repository.getTypeManager();
        longs = typeManager.getValueType("LONG", false, false);
        strings = typeManager.getValueType("STRING", false, false);
        dates = typeManager.getValueType("DATETIME", false, false);
        stringlists = typeManager.getValueType("STRING", true, false);

        vTagParams = vTagParams();
        bugParams = bugParams();
        versionParams = versionParams();
        facetParams = createFacetParams();
        measurementParams = createMeasurementParams();
        flagParams = flagParams();
        userParams = userParams();
        this.log = log;
    }

    /** Get the latest version of the record type for bugs, creating it if not present. */
    RecordType bugType() {
        final Map<Class<? extends Enum<?>>, EnumMap<? extends Enum<?>, Params>> allParams =
            new HashMap<Class<? extends Enum<?>>, EnumMap<? extends Enum<?>, Params>>();
        allParams.put(Fields.Bug.class, bugParams);
        allParams.put(Fields.Version.class, versionParams);
        allParams.put(Fields.Facet.class, facetParams);
        allParams.put(Fields.Measurement.class, measurementParams);
        allParams.put(Types.VTag.class, vTagParams);
        return createOrGetRecordType(BUG, allParams);
    }

    /** Get the latest version of the flag type for bugs, creating it if not present.
     * @param <T>*/
    RecordType flagType() {
        final Map<Class<? extends Enum<?>>, EnumMap<? extends Enum<?>, Params>> allParams =
            new HashMap<Class<? extends Enum<?>>, EnumMap<? extends Enum<?>, Params>>();
        allParams.put(Fields.Flag.class, flagParams);
        allParams.put(Types.VTag.class, vTagParams);
        return createOrGetRecordType(FLAG, allParams);
    }


    private RecordType
    createOrGetRecordType(QName typeName,
                          Map<Class<? extends Enum<?>>, EnumMap<? extends Enum<?>, Params>> all)
    {
        try {
            return typeManager.getRecordTypeByName(typeName, null);
        }
        catch (RecordTypeNotFoundException notFound) { /* that's fine, let's create it... */ }
        catch (TypeException typeException) {
            log.format("Error: Unexpected problem accessing type (%s).\n", typeName);
            throw new RuntimeException(typeException);
        }

        log.format("Type (%s) does not exist, creating it.\n", typeName);

        // Actually create the fields and the type.
        final RecordType type = typeManager.newRecordType(typeName);
        for (Map.Entry<Class<? extends Enum<?>>, EnumMap<? extends Enum<?>, Params>> pair :
                all.entrySet()) {
            final EnumMap<?, Params> fields = pair.getValue();
            for (final Enum<?> field : pair.getKey().getEnumConstants()) {
                final Params params = fields.get(field);
                // Ensure that every enum constant is handled.
                if(params == null) {
                    log.format("Missing params for field %s.%s -- aborting.",
                               field.getClass().getSimpleName(),
                               field.name());
                }
                if (fields.get(field) == Params.UNUSED) continue;
                type.addFieldTypeEntry(createOrGetFieldType(params).getId(), true);
            }
        }

        try {
            typeManager.createRecordType(type);
        } catch (Exception error) {
            error.printStackTrace();
            log.format("Error: Unexpected error creating Bug record type.\n");
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
        return fieldType;
    }

    private void add(EnumMap<VTag, Params> map, VTag field, ValueType type, Scope scope) {
        map.put(field, new Params(NS_VTAG, type, field, scope));
    }

    private <T extends Enum<T>> void add(EnumMap<T, Params> map, T field, ValueType type, Scope scope) {
        map.put(field, new Params(NS, type, field, scope));
    }


    // General purpose:
    private EnumMap<VTag, Params> vTagParams() {
        final EnumMap<VTag, Params> vTagParams = new EnumMap<VTag, Params>(VTag.class);
        add(vTagParams, VTag.LAST,    longs, Scope.NON_VERSIONED);
        add(vTagParams, VTag.HISTORY, longs, Scope.NON_VERSIONED);
        return vTagParams;
    }

    // Record: Bug
    private EnumMap<Fields.Bug, Params> bugParams() {
        final EnumMap<Fields.Bug, Params> bugParams =
            new EnumMap<Fields.Bug, Params>(Fields.Bug.class);
        bugParams.put(Fields.Bug.CREATION_DATE, Params.UNUSED);
        add(bugParams, Fields.Bug.ID, longs, Scope.NON_VERSIONED);
        add(bugParams, Fields.Bug.REPORTED_BY, strings, Scope.NON_VERSIONED);
        return bugParams;
    }

    private EnumMap<Fields.Version, Params> versionParams() {
        final EnumMap<Fields.Version, Params> versionParams =
            new EnumMap<Fields.Version, Params>(Fields.Version.class);
        versionParams.put(Fields.Version.BUG_ID, Params.UNUSED);
        versionParams.put(Fields.Version.PERSISTENCE_STATE, Params.UNUSED);
        add(versionParams, Fields.Version.ANNOTATION,        strings, Scope.VERSIONED_MUTABLE);
        add(versionParams, Fields.Version.MODIFIED_BY,       strings, Scope.VERSIONED);
        add(versionParams, Fields.Version.MODIFICATION_DATE, dates,   Scope.VERSIONED);
        add(versionParams, Fields.Version.EXPIRATION_DATE,   dates,   Scope.VERSIONED_MUTABLE);
        return versionParams;
    }

    private EnumMap<Fields.Facet, Params> createFacetParams() {
        final EnumMap<Fields.Facet, Params> facetParams =
            new EnumMap<Fields.Facet, Params>(Fields.Facet.class);
        add(facetParams, Fields.Facet.KEYWORDS, stringlists, Scope.VERSIONED);
        add(facetParams, Fields.Facet.FLAGS,    stringlists,   Scope.VERSIONED);
        for (Fields.Facet field : Fields.Facet.values()) {
            if (facetParams.containsKey(field)) continue;
            add(facetParams, field, strings, Scope.VERSIONED);
        }
        return facetParams;
    }

    private EnumMap<Fields.Measurement, Params> createMeasurementParams() {
        final EnumMap<Fields.Measurement, Params> measurementParams =
            new EnumMap<Fields.Measurement, Params>(Fields.Measurement.class);
        for (Fields.Measurement field : Fields.Measurement.values()) {
            add(measurementParams, field, longs, Scope.VERSIONED_MUTABLE);
        }
        return measurementParams;
    }


    // Record-type: Flag
    private EnumMap<Fields.Flag, Params> flagParams() {
        final EnumMap<Fields.Flag, Params> flagParams =
            new EnumMap<Fields.Flag, Params>(Fields.Flag.class);
        add(flagParams, Fields.Flag.ID, longs, Scope.NON_VERSIONED);
        add(flagParams, Fields.Flag.NAME, strings, Scope.VERSIONED);
        add(flagParams, Fields.Flag.STATUS, strings, Scope.NON_VERSIONED);
        return flagParams;
    }

    // Record-type: User
    private EnumMap<Fields.User, Params> userParams() {
        final EnumMap<Fields.User, Params> userParams;
        userParams = new EnumMap<Fields.User, Params>(Fields.User.class);
        add(userParams, Fields.User.ID,            longs,   Scope.NON_VERSIONED);
        add(userParams, Fields.User.CREATION_DATE, longs,   Scope.NON_VERSIONED);
        add(userParams, Fields.User.EMAIL,         strings, Scope.VERSIONED);
        add(userParams, Fields.User.NICK,          strings, Scope.VERSIONED);
        return userParams;
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
