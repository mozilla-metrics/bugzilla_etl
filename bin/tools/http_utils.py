import json
from httplib import OK, NOT_FOUND, HTTPException

HTTP_DEBUG = True

def http_get(con, path, headers = {"Accept": "application/json", "Content-Type": "application/json"}):
    """Retrieve a resource from the connected site."""
    if HTTP_DEBUG: print("\nHTTP GET %s" % path)
    con.request("GET", path)
    response = con.getresponse()
    responseText = response.read().decode("utf-8")
    if response.status != OK:
        if response.status == NOT_FOUND: raise Http404
        else: raise HTTPException(response.status)
    return json.loads(responseText)


def http_delete(con, path, headers = {"Accept": "application/json", "Content-Type": "application/json"}):
    """Delete a resource at the connected site."""
    if HTTP_DEBUG: print("\nHTTP DELETE %s" % path)
    con.request("DELETE", path)
    response = con.getresponse()
    # Read everything so that the connection accepts a new request.
    response.read().decode("utf-8")
    if response.status != OK:
        if response.status == NOT_FOUND: raise Http404
        else: raise HTTPException(response.status)

class Http404:
    pass

