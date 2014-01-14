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

import flex.messaging.io.MessageDeserializer;
import flex.messaging.io.amf.ActionContext;
import flex.messaging.io.amf.ActionMessage;
import flex.messaging.io.amf.AmfTrace;
import flex.messaging.io.amf.MessageBody;
import flex.messaging.io.SerializationContext;
import flex.messaging.MessageException;

import org.xml.sax.Locator;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;

import java.io.IOException;
import java.io.InputStream;

/**
 * SAX based AMFX Parser.
 *
 * @author Peter Farland
 */
public class AmfxMessageDeserializer implements MessageDeserializer
{
    protected InputStream in;

    protected Locator locator;

    protected AmfxInput amfxIn;

    /*
     *  DEBUG LOGGING
     */
    protected AmfTrace debugTrace;
    protected boolean isDebug;

    public AmfxMessageDeserializer()
    {
    }

    /**
     * Establishes the context for reading in data from the given InputStream.
     * A null value can be passed for the trace parameter if a record of the
     * AMFX data should not be made.
     */
    public void initialize(SerializationContext context, InputStream in, AmfTrace trace)
    {
        amfxIn = new AmfxInput(context);
        this.in = in;

        debugTrace = trace;
        isDebug = debugTrace != null;

        if (debugTrace != null)
            amfxIn.setDebugTrace(debugTrace);
    }

    public void setSerializationContext(SerializationContext context)
    {
        amfxIn = new AmfxInput(context);
    }

    public void readMessage(ActionMessage m, ActionContext context) throws IOException
    {
        if (isDebug)
            debugTrace.startRequest("Deserializing AMFX/HTTP request");

        amfxIn.reset();
        amfxIn.setDebugTrace(debugTrace);
        amfxIn.setActionMessage(m);

        parse(m);
        
        context.setVersion(m.getVersion());
    }

    public Object readObject() throws ClassNotFoundException, IOException
    {
        return amfxIn.readObject();
    }

    protected void parse(ActionMessage m)
    {
        try
        {
            amfxIn.setXMLStreamReader(XMLInputFactory.newInstance().createXMLStreamReader(in));
            amfxIn.parse();
        }
        catch (MessageException ex)
        {
            ex.printStackTrace();
            clientMessageEncodingException(m, ex);
        }
        catch (XMLStreamException ex)
        {
            ex.printStackTrace();
            clientMessageEncodingException(m, ex);
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            clientMessageEncodingException(m, ex);
        }
    }

    protected void clientMessageEncodingException(ActionMessage m, Throwable t)
    {
        MessageException me;
        if (t instanceof MessageException)
        {
            me = (MessageException)t;
        }
        else
        {
            me = new MessageException("Error occurred parsing AMFX: " + t.getMessage());
        }

        me.setCode("Client.Message.Encoding");
        MessageBody body = new MessageBody();
        body.setData(me);
        m.addBody(body);
    }
}
