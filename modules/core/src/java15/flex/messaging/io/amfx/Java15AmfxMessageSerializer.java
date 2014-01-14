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

import java.io.OutputStream;

import flex.messaging.io.SerializationContext;
import flex.messaging.io.amf.AmfTrace;

/**
 * Specialization of the standard AMFX message serializer which creates a
 * Java 5 capable output object.
 * @exclude
 */
public class Java15AmfxMessageSerializer extends AmfxMessageSerializer
{
    /**
     * Establishes the context for writing out data to the given OutputStream.
     * This stream can handle Java 5 specific constructs (i.e. enum).
     * A null value can be passed for the trace parameter if a record of the
     * AMFX data should not be made.
     *
     * @param context The SerializationContext specifying the custom options.
     * @param out The OutputStream to write out the AMFX data.
     * @param trace If not null, turns on "trace" debugging for AMFX responses.
     */
    public void initialize(SerializationContext context, OutputStream out, AmfTrace trace)
    {
        amfxOut = new Java15AmfxOutput(context);
        amfxOut.setOutputStream(out);
        debugTrace = trace;
        isDebug = debugTrace != null;
        amfxOut.setDebugTrace(trace);
        
    }
}
