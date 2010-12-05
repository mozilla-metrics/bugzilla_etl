import sys

from urllib import quote

from http_utils import http_get
from bugs import Bug, BugRepo


class SolrRepo(BugRepo):

    def __repr__(self):
        return "SolrRepo('%s')" % self.host

    def get(self, bug_id):
        query = "bug_id:%s expiration_date:[NOW TO *]" % bug_id
        docs = http_get(self.connection,
                        "/solr/select/?q=%s&rows=1&wt=json" % quote(query))["response"]["docs"]
        if len(docs) == 0:
            return None
        record = docs[0]
        record["id"] = record["bug_id"]
        return Bug.from_unicode_dict(record)
