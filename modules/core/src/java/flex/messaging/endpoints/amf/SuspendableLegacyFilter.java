/*************************************************************************
 *
 * ADOBE CONFIDENTIAL
 * __________________
 *
 *  Copyright 2008 Adobe Systems Incorporated
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

package flex.messaging.endpoints.amf;

import flex.messaging.io.amf.ASObject;
import flex.messaging.io.amf.ActionContext;
import flex.messaging.io.amf.MessageBody;
import flex.messaging.io.amf.MessageHeader;
import flex.messaging.messages.Message;
import flex.messaging.messages.RemotingMessage;
import flex.messaging.messages.ErrorMessage;
import flex.messaging.security.LoginManager;

import java.io.IOException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.lang.reflect.Array;

/**
 * This filter adds support for legacy FlashRemoting invocations.
 * <p>
 * AMF Headers are of limited use because the apply to the entire AMF packet, which
 * may contain a batch of several requests.
 * </p>
 * <p>
 * Rather than relying on the Flash Player team to change the AMF specification,
 * Flex 1.5 introduced the concept of a Message Envelope that allowed them to provide
 * message level headers that apply to a single request body.
 * </p>
 * <p>
 * Essentially they introduced one more layer of indirection with an ASObject of type &quot;Envelope&quot;
 * that had two properties:<br/>
 * - <i>headers</i>, which was an array of Header structures<br/>
 * - <i>body</i>, which was the actual data of the request (typically an array of arguments)
 * </p>
 * <p>
 * To save space on the wire, a Header structure was simply an array. The first element was
 * the header name as a String, and was the only required field. The second element, a boolean,
 * indicated whether the header must be understood. The third element, any Object, represented
 * the header value, if required.
 * </p>
 * <p>
 * This implementation will never suspend the chain and performs no internal synchronization.
 * </p>
 */
public class SuspendableLegacyFilter extends SuspendableAMFFilter
{
    //--------------------------------------------------------------------------
    //
    // Private Static Constants
    //
    //--------------------------------------------------------------------------

    private static final String LEGACY_ENVELOPE_FLAG_KEY = "_flag";
    private static final String LEGACY_ENVELOPE_FLAG_VALUE = "Envelope";
    private static final String LEGACY_SECURITY_HEADER_NAME = "Credentials";
    private static final String LEGACY_SECURITY_PRINCIPAL = "userid";
    private static final String LEGACY_SECURITY_CREDENTIALS = "password";

    //--------------------------------------------------------------------------
    //
    // Constructor
    //
    //--------------------------------------------------------------------------

    /**
     * Constructs a <tt>SuspendableLegacyFilter</tt>.
     * Legacy AMF requests require custom authentication handling that requires
     * access to a <tt>LoginManager</tt> (authentication is performed on every request
     * unlike the connection-level authentication in Flex 2+).
     *
     * @param loginManager The <tt>LoginManager</tt> to use to authenticate legacy requests.
     */
    public SuspendableLegacyFilter(LoginManager loginManager)
    {
        this.loginManager = loginManager;
    }

    //--------------------------------------------------------------------------
    //
    // Variables
    //
    //--------------------------------------------------------------------------

    /**
     * The login manager to use to auth legacy requests.
     */
    private LoginManager loginManager;

    //--------------------------------------------------------------------------
    //
    // Protected Methods
    //
    //--------------------------------------------------------------------------

    /**
     * @see flex.messaging.endpoints.amf.SuspendableAMFFilter#doInboundFilter(ActionContext)
     */
    protected void doInboundFilter(final ActionContext context) throws IOException
    {
        MessageBody requestBody = context.getRequestMessageBody();
        context.setLegacy(true);

        // Parameters are usually sent as an AMF Array.
        Object data = requestBody.getData();
        List newParams = null;

        // Check whether we're a new Flex 2.0 Messaging request.
        if (data != null)
        {
            if (data.getClass().isArray())
            {
                int paramLength = Array.getLength(data);
                if (paramLength == 1)
                {
                    Object obj = Array.get(data, 0);
                    if ((obj != null) && (obj instanceof Message))
                    {
                        context.setLegacy(false);
                        newParams = new ArrayList();
                        newParams.add(obj);
                    }
                }

                // It was not a Flex 2.0 Message, but we have an array, use its contents as our params.
                if (newParams == null)
                {
                    newParams = new ArrayList();
                    for (int i = 0; i < paramLength; i++)
                    {
                        try
                        {
                            newParams.add(Array.get(data, i));
                        }
                        catch (Throwable ignore)
                        {
                            // NOWARN
                        }
                    }
                }
            }
            else if (data instanceof List)
            {
                List paramList = (List)data;
                if (paramList.size() == 1)
                {
                    Object obj = paramList.get(0);
                    if ((obj != null) && (obj instanceof Message))
                    {
                        context.setLegacy(false);
                        newParams = new ArrayList();
                        newParams.add(obj);
                    }
                }

                // It was not a Flex 2.0 Message, but we have a list, so use it as our params.
                if (newParams == null)
                {
                    newParams = (List)data;
                }
            }
        }

        // We still haven't found any lists of params, so create one with whatever data we have.
        if (newParams == null)
        {
            newParams = new ArrayList();
            newParams.add(data);

        }

        if (context.isLegacy())
        {
            newParams = legacyRequest(context, newParams);
        }

        requestBody.setData(newParams);
    }

    /**
     * @see flex.messaging.endpoints.amf.SuspendableAMFFilter#doOutboundFilter(ActionContext)
     */
    protected void doOutboundFilter(final ActionContext context) throws IOException
    {
        if (context.isLegacy())
        {
            MessageBody responseBody = context.getResponseMessageBody();
            Object response = responseBody.getData();

            if (response instanceof ErrorMessage)
            {
                ErrorMessage error = (ErrorMessage)response;
                ASObject aso = new ASObject();
                aso.put("message", error.faultString);
                aso.put("code", error.faultCode);
                aso.put("details", error.faultDetail);
                aso.put("rootCause", error.rootCause);
                response = aso;
            }
            else if (response instanceof Message)
            {
                response = ((Message)response).getBody();
            }
            responseBody.setData(response);
        }
    }

    //--------------------------------------------------------------------------
    //
    // Private Methods
    //
    //--------------------------------------------------------------------------

    /**
     * Converts a legacy request to a Flex 2+ message.
     */
    private List legacyRequest(ActionContext context, List oldParams)
    {
        List newParams = new ArrayList(1);
        Map headerMap = new HashMap();
        Object body = oldParams;
        Message message = null;
        MessageBody requestBody = context.getRequestMessageBody();

        // Legacy Packet Security
        List packetHeaders = context.getRequestMessage().getHeaders();
        packetCredentials(packetHeaders);

        // Legacy Body
        if (oldParams.size() == 1)
        {
            Object obj = oldParams.get(0);

            if ((obj != null) && (obj instanceof ASObject))
            {
                ASObject aso = (ASObject)obj;

                // Unwrap legacy Flex 1.5 Envelope type.
                if (isEnvelope(aso))
                {
                    body = aso.get("data");

                    // Envelope level headers.
                    Object h = aso.get("headers");
                    if ((h != null) && (h instanceof List))
                    {
                        readEnvelopeHeaders((List)h, headerMap);
                        envelopeCredentials(headerMap);
                    }
                }
            }
        }

        // Convert legacy body into a RemotingMessage.
        message = createMessage(requestBody, body, headerMap);
        newParams.add(message);
        return newParams;
    }

    /**
     * Determines whether an <tt>ASObject</tt> is an AMF envelope.
     */
    private boolean isEnvelope(ASObject aso)
    {
        String flag = null;
        Object f = aso.get(LEGACY_ENVELOPE_FLAG_KEY);
        if ((f != null) && (f instanceof String))
            flag = (String)f;

        if ((flag != null) && flag.equalsIgnoreCase(LEGACY_ENVELOPE_FLAG_VALUE))
        {
            return true;
        }

        return false;
    }

    /**
     * Creates a Flex 2+ RemotingMessage from a legacy AMF message.
     */
    private RemotingMessage createMessage(MessageBody messageBody, Object body, Map headerMap)
    {
        RemotingMessage remotingMessage = new RemotingMessage();
        // MessageBroker expects non-null messageId and we don't need to
        // incur the cost of generating a UUID value so assigning empty string.
        remotingMessage.setMessageId("");
        remotingMessage.setBody(body);
        remotingMessage.setHeaders(headerMap);

        // Decode legacy target URI; format is "destination.operation"
        String targetURI = messageBody.getTargetURI();

        int dotIndex = targetURI.lastIndexOf(".");
        if (dotIndex > 0)
        {
            String destination = targetURI.substring(0, dotIndex);
            remotingMessage.setDestination(destination);
        }

        if (targetURI.length() > dotIndex)
        {
            String operation = targetURI.substring(dotIndex + 1);
            remotingMessage.setOperation(operation);
        }

        return remotingMessage;
    }

    /**
     * Process legacy AMF envelope headers and store them in a headers map that will be assigned
     * to the generated Flex 2+ RemotingMessage.
     */
    private Map readEnvelopeHeaders(List headers, Map headerMap)
    {
        int count = headers.size();
        for (int i = 0; i < count; i++)
        {
            Object obj = headers.get(i);

            // We currently expect a plain old AS Array.
            if ((obj != null) && (obj instanceof List))
            {
                List h = (List)obj;

                Object name = null;
                // Ignore must-understand legacy headers.
                // Object mustUnderstand = null;
                Object data = null;

                int numFields = h.size();

                // The array representing each header must have exactly three fields.
                // Otherwise, it's malformed; ignore it.
                if (numFields == 3)
                {
                    name = h.get(0);

                    if ((name != null) && (name instanceof String))
                    {
                        // mustUnderstand = h.get(1);
                        data = h.get(2);
                        headerMap.put(name, data);
                    }
                }
            }
        }

        return headerMap;
    }

    /**
     * Process legacy AMF envelope-level security.
     */
    private void envelopeCredentials(Map headers)
    {
        // Process Legacy Security Credentials
        Object obj = headers.get(LEGACY_SECURITY_HEADER_NAME);
        if ((obj != null) && (obj instanceof ASObject))
        {
            ASObject header = (ASObject)obj;
            String principal = (String)header.get(LEGACY_SECURITY_PRINCIPAL);
            Object credentials = header.get(LEGACY_SECURITY_CREDENTIALS);
            loginManager.login(principal, credentials.toString());
        }
        headers.remove(LEGACY_SECURITY_HEADER_NAME);
    }

    /**
     * Process legacy AMF packet-level security.
     */
    private void packetCredentials(List packetHeaders)
    {
        if (packetHeaders.size() > 0)
        {
            for (Iterator iter = packetHeaders.iterator(); iter.hasNext();)
            {
                MessageHeader header = (MessageHeader)iter.next();
                if (header.getName().equals(LEGACY_SECURITY_HEADER_NAME))
                {
                    Map loginInfo = (Map)header.getData();
                    String principal = loginInfo.get(LEGACY_SECURITY_PRINCIPAL).toString();
                    Object credentials = loginInfo.get(LEGACY_SECURITY_CREDENTIALS);
                    loginManager.login(principal, credentials.toString());
                    break;
                }
            }
        }
    }
}
