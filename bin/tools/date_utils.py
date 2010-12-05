import calendar
import iso8601

def parse_from_iso(isodate):
    return iso8601.parse_datetime(isodate)

def format_as_timestamp(datetime):
    return calendar.timegm(datetime.utctimetuple())
