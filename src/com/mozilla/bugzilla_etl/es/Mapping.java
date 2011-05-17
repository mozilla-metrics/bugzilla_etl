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

package com.mozilla.bugzilla_etl.es;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.elasticsearch.common.xcontent.XContentBuilder;

import com.mozilla.bugzilla_etl.base.Assert;
import com.mozilla.bugzilla_etl.di.Converters;
import com.mozilla.bugzilla_etl.di.Converters.Converter;
import com.mozilla.bugzilla_etl.model.Field;


public interface Mapping<T extends Field> {

    String string(T field, Map<String, Object> fields);
    Date date(T field, Map<String, Object> fields);
    String csv(T field, Map<String, Object> fields);
    Long integer(T field, Map<String, Object> fields);
    XContentBuilder append(XContentBuilder builder, T field, Object value) throws IOException;


    public static enum Conv { STRING, STRINGLIST, DATE, INTEGER, BOOLEAN, UNUSED }


    public static abstract class BaseMapping<T extends Field> implements Mapping<T> {

        @Override
        public String string(T field, Map<String, Object> fields) {
            final String name = name(field);
            if (!fields.containsKey(name)) return null;
            return string(field, fields.get(name));
        }

        @Override
        public Date date(T field, Map<String, Object> fields) {
            final String name = name(field);
            if (!fields.containsKey(name)) return null;
            return date(field, fields.get(name));
        }

        @Override
        public String csv(T field, Map<String, Object> fields) {
            final String name = name(field);
            if (!fields.containsKey(name)) return null;
            return csv(field, fields.get(name));
        }

        @Override
        public Long integer(T field, Map<String, Object> fields) {
            final String name = name(field);
            if (!fields.containsKey(name)) return null;
            return integer(field, fields.get(name));
        }


        @Override
        public XContentBuilder append(XContentBuilder builder,
                                      T field, Object value) throws IOException {
            switch (conversions.get(field)) {
                case DATE:
                    return builder.field(field.columnName(), (Date) value);
                case STRING:
                    if (value == null) value = "<none>";
                    return builder.field(field.columnName(), (String) value);
                case INTEGER:
                    return builder.field(field.columnName(), (Long) value);
                case BOOLEAN:
                    return builder.field(field.columnName(), ((Boolean)value).booleanValue());
                case UNUSED:
                    return builder;
                default:
                    return Assert.unreachable(XContentBuilder.class);
            }
        }

        private String name(T field) {
            Assert.nonNull(field);
            return field.columnName();
        }

        private String string(T field, Object esValue) {
            // For facets, obtain fields as strings.
            if (conversions.get(field) == Conv.STRINGLIST) {
                return csv(field, esValue);
            }
            if (conversions.get(field) == Conv.BOOLEAN) {
                return Converters.BOOL.format((Boolean) esValue);
            }
            Assert.check(conversions.get(field) == Conv.STRING
                         || conversions.get(field) == Conv.DATE);
            if (esValue == null) return null;
            return (String) esValue;
        }

        private Long integer(T field, Object esValue) {
            Assert.check(conversions.get(field) == Conv.INTEGER);
            if (esValue == null) return null;
            if (esValue instanceof Integer) return new Long((Integer) esValue);
            Assert.check(esValue instanceof Long);
            return (Long) esValue;
        }

        private Date date(T field, Object esValue) {
            Assert.check(conversions.get(field) == Conv.DATE);
            if (esValue == null) return null;
            Assert.check(esValue instanceof String);
            return Converters.ISO8601.parse((String)esValue);
        }

        private String csv(T field, Object esValue) {
            Assert.check(conversions.get(field) == Conv.STRINGLIST);
            if (esValue == null) return "";
            List<?> esValues = (List<?>) esValue;
            // Cast is guarded by the check above.
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) esValues;
            return csvConverter.format(list);
        }


        protected final Converter<List<String>> csvConverter =
            new Converters.CsvConverter(true);

        protected Map<T, Conv> conversions;

    }


    /** Facet mappings are extracted from EnumMap<Field, String> */
    public static abstract class BaseFacetMapping<FACET extends Enum<FACET> & Field>
    extends BaseMapping<FACET>{

        @Override
        public XContentBuilder append(XContentBuilder builder,
                                      FACET field, Object value) throws IOException {
            Assert.check(value == null || value instanceof String);
            String facet = (String) value;
            switch (conversions.get(field)) {
                case DATE:
                    Date date = Converters.DATE.parse(facet);
                    if (date == null) return builder;
                    return builder.field(field.columnName(), date);
                case STRING:
                    if (facet == null) facet = "<none>";
                    return builder.field(field.columnName(), facet);
                case STRINGLIST:
                    List<String> values = csvConverter.parse(facet);
                    if (values.size() == 0) return builder;
                    builder.field(field.columnName()).startArray();
                    for (String item : values) builder.value(item);
                    return builder.endArray();
                case BOOLEAN:
                    if (facet == null) return builder;
                    return builder.field(field.columnName(),
                                         Converters.BOOL.parse(facet).booleanValue());
                case UNUSED:
                    return builder;
                default:
                    return Assert.unreachable(XContentBuilder.class);
            }
        }

    }


}
