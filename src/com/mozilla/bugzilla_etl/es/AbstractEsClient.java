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


import java.io.PrintStream;
import java.util.List;

import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.transport.TransportAddress;

import com.mozilla.bugzilla_etl.base.Assert;
import com.mozilla.bugzilla_etl.base.Converters;
import com.mozilla.bugzilla_etl.base.Fields;
import com.mozilla.bugzilla_etl.es.Mapping.BugMapping;
import com.mozilla.bugzilla_etl.es.Mapping.FacetMapping;
import com.mozilla.bugzilla_etl.es.Mapping.MeasurementMapping;
import com.mozilla.bugzilla_etl.es.Mapping.VersionMapping;


abstract class AbstractEsClient {

    protected final Client client;
    protected final PrintStream log;
    
    protected String index() {
        return "bugs";
    }

    public AbstractEsClient(final PrintStream log, final String esNodes) {
        Assert.nonNull(log, esNodes);
        this.log = log;
        try {
            log.format("Using elasticsearch connection '%s'.\n", esNodes);
            TransportClient transportClient = new TransportClient(); 
            List<String> nodes = new Converters.CsvConverter().parse(esNodes);
            for (String node : nodes) {
                int colon = node.indexOf(':');
                String host = node.substring(0, colon);
                int port = Integer.parseInt(node.substring(colon+1));
                TransportAddress a = new InetSocketTransportAddress(host, port);
                transportClient.addTransportAddress(a);
            }
            client = transportClient;
        }
        catch (Exception error) {
            log.print("Error: Cannot create elastic search client.\n");
            error.printStackTrace(log);
            throw new RuntimeException(error);
        }
    }

    // elasticsearch mapping helpers:
    public static final Mapping<Fields.Bug> BUG = new BugMapping();
    public static final Mapping<Fields.Facet> FACET = new FacetMapping();
    public static final Mapping<Fields.Version> VERSION = new VersionMapping();
    public static final Mapping<Fields.Measurement> MEASURE = new MeasurementMapping();
    
}
