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

package com.mozilla.bugzilla_etl.lily;

import java.io.PrintStream;

import org.apache.zookeeper.KeeperException;
import org.lilyproject.client.LilyClient;
import org.lilyproject.repository.api.Repository;

import com.mozilla.bugzilla_etl.base.Assert;

abstract class AbstractLilyClient {

    static final int ZK_TIMEOUT_MS = 5000;

    protected final LilyClient client;
    protected final Repository repository;
    protected final PrintStream log;
    protected final Types types;
    protected final Ids ids;

    public AbstractLilyClient(final PrintStream log, final String lilyConnection) {
        Assert.nonNull(log, lilyConnection);
        this.log = log;
        try {
            client = new LilyClient(lilyConnection, ZK_TIMEOUT_MS);
            repository = client.getRepository();
        }
        catch (KeeperException zookeeperError) {
            log.print("Error: Trouble talking to Zookeeper.\n");
            zookeeperError.printStackTrace(log);
            throw new RuntimeException(zookeeperError);
        }
        catch (Exception error) {
            log.print("Error: Cannot connect to lily instance.\n");
            error.printStackTrace(log);
            throw new RuntimeException(error);
        }
        types = new Types(log, repository);
        ids = new Ids(repository);
    }

}
