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

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.util.List;

import flex.messaging.MessageException;
import flex.messaging.io.MessageDeserializer;
import flex.messaging.io.MessageIOConstants;
import flex.messaging.io.MessageSerializer;
import flex.messaging.io.SerializationContext;
import flex.messaging.io.SerializationException;
import flex.messaging.io.TypeMarshaller;
import flex.messaging.io.TypeMarshallingContext;
import flex.messaging.io.amf.ASObject;
import flex.messaging.io.amf.ActionContext;
import flex.messaging.io.amf.ActionMessage;
import flex.messaging.io.amf.AmfTrace;
import flex.messaging.io.amf.MessageBody;
import flex.messaging.log.Log;
import flex.messaging.log.LogCategories;
import flex.messaging.log.Logger;
import flex.messaging.messages.ErrorMessage;
import flex.messaging.messages.Message;
import flex.messaging.messages.MessagePerformanceInfo;
import flex.messaging.util.ExceptionUtil;
import flex.messaging.util.StringUtils;

/**
 * Filter for serializing and deserializing AMF.
 * This implementation will never suspend the chain and performs no internal synchronization.
 */
public class SuspendableSerializationFilter extends SuspendableAMFFilter
{
    //--------------------------------------------------------------------------
    //
    // Private Static Constants
    //
    //--------------------------------------------------------------------------    

    // Error codes.
    private static final int REQUEST_ERROR = 10307;
    private static final int RESPONSE_ERROR = 10308;

    //--------------------------------------------------------------------------
    //
    // Constructor
    //
    //--------------------------------------------------------------------------    

    /**
     * Constructs a <tt>SuspendableSerializationFilter</tt>.
     * Accepts an optional log category to use, to support logging under a specific endpoint's
     * log category rather than the general endpoint log category.
     * 
     * @param logCategory A specific endpoint log category to use.
     * @param serializationContext The serialization context to use for inbound and outbound serialization and
     *        deserialization.
     * @param typeMarshaller The type marshalling context to use for inbound and outbound serialization
     *        and deserialization.
     */
    public SuspendableSerializationFilter(String logCategory, SerializationContext serializationContext, TypeMarshaller typeMarshaller)
    {
        if (logCategory == null)
            logCategory = LogCategories.ENDPOINT_GENERAL;
        logger = Log.getLogger(logCategory);
        this.serializationContext = serializationContext;
        this.typeMarshaller = typeMarshaller;
    }

    //--------------------------------------------------------------------------
    //
    // Variables
    //
    //--------------------------------------------------------------------------    
        
    /**
     * Used to log serialization/deserialization messages.
     */
    private Logger logger;
    
    /**
     * Used for inbound and outbound serialization and deserialization.
     */
    private SerializationContext serializationContext;
    
    /**
     * Used for marshalling types during inbound and outbound serialization and deserialization.
     */
    private TypeMarshaller typeMarshaller;
    
    //--------------------------------------------------------------------------
    //
    // Properties
    //
    //--------------------------------------------------------------------------    

    //----------------------------------
    //  inputStream
    //----------------------------------            

    private InputStream input;
    
    /**
     * Returns the <tt>InputStream</tt> currently assigned to this filter that will be used
     * to read message bytes from.
     */
    public InputStream getInputStream()
    {
        return input;
    }
    
    /**
     * Sets the <tt>InputStream</tt> to read message bytes from.
     */
    public void setInputStream(InputStream value)
    {
        input = value;
    }

    //----------------------------------
    //  contentLength
    //----------------------------------            

    private int contentLength = -1;
    
    /**
     * Returns the number of bytes that the <tt>InputStream</tt> contains.
     */
    public int getContentLength()
    {
        return contentLength;
    }
    
    /**
     * Sets the number of bytes that the <tt>InputStream</tt> contains.
     * The <tt>InputStream</tt> does not provide a convenient way to track total bytes read
     * so this property is provided to allow the content length to be assigned along with 
     * the stream containing the content.
     * The stream generally wraps an Http request body, which is a finite byte array of known
     * length.
     */
    public void setContentLength(int value)
    {
        contentLength = value;
    }
    
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
        // Additional AMF packet tracing is enabled only at the debug logging level.
        AmfTrace debugTrace = Log.isDebug() ? new AmfTrace() : null;
        
        // Create an empty ActionMessage object to hold our response.
        context.setResponseMessage(new ActionMessage());
        
        // Flag to track whether we deserialize valid AMF (if we don't, we need to skip further inbound processing).
        boolean success = false;
        
        // Use the thread local serialization context.
        try
        {
            TypeMarshallingContext.setTypeMarshaller(typeMarshaller);
            
            // Deserialize the input stream into an ActionMessage object.
            MessageDeserializer deserializer = serializationContext.newMessageDeserializer();

            // Setup the deserialization context.           
            deserializer.initialize(serializationContext, input, debugTrace);

            // Record the length of the input stream for performance metrics.
            if (contentLength != -1)
                context.setDeserializedBytes(contentLength);

            // Set up the incoming MPI info if it is enabled.            
            if (context.isMPIenabled())
            {
                MessagePerformanceInfo mpi = new MessagePerformanceInfo();
                mpi.recordMessageSizes = context.isRecordMessageSizes();
                mpi.recordMessageTimes = context.isRecordMessageTimes();
                if (context.isRecordMessageTimes())
                    mpi.receiveTime = System.currentTimeMillis();
                if (context.isRecordMessageSizes())
                    mpi.messageSize = contentLength;
                
                context.setMPII(mpi);
            }

            ActionMessage m = new ActionMessage();
            context.setRequestMessage(m);
            deserializer.readMessage(m, context);
            success = true; // Continue inbound processing.
        }
        catch (EOFException eof)
        {
            context.setStatus(MessageIOConstants.STATUS_NOTAMF);
        }
        catch (IOException e) 
        {
            if (Log.isDebug())
                logger.debug("IOException reading AMF message - client closed socket before sending the message?");

            throw e;
        }
        catch (Throwable t)
        {
            deserializationError(context, t);
        }
        finally
        {
            if (!success)
                setInboundAborted(true);
            
            TypeMarshallingContext.setTypeMarshaller(null);
            
            // Reset content length.
            contentLength = -1;
            
            // Use the same ActionMessage version for the response.
            ActionMessage respMsg = context.getResponseMessage();
            respMsg.setVersion(context.getVersion());

            // Log a trace of the inbound AMF message.
            if (Log.isDebug())
                logger.debug(debugTrace.toString());
        }
    }
    
    /**
     * @see flex.messaging.endpoints.amf.SuspendableAMFFilter#doOutboundFilter(ActionContext)
     */
    protected void doOutboundFilter(final ActionContext context) throws IOException
    {
        // Serialize output.
        if (context.getStatus() != MessageIOConstants.STATUS_NOTAMF)
        {
            ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();                               
            ActionMessage respMesg = context.getResponseMessage();

            // Additional AMF packet tracing is enabled only at the debug logging level.
            AmfTrace debugTrace = Log.isDebug() ? new AmfTrace() : null;

            try
            {
                TypeMarshallingContext.setTypeMarshaller(typeMarshaller);
                
                // Overhead calculation is only necessary when MPI is enabled. 
                long serializationOverhead=0;
                if (context.isRecordMessageTimes())
                {                
                    // Set server send time.
                    context.getMPIO().sendTime = System.currentTimeMillis();
                    if (context.isRecordMessageSizes())
                        serializationOverhead = System.currentTimeMillis();                     
                }        
                
                // Use the thread local serialization context to serialize the response.
                MessageSerializer serializer = serializationContext.newMessageSerializer();
                serializer.initialize(serializationContext, outBuffer, debugTrace);
                serializer.writeMessage(respMesg);
                
                // Keep track of serializes bytes for performance metrics.
                context.setSerializedBytes(outBuffer.size());
                
                // Serialize message again after adding info if mpio with sizing is enabled.
                if (context.isRecordMessageSizes())
                {
                    try
                    {                           
                        context.getMPIO().messageSize = outBuffer.size();
                        
                        // Reset server send time.           
                        if (context.isRecordMessageTimes())
                        {
                            serializationOverhead = System.currentTimeMillis() - serializationOverhead;
                            context.getMPIO().addToOverhead(serializationOverhead);
                            context.getMPIO().sendTime = System.currentTimeMillis(); 
                        }
                        
                        // Reserialize the message now that info has been added.
                        outBuffer = new ByteArrayOutputStream();
                        respMesg = context.getResponseMessage();
                        serializer = serializationContext.newMessageSerializer();
                        serializer.initialize(serializationContext, outBuffer, debugTrace);
                        serializer.writeMessage(respMesg);
                    }
                    catch(Exception e)
                    {
                        if (Log.isDebug())
                            logger.debug("MPI set up error: " + e.toString());
                    }
                }                                                                                
                context.setResponseOutput(outBuffer);
            }
            catch (Exception e)
            {
                serializationError(context, e);
            }
            finally
            {
                TypeMarshallingContext.setTypeMarshaller(null);
                
                if (Log.isDebug())
                    logger.debug(debugTrace.toString());
            }
        }        
    }

    /**
     * Attempt to provide the client with useful information about the deserialization failure.
     */
    private void deserializationError(ActionContext context, Throwable t)
    {
        context.setStatus(MessageIOConstants.STATUS_ERR);

        // Create a single message body to hold the error.
        MessageBody responseBody = new MessageBody();
        if (context.getMessageNumber() < context.getRequestMessage().getBodyCount())
        {
            responseBody.setTargetURI(context.getRequestMessageBody().getResponseURI());
        }

        // If the message couldn't be deserialized enough to know the version, set the current version here
        if (context.getVersion() == 0)
        {
            context.setVersion(ActionMessage.CURRENT_VERSION);
        }

        // Append the response body to the output message.
        context.getResponseMessage().addBody(responseBody);

        String message;
        MessageException methodResult;
        if (t instanceof MessageException)
        {
            methodResult = (MessageException)t;
            message = methodResult.getMessage();
        }
        else
        {
            // Error deserializing client message.
            methodResult = new SerializationException();
            methodResult.setMessage(REQUEST_ERROR);
            methodResult.setRootCause(t);
            message = methodResult.getMessage();
        }
        responseBody.setData(methodResult.createErrorMessage());
        responseBody.setReplyMethod(MessageIOConstants.STATUS_METHOD);

        if (Log.isError())
            logger.error(message + StringUtils.NEWLINE + ExceptionUtil.toString(t));
    }

    /**
     * Attempt to provide the client with useful information about the serialization failure
     * When there is a serialization failure, there is no way to tell which response failed
     * serilization. Add a new response with the serialization failure for each of the
     * corresponding requests in the received ActionMessage.
     */
    private void serializationError(ActionContext context, Throwable t)
    {
        ActionMessage responseMessage = new ActionMessage();
        context.setResponseMessage(responseMessage);

        int bodyCount = context.getRequestMessage().getBodyCount();
        for (context.setMessageNumber(0); context.getMessageNumber() < bodyCount; context.incrementMessageNumber())
        {
            MessageBody responseBody = new MessageBody();
            responseBody.setTargetURI(context.getRequestMessageBody().getResponseURI());
            context.getResponseMessage().addBody(responseBody);

            Object methodResult;

            if (t instanceof MessageException)
            {
                methodResult = ((MessageException)t).createErrorMessage();
            }
            else
            {
                String message = "An error occurred while serializing server response(s).";
                if (t.getMessage() != null)
                {
                    message = t.getMessage();
                    if (message == null)
                        message = t.toString();
                }

                methodResult = new MessageException(message, t).createErrorMessage();
            }

            if (context.isLegacy())
            {
                if (methodResult instanceof ErrorMessage)
                {
                    ErrorMessage error = (ErrorMessage)methodResult;
                    ASObject aso = new ASObject();
                    aso.put("message", error.faultString);
                    aso.put("code", error.faultCode);
                    aso.put("details", error.faultDetail);
                    aso.put("rootCause", error.rootCause);
                    methodResult = aso;
                }
                else if (methodResult instanceof Message)
                {
                    methodResult = ((Message)methodResult).getBody();
                }
            }
            else
            {
                Object data = context.getRequestMessageBody().getData();
                if (data instanceof List)
                {
                    data = ((List)data).get(0);
                }
                else if (data.getClass().isArray())
                {
                    data = Array.get(data, 0);
                }

                Message inMessage;
                if (data instanceof Message)
                {
                    inMessage = (Message)data;
                    if (inMessage.getClientId() != null)
                    {
                        ((ErrorMessage)methodResult).setClientId(inMessage.getClientId().toString());
                    }
                    if (inMessage.getMessageId() != null)
                    {
                        ((ErrorMessage)methodResult).setCorrelationId(inMessage.getMessageId());
                        ((ErrorMessage)methodResult).setDestination(inMessage.getDestination());
                    }
                }
            }

            responseBody.setData(methodResult);
            responseBody.setReplyMethod(MessageIOConstants.STATUS_METHOD);
        }

        if (Log.isError())
            logger.error("Exception occurred during serialization: " + ExceptionUtil.toString(t));

        // Serialize the error messages.
        SerializationContext sc = SerializationContext.getSerializationContext();
        MessageSerializer serializer = sc.newMessageSerializer();
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        AmfTrace debugTrace = Log.isDebug() ? new AmfTrace() : null;
        serializer.initialize(sc, outBuffer, debugTrace);

        try
        {
            serializer.writeMessage(context.getResponseMessage());
            context.setResponseOutput(outBuffer);
        }
        catch (IOException e)
        {
            // Error serializing response.
            MessageException ex = new MessageException();
            ex.setMessage(RESPONSE_ERROR);
            ex.setRootCause(e);
            throw ex;
        }
    }
}
