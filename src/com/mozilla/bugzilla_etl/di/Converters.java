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

package com.mozilla.bugzilla_etl.di;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Date;
import java.util.regex.Pattern;

import com.mozilla.bugzilla_etl.base.Assert;
import com.mozilla.bugzilla_etl.model.attachment.Request;
import com.mozilla.bugzilla_etl.model.bug.Flag;

public class Converters {

    private static final Pattern CSV_SPLITTER = Pattern.compile("\\s*,\\s*");
    private static final Pattern CSV_WITH_ESCAPES_SPLITTER = Pattern.compile("\\s*(?<!\\\\),\\s*");

    public static interface Converter<T> {
        T parse(String representation);
        String format(T value);
    }

    public static class CsvConverter implements Converter<List<String>> {

        final boolean usesEscapes;
        public CsvConverter() { usesEscapes = true; }
        public CsvConverter(final boolean useEscapes) { usesEscapes = useEscapes; }

        @Override
        public List<String> parse(String representation) {
            Assert.nonNull(representation);
            final String[] parts = (usesEscapes ? CSV_WITH_ESCAPES_SPLITTER
                                                : CSV_SPLITTER).split(representation);
            if (parts.length == 1 && parts[0].equals("")) return Collections.emptyList();
            return Arrays.asList(parts);
        }

        @Override
        public String format(List<String> items) {
            final StringBuilder buffer = new StringBuilder();
            boolean first = true;
            for (String item : items) {
                if (usesEscapes) item = item.replace(",", "\\,");
                if (!first) buffer.append(',');
                buffer.append(item);
                first = false;
            }
            return buffer.toString();
        }
    }

    public static final Converter<List<String>> KEYWORDS = new CsvConverter(false);
    public static final Converter<List<String>> MODIFIED_FIELDS = new CsvConverter(false);
    public static final Converter<List<String>> CHANGES = new CsvConverter(true);
    /** This does not parse the status_whiteboard field! */
    public static final Converter<List<String>> STATUS_WHITEBOARD_ITEMS = new CsvConverter(true);

    public static final Converter<Flag> FLAG = new Converter<Flag>(){
        @Override
        public Flag parse(String representation) {
            Assert.nonNull(representation);
            return Flag.fromRepresentation(representation);
        }
        @Override
        public String format(Flag flag) {
            Assert.nonNull(flag);
            return flag.representation();
        }
    };

    public static final Converter<List<Flag>> FLAGS = new Converter<List<Flag>>(){
        @Override
        public List<Flag> parse(String representation) {
            Assert.nonNull(representation);
            final List<Flag> flags = new java.util.LinkedList<Flag>();
            if (representation.isEmpty()) return flags;
            for (final String flagRepr : CSV_SPLITTER.split(representation)) {
                try {
                    flags.add(Flag.fromRepresentation(flagRepr));
                }
                catch (IllegalArgumentException ex) {
                    // Error parsing individual flag. Just give a message.
                    System.out.format("WARNING: %s\n", ex.getMessage());
                }
            }
            return flags;
        }
        @Override
        public String format(List<Flag> flags) {
            final StringBuilder buffer = new StringBuilder();
            boolean first = true;
            for (Flag flag : flags) {
                if (!first) buffer.append(',');
                buffer.append(flag.representation());
                first = false;
            }
            return buffer.toString();
        }
    };

    public static final Converter<List<Request>> REQUESTS = new Converter<List<Request>>(){
        @Override
        public List<Request> parse(String representation) {
            Assert.nonNull(representation);
            final List<Request> requests = new java.util.LinkedList<Request>();
            if (representation.isEmpty()) return requests;
            for (final String requestRepr : CSV_SPLITTER.split(representation)) {
                try {
                    requests.add(Request.fromRepresentation(requestRepr));
                }
                catch (IllegalArgumentException ex) {
                    // Error parsing individual review request. Just give a message.
                    System.out.format("WARNING: %s\n", ex.getMessage());
                }
            }
            return requests;
        }
        @Override
        public String format(List<Request> requests) {
            final StringBuilder buffer = new StringBuilder();
            boolean first = true;
            for (Flag request : requests) {
                if (!first) buffer.append(',');
                buffer.append(request.representation());
                first = false;
            }
            return buffer.toString();
        }
    };

    public static final Converter<Date> DATE = new Converter<Date>() {
        @Override
        public Date parse(String representation) {
            if (representation == null || representation.length() == 0) return null;
            return new Date(Long.parseLong(representation));
        }
        @Override
        public String format(Date date) {
            if (date == null) return null;
            return Long.valueOf(date.getTime()).toString();
        }
    };

    public static final Converter<Date> ISO8601 = new Converter<Date>() {
        private final SimpleDateFormat df =
            new SimpleDateFormat( "yyyy-MM-dd'T'HH:mm:ss.SSSz" );

        /** Note that the full ISO 8601 string including time zone is needed. */
        @Override
        public Date parse(String repr) {
            // Change time zone indicator to be java compatible.
            if (repr.endsWith("Z")) {
                repr = repr.substring(0, repr.length()-1) + "GMT-00:00";
            }
            else {
                int tzStart = repr.length() - 6;
                String s0 = repr.substring(0, tzStart);
                String s1 = repr.substring(tzStart, repr.length());
                repr = s0 + "GMT" + s1;
            }

            try {
                return df.parse(repr);
            }
            catch (ParseException e) {
                return Assert.unreachable(Date.class, "Invalid date: %s", repr);
            }
        }

        @Override
        public String format(Date value) {
            return Assert.unreachable(String.class, "Not yet implemented.");
        }
    };

}
