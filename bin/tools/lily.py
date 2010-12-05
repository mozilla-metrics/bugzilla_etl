from datetime import datetime
from urllib import quote

from http_utils import Http404, http_get, http_delete
from date_utils import parse_from_iso, format_as_timestamp
from bugs import Bug, BugRepo


NS_BETL = "com.mozilla.bugzilla_etl"
MAX_VERSION = 2147483647

class LilyRepo(BugRepo):

    def _master_path(self, bug_id):
        master_id = "USER.%s#" % "".join(reversed(str(bug_id).zfill(6)))
        return "/repository/record/%s" % quote(master_id)

    def get(self, bug_id):
        record = http_get(self.connection, self._master_path(bug_id))
        namespace_prefix = record["namespaces"][NS_BETL] + "$"
        unqualified_fields = dict(map(lambda item: (item[0][len(namespace_prefix):], item[1]),
                                      record["fields"].items()))
        return Bug.from_unicode_dict(unqualified_fields)

    def delete(self, bug_id):
        print("[Bug #%s] getting master record versions" % bug_id)
        master_path = self._master_path(bug_id)
        all_versions_path = "%s/version?max-results=%d&ns.betl=%s&fields=%s" \
                            % (master_path, MAX_VERSION, NS_BETL, quote("betl$modification_date"))
        try:
            all_versions = http_get(self.connection, all_versions_path)["results"]
        except Http404:
            print("[Bug #%s] 404 does not exists. skipping" % bug_id)
            return
        num_versions = len(all_versions)
        print("[Bug #%s] OK got bug '%s' (%s versions)" % (bug_id, bug_id, num_versions))

        print("[Bug #%s] getting slave/version record ids" % bug_id)
        for i, v in enumerate(all_versions):
            namespace_prefix = v["namespaces"][NS_BETL]
            from_datetime = parse_from_iso(v["fields"]["%s$modification_date" % namespace_prefix])
            from_timestamp = format_as_timestamp(from_datetime)
            slave_path = "%s%s000" % (master_path, from_timestamp)
            try:
                slave = http_get(self.connection, slave_path)
                print("[Bug #%s] ... v%s OK got slave record %r" % (bug_id, i+1, slave["id"]))
                http_delete(self.connection, slave_path)
                print("[Bug #%s] ... v%s OK deleted %r" % (bug_id, i+1, slave["id"]))
            except Http404:
                print("[Bug #%s] ... v%s does not exists (incomplete import?)" % (bug_id, i+1))

        print("\n[Bug #%s] deleted slave/version records" % bug_id)

        http_delete(self.connection, master_path)
        print("[Bug #%s] deleted master record." % bug_id)
