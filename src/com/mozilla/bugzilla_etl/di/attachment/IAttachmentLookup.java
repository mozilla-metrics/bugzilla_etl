package com.mozilla.bugzilla_etl.di.attachment;

import com.mozilla.bugzilla_etl.base.Lookup;
import com.mozilla.bugzilla_etl.model.attachment.Attachment;

/** We have this non-generic interface solely for janino compatibility. */
public interface IAttachmentLookup extends Lookup<Attachment, Exception> { }
