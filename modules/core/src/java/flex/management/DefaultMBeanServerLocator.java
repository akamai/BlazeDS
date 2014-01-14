/*************************************************************************
 *
 * ADOBE CONFIDENTIAL
 * __________________
 *
 *  Copyright 2002 - 2007 Adobe Systems Incorporated
 *  All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of Adobe Systems Incorporated and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Adobe Systems Incorporated
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Adobe Systems Incorporated.
 **************************************************************************/
package flex.management;

import java.util.ArrayList;
import java.util.Iterator;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;

import flex.messaging.log.Log;
import flex.messaging.log.LogCategories;

/**
 * The default implementation of an MBeanServerLocator. This implementation
 * returns the first MBeanServer from the list returned by MBeanServerFactory.findMBeanServer(null).
 * If no MBeanServers have been instantiated, this class will request the creation
 * of an MBeanServer and return a reference to it.
 *
 * @author shodgson
 */
public class DefaultMBeanServerLocator implements MBeanServerLocator
{
    private MBeanServer server;

    /** {@inheritDoc} */
    public synchronized MBeanServer getMBeanServer()
    {
        if (server == null)
        {
            // Use the first MBeanServer we can find.
            ArrayList servers = MBeanServerFactory.findMBeanServer(null);
            if (servers.size() > 0)
            {
                Iterator iterator = servers.iterator();
                server = (MBeanServer)iterator.next();
            }
            else
            {
                // As a last resort, try to create a new MBeanServer.
                server = MBeanServerFactory.createMBeanServer();
            }
            if (Log.isDebug())
                Log.getLogger(LogCategories.MANAGEMENT_MBEANSERVER).debug("Using MBeanServer: " + server);
        }
        return server;
    }

}
