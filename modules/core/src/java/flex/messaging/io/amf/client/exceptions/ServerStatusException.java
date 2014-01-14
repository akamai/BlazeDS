/*************************************************************************
 *
 * ADOBE CONFIDENTIAL
 * ___________________
 *
 *  2008 Adobe Systems Incorporated
 *  All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of Adobe Systems Incorporated and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Adobe Systems Incorporated and its
 * suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Adobe Systems Incorporated.
 **************************************************************************/

package flex.messaging.io.amf.client.exceptions;

/**
 * Server status exceptions are thrown by the AMF connection when a server side
 * error is encountered.
 */
public class ServerStatusException extends Exception
{
    private Object data;
    private HttpResponseInfo httpResponseInfo;

    /**
     * Creates a server status exception with the supplied message and data.
     * 
     * @param message The message of the exception.
     * @param data The data of the exception which is usually an AMF result or 
     * status message.
     */
    public ServerStatusException(String message, Object data)
    {
        this(message, data, null);
    }

    /**
     * Creates a server status exception with the supplied message, data, and
     * HTTP response info object.
     * 
     * @param message The message of the exception.
     * @param data The data of the exception which is usually an AMF result or
     * status message.
     * @param httpResponseInfo The HTTP response info object that represents
     * the HTTP response returned with the exception.
     */
    public ServerStatusException(String message, Object data, HttpResponseInfo httpResponseInfo)
    {
        super(message);
        this.data = data;
        this.httpResponseInfo = httpResponseInfo;
    }

    /**
     * Returns the data of the exception.
     * 
     * @return The data of the exception.
     */
    public Object getData()
    {
        return data;
    }

    /**
     * Returns the HTTP response info of the exception.
     * 
     * @return The HTTP response info of the exception.
     */
    public HttpResponseInfo getHttpResponseInfo()
    {
        return httpResponseInfo;
    }

    /**
     * Returns a String representation of the exception.
     * 
     * @return A String that represents the exception.
     */
    public String toString()
    {
        String temp = "ServerStatusException " + "\n\tdata: " + data;
        if (httpResponseInfo != null)
            temp += "\n\tHttpResponseInfo: " + httpResponseInfo;
        return temp;
    } 

    /**
     * An inner class to represent the HTTP response associated with the exception.
     */
    public static class HttpResponseInfo
    {
        private int responseCode;
        private String responseMessage;

        /**
         * Creates an HTTP response info with the HTTP code and message.
         * 
         * @param responseCode The HTTP response code.
         * @param responseMessage the HTTP message.
         */
        public HttpResponseInfo(int responseCode, String responseMessage)
        {
            this.responseCode = responseCode;
            this.responseMessage = responseMessage;
        }

        /**
         * Returns the HTTP response code.
         * 
         * @return The HTTP response code.
         */
        public int getResponseCode()
        {
            return responseCode;
        }

        /**
         * Returns the HTTP response message.
         * 
         * @return The HTTP response message.
         */
        public String getResponseMessage()
        {
            return responseMessage;
        }

        /**
         * Returns a String representation of the HTTP response info.
         * 
         * @return A String representation of the HTTP response info.
         */
        public String toString()
        {
            return "HttpResponseInfo " + "\n\tcode: " + responseCode 
                + "\n\tmessage: " + responseMessage;
        }
    }
}
