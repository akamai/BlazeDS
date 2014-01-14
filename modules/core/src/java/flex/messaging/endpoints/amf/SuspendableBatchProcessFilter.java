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

import flex.messaging.io.amf.ActionContext;
import flex.messaging.io.amf.MessageBody;
import flex.messaging.io.MessageIOConstants;
import flex.messaging.io.RecoverableSerializationException;

import java.io.IOException;

/**
 * Filter that splits a batched AMF message into individual invocations of the chain from this point forward
 * for each message within the batch that deserializes without errors.
 * This implementation will never suspend the chain and performs no internal synchronization.
 */
public class SuspendableBatchProcessFilter extends SuspendableAMFFilter
{
    //--------------------------------------------------------------------------
    //
    // Variables
    //
    //--------------------------------------------------------------------------    
    
    /**
     * Used to detect when the filter is processing a new message batch.
     */
    private ActionContext currentBatchContext;
    
    /**
     * The count of messages in the current batch to process.
     */
    private int messageCount;

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
        // New context (a new message batch) so reset state.
        if (currentBatchContext != context)
        {
            currentBatchContext = context;
            messageCount = context.getRequestMessage().getBodyCount();
            context.setMessageNumber(0);
        }
        else // Iterating on the same context; advance to the next message.
        {
            context.incrementMessageNumber();
        }
        
        // Create the response message container for the currently selected inbound context message.
        MessageBody responseBody = new MessageBody();
        responseBody.setTargetURI(context.getRequestMessageBody().getResponseURI());
        context.getResponseMessage().addBody(responseBody);

        //Check that the deserialized message body data type was valid. If not, skip the message.
        while (true)
        {
            Object o = context.getRequestMessageBody().getData();
            if (o != null && o instanceof RecoverableSerializationException)
            {
                context.getResponseMessageBody().setData(((RecoverableSerializationException)o).createErrorMessage());
                context.getResponseMessageBody().setReplyMethod(MessageIOConstants.STATUS_METHOD); 
                messageCount--;
                continue; // No need to process the current message further.
            }
            else
            {
                break; // The current message deserialized correctly and can be processed.
            }
        }
    }
    
    /**
     * @see flex.messaging.endpoints.amf.SuspendableAMFFilter#doOutboundFilter(ActionContext)
     */
    protected void doOutboundFilter(final ActionContext context) throws IOException
    {
        if (--messageCount > 0)
        {
            // Reinvoke the chain from this point; the context contains more messages in the batch to process.
            reinvoke();
        }
    }
}
