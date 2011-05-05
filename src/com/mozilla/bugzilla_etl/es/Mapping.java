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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.search.SearchHitField;
import com.mozilla.bugzilla_etl.base.Assert;
import com.mozilla.bugzilla_etl.base.Converters;
import com.mozilla.bugzilla_etl.base.Fields;
import com.mozilla.bugzilla_etl.base.Converters.Converter;
import com.mozilla.bugzilla_etl.base.Fields.Field;


interface Mapping<T extends Field> {

    String string(T field, Map<String, SearchHitField> fields);
    Date date(T field, Map<String, SearchHitField> fields);
    String csv(T field, Map<String, SearchHitField> fields);
    Long integer(T field, Map<String, SearchHitField> fields);
    XContentBuilder append(XContentBuilder builder, T field, Object value) 
        throws IOException;

    static class BugMapping extends BaseMapping<Fields.Bug> {
        
        public static final String TYPE = "bug";
        
        BugMapping() {
            conversions = 
                new EnumMap<Fields.Bug, Conv>(Fields.Bug.class);
            conversions.put(Fields.Bug.ID,            Conv.INTEGER);
            conversions.put(Fields.Bug.REPORTED_BY,   Conv.STRING);
            conversions.put(Fields.Bug.CREATION_DATE, Conv.DATE);
        }
    }
    
    
    static class VersionMapping extends BaseMapping<Fields.Version> {
        VersionMapping() {
            conversions = 
                new EnumMap<Fields.Version, Conv>(Fields.Version.class);
            conversions.put(Fields.Version.BUG_ID,            Conv.UNUSED);
            conversions.put(Fields.Version.PERSISTENCE_STATE, Conv.UNUSED);
            conversions.put(Fields.Version.MODIFIED_BY,       Conv.STRING);
            conversions.put(Fields.Version.ANNOTATION,        Conv.STRING);
            conversions.put(Fields.Version.MODIFICATION_DATE, Conv.DATE);
            conversions.put(Fields.Version.EXPIRATION_DATE,   Conv.DATE);
        }
    }
    
    
    static class FacetMapping extends BaseMapping<Fields.Facet> {
        FacetMapping() {
            final EnumMap<Fields.Facet, Conv> c = 
                new EnumMap<Fields.Facet, Conv>(Fields.Facet.class);
            c.put(Fields.Facet.KEYWORDS,                       Conv.STRINGLIST);
            c.put(Fields.Facet.FLAGS,                          Conv.STRINGLIST);
            c.put(Fields.Facet.MODIFIED_FIELDS,                Conv.STRINGLIST);
            c.put(Fields.Facet.STATUS_WHITEBOARD_ITEMS,        Conv.STRINGLIST);
            c.put(Fields.Facet.CHANGES,                        Conv.STRINGLIST);
            c.put(Fields.Facet.MAJOR_STATUS_LAST_CHANGED_DATE, Conv.DATE);
            c.put(Fields.Facet.STATUS_LAST_CHANGED_DATE,       Conv.DATE);
            // The others are all single strings:
            for (Fields.Facet field : Fields.Facet.values()) {
                if (c.containsKey(field)) continue;
                c.put(field, Conv.STRING);
            }
            conversions = c;
        }
        
        @Override 
        public XContentBuilder append(XContentBuilder builder, 
                                      Fields.Facet field, Object value) throws IOException {
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
                case UNUSED:
                    return builder;
                default:
                    return Assert.unreachable(XContentBuilder.class);
            }
        }

    }

    
    static class MeasurementMapping extends BaseMapping<Fields.Measurement> {
        public MeasurementMapping() {
            conversions = 
                new EnumMap<Fields.Measurement, Conv>(Fields.Measurement.class);
            for (Fields.Measurement field : Fields.Measurement.values()) {
                conversions.put(field, Conv.INTEGER);
            }
        }
      
    }
    
    static enum Conv { STRING, STRINGLIST, DATE, INTEGER, UNUSED }
    
    static abstract class BaseMapping<T extends Field> implements Mapping<T> {

        @Override
        public String string(T field, Map<String, SearchHitField> fields) {
            final String name = name(field);
            if (!fields.containsKey(name)) return null;
            return string(field, fields.get(name).value());
        }
        
        @Override
        public Date date(T field, Map<String, SearchHitField> fields) {
            final String name = name(field);
            if (!fields.containsKey(name)) return null;
            return date(field, fields.get(name).value());
        }
        
        @Override
        public String csv(T field, Map<String, SearchHitField> fields) {
            final String name = name(field);
            if (!fields.containsKey(name)) return null;
            return csv(field, fields.get(name).values());
        }

        @Override
        public Long integer(T field, Map<String, SearchHitField> fields) {
            final String name = name(field);
            if (!fields.containsKey(name)) return null;
            return integer(field, fields.get(name).value());
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
            Assert.check(conversions.get(field) == Conv.STRING);
            if (esValue == null) return null;
            return esValue.toString();
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
            Assert.check(esValue instanceof Date);
            return (Date) esValue; 
        }
        
        private String csv(T field, List<?> esValues) {
            Assert.check(conversions.get(field) == Conv.STRINGLIST);
            if (esValues == null) return "";
            // Cast is guarded by the check above.
            @SuppressWarnings("unchecked") 
            List<String> list = (List<String>) esValues;
            return csvConverter.format(list); 
        }

        
        protected final Converter<List<String>> csvConverter = 
            new Converters.CsvConverter(true);
        
        protected Map<T, Conv> conversions;

    }
    
}
