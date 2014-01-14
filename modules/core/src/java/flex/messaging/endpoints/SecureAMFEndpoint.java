/*************************************************************************
 *
 * ADOBE CONFIDENTIAL
 * __________________
 *
 *  [2002] - [2007] Adobe Systems Incorporated
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
package flex.messaging.endpoints;

/**
 * Secure version of AMFEndpoint.
 */
public class SecureAMFEndpoint extends AMFEndpoint
{
    //--------------------------------------------------------------------------
    //
    // Constructor
    //
    //--------------------------------------------------------------------------

    /**
     * Constructs an unmanaged <code>SecureAMFEndpoint</code>.
     */
    public SecureAMFEndpoint()
    {
        this(false);
    }

    /**
     * Constructs a <code>SecureAMFEndpoint</code> with the indicated management.
     *
     * @param enableManagement <code>true</code> if the <code>SecureAMFEndpoint</code>
     * is manageable; otherwise <code>false</code>.
     */
    public SecureAMFEndpoint(boolean enableManagement)
    {
        super(enableManagement);
    }

    //--------------------------------------------------------------------------
    //
    // Public Methods
    //
    //--------------------------------------------------------------------------

    /**
     * Determines whether the endpoint is secure or not.
     *
     * @return <code>true</code>.
     */
    public boolean isSecure()
    {
        return true;
    }
}
