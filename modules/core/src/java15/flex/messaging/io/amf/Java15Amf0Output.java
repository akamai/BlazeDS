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
package flex.messaging.io.amf;

import java.io.IOException;

import flex.messaging.io.SerializationContext;
import flex.messaging.io.PropertyProxyRegistry;

/**
 * A JDK 1.5 specific subclass of Amf0Output to handle Java 5 constructs 
 * such as enums.
 * @exclude
 */
public class Java15Amf0Output extends Amf0Output
{
    /**
     * Creates an output object that handles Java 5 constructs such as enums.
     * @param context the serialization context to use
     */
    public Java15Amf0Output(SerializationContext context)
    {
        super(context);
    }

    /**
     * Creates a new JDK 1.5 specific subclass of Amf3Output which is
     * initialized with the current SerializationContext, OutputStream and
     * debug trace settings to switch the version of the AMF protocol
     * mid-stream.
     */
    protected void createAMF3Output()
    {
        avmPlusOutput = new Java15Amf3Output(context);
        avmPlusOutput.setOutputStream(out);
        avmPlusOutput.setDebugTrace(trace);
    }

    /**
     * Serialize an object using AMF0.  Handles Java 5 constructs such as enum.
     * @param o the object to serialize
     * @throws IOException if there is a problem writing to the output stream
     */
    public void writeObject(Object o) throws IOException
    {
        if (o != null && o instanceof Enum && PropertyProxyRegistry.getRegistry().getProxy(o.getClass()) == null)
        {
            Enum enumValue = (Enum)o;
            writeAMFString(enumValue.name());
        }
        else
        {
            super.writeObject(o);
        }
    }
}
