import sys

from http_utils import http_get
from bugs import Bug, BugRepo

class BzapiRepo(BugRepo):

    def get(self, bug_id):
        record = http_get(self.connection, "/latest/bug/%s" % bug_id)
        return Bug.from_unicode_dict(record)
