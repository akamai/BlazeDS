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

import java.io.IOException;

/**
 * Base class for suspendable AMF filter implementations.
 * This class performs no internal synchronization.
 */
public abstract class SuspendableAMFFilter
{
    //--------------------------------------------------------------------------
    //
    // Public Static Constants
    //
    //--------------------------------------------------------------------------    

    /**
     * Indicates a filter is in its inbound processing state.
     * 
     * @see #getDirection()
     */
    public static final int DIRECTION_INBOUND = 0;
    
    /**
     * Indicates a filter is in its outbound processing state.
     * 
     * @see #getDirection()
     */
    public static final int DIRECTION_OUTBOUND = 1;
    
    //--------------------------------------------------------------------------
    //
    // Public Static Methods
    //
    //--------------------------------------------------------------------------    

    /**
     * Utility method that takes an array of filters and connects them into a chain,
     * preserving their relative ordering.
     * 
     * @param members The individual filters for the chain in their desired order.
     * @return The first filter in the chain.
     */
    public static SuspendableAMFFilter buildChain(SuspendableAMFFilter[] members)
    {
        if ((members == null) || (members.length == 0))
            throw new IllegalArgumentException();
        
        int n = members.length;
        for (int i = 0; i < n; i++)
        {
            if (i > 0)
                members[i].setPrevious(members[i - 1]);
            if (i < n - 1)
                members[i].setNext(members[i + 1]);
        }
        return members[0];
    }

    //--------------------------------------------------------------------------
    //
    // Constructor
    //
    //--------------------------------------------------------------------------    

    /**
     * Constructs a <tt>SuspendableAMFFilter</tt>.
     */
    public SuspendableAMFFilter() 
    {        
    }

    //--------------------------------------------------------------------------
    //
    // Properties
    //
    //--------------------------------------------------------------------------    

    //----------------------------------
    //  inboundAborted
    //----------------------------------            

    protected boolean inboundAborted;
    
    /**
     * Returns <code>true</code> if inbound processing for the chain has been aborted.
     */
    public boolean isInboundAborted()
    {
        return inboundAborted;
    }
    
    /**
     * Flags inbound processing as aborted.
     * Alternately, re-enables inbound processing.
     */
    public void setInboundAborted(boolean value)
    {
        inboundAborted = value;
    }    
    
    //----------------------------------
    //  context
    //----------------------------------            

    protected ActionContext context;
    
    /**
     * Returns the <tt>ActionContext</tt> for the chain.
     */
    public ActionContext getContext()
    {
        return context;
    }
    
    /**
     * Sets the <tt>ActionContext</tt> for the chain.
     */
    public void setContext(ActionContext value)
    {
        context = value;
    }
    
    //----------------------------------
    //  direction
    //----------------------------------            
    
    protected int direction = DIRECTION_INBOUND;
    
    /**
     * Returns the current processing direction for the chain.
     * 
     * @see #DIRECTION_INBOUND
     * @see #DIRECTION_OUTBOUND
     */
    public int getDirection()
    {
        return direction;
    }
    
    /**
     * Sets the current processing direction for the chain.
     * 
     * @see #DIRECTION_INBOUND
     * @see #DIRECTION_OUTBOUND
     */
    public void setDirection(int value)
    {
        direction = value;
    }
    
    //----------------------------------
    //  next
    //----------------------------------    

    protected SuspendableAMFFilter next;

    /**
     * Returns the filter that follows this filter in a chain.
     */
    public SuspendableAMFFilter getNext()
    {
        return next;
    }
    
    /**
     * Assigns a filter to follow this filter in a chain.
     */
    public void setNext(SuspendableAMFFilter value)
    {
        next = value;
    }
    
    //----------------------------------
    //  previous
    //----------------------------------    
    
    protected SuspendableAMFFilter previous;
    
    /**
     * Returns the filter that precedes this filter in a chain.
     */
    public SuspendableAMFFilter getPrevious()
    {
        return previous;
    }

    /**
     * Assigns a filter to precede this filter in a chain.
     */
    public void setPrevious(SuspendableAMFFilter value)
    {
        previous = value;
    }
    
    //----------------------------------
    //  suspended
    //----------------------------------    
    
    /**
     * Returns <code>true</code> if the chain this filter belongs to is suspended.
     */
    public boolean isSuspended()
    {
        return (getSuspendedFilter() != null);
    }

    //----------------------------------
    //  suspendedFilter
    //----------------------------------        
    
    protected SuspendableAMFFilter suspendedFilter;
    
    /**
     * Returns the specific filter within a chain that has been suspended and may be resumed.
     */
    public SuspendableAMFFilter getSuspendedFilter()
    {
        return suspendedFilter;
    }
    
    /**
     * Assigns a reference to the specific filter in the chain that is currently suspended.
     */
    public void setSuspendedFilter(SuspendableAMFFilter value)
    {
        suspendedFilter = value;
    }
    
    //--------------------------------------------------------------------------
    //
    // Public Methods
    //
    //--------------------------------------------------------------------------            

    /**
     * Invokes the chain.
     * 
     * @throws IllegalStateException If the chain is suspended; use <code>resume()</code> to resume a suspended chain's invocation.
     */
    public void invoke(final ActionContext context) throws IOException
    {
        if (isSuspended())
            throw new IllegalStateException();
        
        // Execute the chain from this filter.
        setContext(context);
        setDirection(DIRECTION_INBOUND);
        executeChain(this, true);
    }
    
    /**
     * Resumes the execution of a suspended chain.
     * 
     * @throws IllegalStateException If the chain is not suspended; use <code>invoke(ActionContext)</code> to execute an unsuspended chain.
     */
    public void resume() throws IOException
    {
        if (!isSuspended())
            throw new IllegalStateException();
        
        SuspendableAMFFilter suspensionPoint = getSuspendedFilter();
        
        // Un-suspend chain; sets 'suspendedFilter' to 'null' on all chain members.
        SuspendableAMFFilter chainMember = this;
        chainMember.setSuspendedFilter(null);
        while ((chainMember = chainMember.getPrevious()) != null)
            chainMember.setSuspendedFilter(null);
        chainMember = this;
        while ((chainMember = chainMember.getNext()) != null)
            chainMember.setSuspendedFilter(null);
        
        // Execute the chain from its suspension point.
        executeChain(suspensionPoint, true);
    }
    
    //--------------------------------------------------------------------------
    //
    // Protected Methods
    //
    //--------------------------------------------------------------------------            
    
    /**
     * This method is invoked for incoming context processing.
     * 
     * @param context The <tt>ActionContext</tt> associated with the current invocation of the chain.
     */
    protected abstract void doInboundFilter(final ActionContext context) throws IOException;

    /**
     * This method is invoked for outbound context processing.
     * 
     * @param context The <tt>ActionContext</tt> associated with the current invocation of the chain.
     */
    protected abstract void doOutboundFilter(final ActionContext context) throws IOException;
    
    /**
     * Helper method used by <code>invoke(ActionContext)</code> and <code>resume()</code> to execute
     * the chain.
     * 
     * @param executionPoint The filter in the chain to begin execution at.
     * @param executePreviousOutboundFilters <code>true</code> in the general case; <code>false</code> when this method is called
     *        by a <code>reinvoke()</code> in which case only filters from the execution point forward should execute.
     */
    protected void executeChain(SuspendableAMFFilter executionPoint, boolean executePreviousOutboundFilters) throws IOException
    {
        SuspendableAMFFilter completionPoint = (!executePreviousOutboundFilters) ? executionPoint : null;
        SuspendableAMFFilter previous = executionPoint;
        
        // Process inbound.
        int direction = executionPoint.getDirection();
        if (direction == DIRECTION_INBOUND)
        {
            // Lazily assign context and direction to allow filters that may loop their inbound processing to allow 
            // them to detect a processing loop dealing with an existing context or processing of a brand new context.
            while (true)
            {
                // Advance previous ref.
                previous = executionPoint;
                
                executionPoint.doInboundFilter(context);
                
                // Check for suspension.
                if (isSuspended())
                {
                    // Leave the chain in its current state.
                    return;
                }
                else if (executionPoint.isInboundAborted())
                {
                    // Abort all further inbound processing for this context and jump to outbound processing.
                    executionPoint.setInboundAborted(false);
                    executionPoint.setDirection(DIRECTION_OUTBOUND);
                    break;
                }
                else
                {
                    // Flip processing direction for this chain member and advance (below).
                    executionPoint.setDirection(DIRECTION_OUTBOUND);
                }
                
                // Advance execution point.
                executionPoint = executionPoint.getNext();
                if (executionPoint != null)
                {
                    // Update context and direction for the next execution point.
                    executionPoint.setContext(context);
                    executionPoint.setDirection(DIRECTION_INBOUND);
                }
                else
                {
                    break; // Done.
                }
            }             
        }
        
        // Process outbound.
        direction = previous.getDirection();
        if (direction == DIRECTION_OUTBOUND)
        {     
            // All previous filters have the proper context and direction assigned at this point.
            do
            {
                previous.doOutboundFilter(context);
                
                // Check for suspension.
                if (isSuspended())
                    return;
                
                if (previous == completionPoint)
                    return;
            }
            while ((previous = previous.getPrevious()) != null);
        }
    }    
    
    /**
     * Members of the chain may need to iterate chain processing from their current position forward.
     * This method may be invoked from a filter's <code>doOutboundFilter()</code> if it determines that
     * it (and the filters that follow it) require another processing cycle.
     * Any preceeding filters in the chain will not be reinvoked.
     */
    protected void reinvoke() throws IOException
    {
        setDirection(DIRECTION_INBOUND);
        // Use the existing context.
        executeChain(this, false /* Don't execute previous filters */);
    }
    
    /**
     * Members of the chain may need to suspend their processing and resume it at a later point.
     * They may invoke this method to place the entire chain in a suspended state.
     * Before invoking this method, the specific filter requesting suspension should store any
     * state it will require upon resumption.
     * When the chain is resumed, either <code>doInboundFilter(ActionContext)</code> or <code>doOutboundFilter(ActionContext)</code>
     * will be reinvoked, depending on which of these methods the filter was in when it requested suspension.
     * 
     * @throws IllegalStateException If the chain is already suspended.
     */
    protected void suspend()
    {
        if (isSuspended())
            throw new IllegalStateException(); // Already suspended.
        
        SuspendableAMFFilter chainMember = this;
        chainMember.setSuspendedFilter(this);
        while ((chainMember = chainMember.getPrevious()) != null)
            chainMember.setSuspendedFilter(this);
        chainMember = this;
        while ((chainMember = chainMember.getNext()) != null)
            chainMember.setSuspendedFilter(this);
    }
}
