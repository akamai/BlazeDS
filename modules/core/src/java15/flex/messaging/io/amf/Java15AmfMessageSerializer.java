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

import java.io.OutputStream;

import flex.messaging.io.MessageIOConstants;
import flex.messaging.io.SerializationContext;

/**
 * This class can serialize messages, include Java 5 constructs such as enum, to an output stream.
 *
 * <p>Multiple messages can be written to the same stream.</p>
 * 
 * @exclude
 */
public class Java15AmfMessageSerializer extends AmfMessageSerializer
{
    /**
     * Establishes the context for writing out data to the given OutputStream.
     * A null value can be passed for the trace parameter if a record of the
     * AMF data should not be made.
     *
     * @param context The SerializationContext specifying the custom options.
     * @param out The OutputStream to write out the AMF data.
     * @param trace If not null, turns on "trace" debugging for AMF responses.
     */
    public void initialize(SerializationContext context, OutputStream out, AmfTrace trace)
    {
        amfOut = new Java15Amf0Output(context);
        amfOut.setAvmPlus(version >= MessageIOConstants.AMF3);
        amfOut.setOutputStream(out);

        debugTrace = trace;
        isDebug = trace != null;
        amfOut.setDebugTrace(debugTrace);
    }
}
