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
package flex.messaging.io.amfx;

import java.io.IOException;

import flex.messaging.io.SerializationContext;
import flex.messaging.io.amf.Amf3Output;
import flex.messaging.io.amf.Java15Amf3Output;
import flex.messaging.io.PropertyProxyRegistry;

/**
 * Serializes Java types to ActionScript 3 types via AMFX, an XML
 * based representation of AMF 3.
 * This specialization handles Java 5 specific constructs, like enum.
 * @exclude
 */
public class Java15AmfxOutput extends AmfxOutput
{
    /**
     * Create an output object that can handle Java 5 specific constructs such as enum.
     * @param context context parameters for the output object
     */
    public Java15AmfxOutput(SerializationContext context)
    {
        super(context);
    }

    /**
     * Serialize the provided object to the output stream.
     * Convert enums to their string representation for Actionscript.
     */
    public void writeObject(Object o) throws IOException
    {
        if (o != null && o instanceof Enum && PropertyProxyRegistry.getRegistry().getProxy(o.getClass()) == null)
        {
            Enum enumValue = (Enum)o;
            writeString(enumValue.name());
        }
        else
        {
            super.writeObject(o);
        }
    }

    /**
     * Returns a new output object capable of handling Java 5 constructs (i.e. enum).
     */
    protected Amf3Output createAMF3Output()
    {
        return new Java15Amf3Output(context);
    }
}
