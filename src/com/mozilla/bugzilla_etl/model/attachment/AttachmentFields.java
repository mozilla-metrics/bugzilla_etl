package com.mozilla.bugzilla_etl.model.attachment;

import com.mozilla.bugzilla_etl.model.Family;
import com.mozilla.bugzilla_etl.model.Field;


public class AttachmentFields {

    /** Version-independent data on an attachment. */
    public static enum Attachment implements Field {
        ID("attachment_id"),
        BUG_ID,
        SUBMITTED_BY,
        SUBMISSION_DATE;

        public String columnName;

        @Override
        public String columnName() { return columnName; }

        @Override
        public Family family() { return Family.ATTACHMENT; }

        Attachment(String name) { columnName = name; }
        Attachment() { columnName = name().toLowerCase(); }
    }


    /** Attachment facets. */
    public static enum Facet implements Field {
        CHANGES(true),
        IS_OBSOLETE,
        IS_PATCH,
        IS_URL,
        MIMETYPE,
        MODIFIED_FIELDS(true),
        REQUESTS;

        @Override
        public Family family() { return Family.ATTACHMENT_FACET; }

        private final String columnName;
        public final boolean isComputed;

        /**
         * If initialized with a column name of <tt>null</tt>, this facet is
         * computed and not present in activities input.
         * @param columnName
         */
        Facet(boolean isComputed) {
            this.isComputed = isComputed;
            this.columnName = name().toLowerCase();
        }

        Facet() { this(false); }

        @Override public String columnName() {
            return columnName;
        }
    }


    /** Attachment measurements. */
    public static enum Measurement implements Field {

        /** Attachment version number. */
        NUMBER;

        public String columnName;

        @Override
        public String columnName() { return columnName; }

        @Override
        public Family family() { return Family.ATTACHMENT_MEASURE; }

        Measurement(String name) { columnName = name; }
        Measurement() { columnName = name().toLowerCase(); }
    }
}
