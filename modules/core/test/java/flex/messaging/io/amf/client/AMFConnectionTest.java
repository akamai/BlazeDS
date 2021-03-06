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

package flex.messaging.io.amf.client;

import java.net.HttpURLConnection;

import junit.framework.Assert;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import amfclient.ClientCustomType;

import flex.messaging.MessageException;
import flex.messaging.io.amf.ASObject;
import flex.messaging.io.amf.client.exceptions.ClientStatusException;
import flex.messaging.io.amf.client.exceptions.ServerStatusException;
import flex.messaging.io.amf.client.exceptions.ServerStatusException.HttpResponseInfo;
import flex.messaging.io.MessageIOConstants;


/**
 * JUnit tests for AMFConnection. Note that most of the tests require a running
 * server with the specified destination.
 */
public class AMFConnectionTest extends TestCase
{
    private static final String DEFAULT_DESTINATION_ID = "amfConnectionTestService";
    private static final String DEFAULT_METHOD_NAME = "echoString";
    private static final String DEFAULT_METHOD_ARG = "echo me";
    private static final String DEFAULT_URL = "http://localhost:8400/qa-regress/messagebroker/amf";
    private static final String DEFAULT_AMF_OPERATION = getOperationCall(DEFAULT_METHOD_NAME);
    private static final String FOO_STRING = "foo";
    private static final String BAR_STRING = "bar";
    private static final String UNEXPECTED_EXCEPTION_STRING = "Unexpected exception: ";

    /**
     * Given a remote method name, returns the AMF connection call needed using
     * the default destination id.
     */
    private static String getOperationCall(String method)
    {
        return DEFAULT_DESTINATION_ID + "." + method;
    }


    public AMFConnectionTest(String name)
    {
        super(name);
    }

    public static Test suite()
    {
        //TestSuite suite = new TestSuite(AMFConnectionTest.class);
        TestSuite suite = new TestSuite();
        suite.addTest(new AMFConnectionTest("testConnect"));
        suite.addTest(new AMFConnectionTest("testConnectAndClose"));
        suite.addTest(new AMFConnectionTest("testConnectBadUrl"));
        suite.addTest(new AMFConnectionTest("testCallMultipleTimes"));
        suite.addTest(new AMFConnectionTest("testCallNoConnect"));
        suite.addTest(new AMFConnectionTest("testCallNoConnectStringMsg"));
        suite.addTest(new AMFConnectionTest("testCallUnreachableConnectUrl"));
        suite.addTest(new AMFConnectionTest("testCallNonexistantMethod"));
        suite.addTest(new AMFConnectionTest("testHttpResponseInfoWithNonexistantMethod"));
        suite.addTest(new AMFConnectionTest("testCloseNoConnect"));
        suite.addTest(new AMFConnectionTest("testSetGetObjectEncoding"));
        suite.addTest(new AMFConnectionTest("testSetGetDefaultObjectEncoding"));
        suite.addTest(new AMFConnectionTest("testSetGetAMFHeaderProcessor"));
        suite.addTest(new AMFConnectionTest("testAddRemoveAMFHeaderTwoParam"));
        suite.addTest(new AMFConnectionTest("testAddRemoveAMFHeader"));
        suite.addTest(new AMFConnectionTest("testAddRemoveAllAMFHeaders"));
        suite.addTest(new AMFConnectionTest("testAddRemoveHTTPRequestHeader"));
        suite.addTest(new AMFConnectionTest("testAddRemoveAllHTTPRequestHeaders"));
        suite.addTest(new AMFConnectionTest("testRemoveAMFHeader"));
        suite.addTest(new AMFConnectionTest("testRemoveAllAMFHeaders"));
        suite.addTest(new AMFConnectionTest("testRemoveHTTPRequestHeader"));
        suite.addTest(new AMFConnectionTest("testRemoveAllHTTPRequestHeaders"));
        suite.addTest(new AMFConnectionTest("testInstantiateTypes"));
        return suite;
    }

    protected void setUp() throws Exception
    {
        AMFConnection.registerAlias(
                "remoting.amfclient.ServerCustomType" /* server type */,
                "amfclient.ClientCustomType" /* client type */);
        super.setUp();
    }

    // Not a test, just an example to show how to use AMFConnection.
    public void example()
    {
        // Create the AMF connection.
        AMFConnection amfConnection = new AMFConnection();

        // Connect to the remote url.
        try
        {
            amfConnection.connect(DEFAULT_URL);
        }
        catch (ClientStatusException cse)
        {
            return;
        }

        // Make a remoting call and retrieve the result.
        try
        {
            Object result = amfConnection.call(DEFAULT_AMF_OPERATION, DEFAULT_METHOD_ARG);
            Assert.assertEquals(DEFAULT_METHOD_ARG, result);
        }
        catch (ClientStatusException cse)
        {
        }
        catch (ServerStatusException sse)
        {
        }

        // Close the connection.
        amfConnection.close();
    }

    public void testConnect()
    {
        AMFConnection amfConnection = new AMFConnection();
        try
        {
            amfConnection.connect(DEFAULT_URL);
            Assert.assertEquals(DEFAULT_URL, amfConnection.getUrl());
        }
        catch (ClientStatusException cse)
        {
            fail(UNEXPECTED_EXCEPTION_STRING + cse);
        }
        finally
        {
            amfConnection.close();
        }
    }

    public void testConnectAndClose()
    {
        AMFConnection amfConnection = new AMFConnection();
        try
        {
            amfConnection.connect(DEFAULT_URL);
            Assert.assertEquals(DEFAULT_URL, amfConnection.getUrl());
        }
        catch (ClientStatusException cse)
        {
            fail(UNEXPECTED_EXCEPTION_STRING + cse);
        }
        finally
        {
            amfConnection.close();
            Assert.assertEquals(null, amfConnection.getUrl());
        }
    }

    public void testConnectBadUrl()
    {
        String badUrl = "badUrl";
        AMFConnection amfConnection = new AMFConnection();
        try
        {
            amfConnection.connect(badUrl);
            fail("ClientStatusException expected");
        }
        catch (ClientStatusException cse)
        {
            Assert.assertEquals(ClientStatusException.AMF_CONNECT_FAILED_CODE, cse.getCode());
        }
        finally
        {
            amfConnection.close();
        }
    }

    public void testCallMultipleTimes()
    {
        AMFConnection amfConnection = new AMFConnection();
        try
        {
            amfConnection.connect(DEFAULT_URL);
        }
        catch (ClientStatusException cse)
        {
            fail(UNEXPECTED_EXCEPTION_STRING + cse);
        }
        // Make a remoting call and retrieve the result.
        try
        {
            for (int i = 1; i < 4; i++)
            {
                String stringToEcho = DEFAULT_METHOD_ARG + i;
                Object result = amfConnection.call(DEFAULT_AMF_OPERATION, stringToEcho);
                Assert.assertEquals(stringToEcho, result);
            }
        }
        catch (Exception e)
        {
            fail(UNEXPECTED_EXCEPTION_STRING + e);
        }
        finally
        {
            amfConnection.close();
        }
    }

    public void testCallNoConnect()
    {
        AMFConnection amfConnection = new AMFConnection();
        // Make a remoting call without connect.
        try
        {
            Object result = amfConnection.call(DEFAULT_AMF_OPERATION, DEFAULT_METHOD_ARG);
            Assert.assertEquals(DEFAULT_METHOD_ARG, result);
        }
        catch (ClientStatusException cse)
        {
            Assert.assertEquals(ClientStatusException.AMF_CALL_FAILED_CODE, cse.getCode());
        }
        catch (Exception e)
        {
            fail(UNEXPECTED_EXCEPTION_STRING + e);
        }
        finally
        {
            amfConnection.close();
        }
    }

    public void testCallNoConnectStringMsg()
    {
        AMFConnection amfConnection = new AMFConnection();
        // Make a remoting call without connect.
        try
        {
            Object result = amfConnection.call(DEFAULT_AMF_OPERATION, DEFAULT_METHOD_ARG);
            Assert.assertEquals(DEFAULT_METHOD_ARG, result);
        }
        catch (ClientStatusException cse)
        {
            Assert.assertEquals(ClientStatusException.AMF_CALL_FAILED_CODE, cse.getCode());
        }
        catch (Exception e)
        {
            fail(UNEXPECTED_EXCEPTION_STRING + e);
        }
        finally
        {
            amfConnection.close();
        }
    }

    public void testCallUnreachableConnectUrl()
    {
        String unreachableUrl = "http://localhost:8400/team/messagebroker/unreachable";
        AMFConnection amfConnection = new AMFConnection();
        try
        {
            // Connect does not actually connect but simply sets the url.
            amfConnection.connect(unreachableUrl);
        }
        catch (ClientStatusException cse)
        {
            fail(UNEXPECTED_EXCEPTION_STRING + cse);
        }
        // Make a remoting call and retrieve the result.
        try
        {
            Object result = amfConnection.call(DEFAULT_AMF_OPERATION, DEFAULT_METHOD_ARG);
            Assert.assertEquals(DEFAULT_METHOD_ARG, result);
        }
        catch (ClientStatusException cse)
        {
            Assert.assertEquals(ClientStatusException.AMF_CALL_FAILED_CODE, cse.getCode());
        }
        catch (Exception e)
        {
            fail(UNEXPECTED_EXCEPTION_STRING + e);
        }
        finally
        {
            amfConnection.close();
        }
    }

    public void testCallNonexistantMethod()
    {
        String method = "nonExistantMethod";
        final ClientCustomType methodArg = new ClientCustomType();
        methodArg.setId(1);
        try
        {
            internalTestCall(getOperationCall(method), methodArg, new CallResultHandler(){
                public void onResult(Object result)
                {
                    fail("Unexcepted result: " + result);
                }
            });
        }
        catch (ServerStatusException sse)
        {
            ASObject status = (ASObject)sse.getData();
            String code = (String)status.get("code");
            Assert.assertEquals(MessageException.CODE_SERVER_RESOURCE_UNAVAILABLE, code);
            HttpResponseInfo info = sse.getHttpResponseInfo();
            // AMF status messages are reported as HTTP_OK still.
            Assert.assertEquals(HttpURLConnection.HTTP_OK, info.getResponseCode());
            Assert.assertEquals("OK", info.getResponseMessage());
        }
        catch (Exception e)
        {
            fail(UNEXPECTED_EXCEPTION_STRING + e);
        }
    }

    public void testHttpResponseInfoWithNonexistantMethod()
    {
        String method = "nonExistantMethod";
        final ClientCustomType methodArg = new ClientCustomType();
        methodArg.setId(1);
        try
        {
            internalTestCall(getOperationCall(method), methodArg, new CallResultHandler(){
                public void onResult(Object result)
                {
                    fail("Unexcepted result: " + result);
                }
            });
        }
        catch (ServerStatusException sse)
        {
            HttpResponseInfo info = sse.getHttpResponseInfo();
            // AMF status messages are reported as HTTP_OK still.
            Assert.assertEquals(HttpURLConnection.HTTP_OK, info.getResponseCode());
            Assert.assertEquals("OK", info.getResponseMessage());
        }
        catch (Exception e)
        {
            fail(UNEXPECTED_EXCEPTION_STRING + e);
        }
    }
    public void testCloseNoConnect()
    {
        AMFConnection amfConnection = new AMFConnection();
        // Closing with no connection or call.
        try
        {
            amfConnection.close();
            Assert.assertEquals(null, amfConnection.getUrl());
        }
        catch (Exception e)
        {
            fail(UNEXPECTED_EXCEPTION_STRING + e);
        }
    }

    public void testSetGetObjectEncoding()
    {
        int retAMF;
        AMFConnection amfConnection = new AMFConnection();
        try
        {
            amfConnection.connect(DEFAULT_URL);
            Assert.assertEquals(DEFAULT_URL, amfConnection.getUrl());
            amfConnection.setObjectEncoding(MessageIOConstants.AMF0);
            retAMF = amfConnection.getObjectEncoding();
            Assert.assertEquals(MessageIOConstants.AMF0, retAMF);
        }
        catch (ClientStatusException cse)
        {
            fail(UNEXPECTED_EXCEPTION_STRING + cse);
        }
        finally
        {
            amfConnection.close();
        }
    }

    public void testSetGetDefaultObjectEncoding()
    {
        int retAMF;
        AMFConnection amfConnection = new AMFConnection();
        try
        {
            amfConnection.connect(DEFAULT_URL);
            Assert.assertEquals(DEFAULT_URL, amfConnection.getUrl());
            AMFConnection.setDefaultObjectEncoding(MessageIOConstants.AMF3);
            retAMF = AMFConnection.getDefaultObjectEncoding();
            Assert.assertEquals(MessageIOConstants.AMF3, retAMF);
        }
        catch (ClientStatusException cse)
        {
            fail(UNEXPECTED_EXCEPTION_STRING + cse);
        }
        finally
        {
            amfConnection.close();
        }
    }

    public void testSetGetAMFHeaderProcessor()
    {
        AMFHeaderProcessor setAMF = null;
        AMFHeaderProcessor retAMF;
        AMFConnection amfConnection = new AMFConnection();
        try
        {
            amfConnection.connect(DEFAULT_URL);
            Assert.assertEquals(DEFAULT_URL, amfConnection.getUrl());
            amfConnection.setAMFHeaderProcessor(setAMF);
            retAMF = amfConnection.getAMFHeaderProcessor();
            Assert.assertEquals(setAMF, retAMF);
        }
        catch (ClientStatusException cse)
        {
            fail(UNEXPECTED_EXCEPTION_STRING + cse);
        }
        finally
        {
            amfConnection.close();
        }
    }

    public void testAddRemoveAMFHeaderTwoParam()
    {
        boolean retAMF;
        Object val = 1;
        AMFConnection amfConnection = new AMFConnection();
        try
        {
            amfConnection.connect(DEFAULT_URL);
            Assert.assertEquals(DEFAULT_URL, amfConnection.getUrl());
            amfConnection.addAmfHeader(FOO_STRING,val);
            retAMF = amfConnection.removeAmfHeader(FOO_STRING);
            Assert.assertTrue(retAMF);
        }
        catch (ClientStatusException cse)
        {
            fail(UNEXPECTED_EXCEPTION_STRING + cse);
        }
        finally
        {
            amfConnection.close();
        }
    }

    public void testAddRemoveAMFHeader()
    {
        boolean retAMF;
        Object val = 1;
        AMFConnection amfConnection = new AMFConnection();
        try
        {
            amfConnection.connect(DEFAULT_URL);
            Assert.assertEquals(DEFAULT_URL, amfConnection.getUrl());
            amfConnection.addAmfHeader(FOO_STRING,true,val);
            retAMF = amfConnection.removeAmfHeader(FOO_STRING);
            Assert.assertTrue(retAMF);
        }
        catch (ClientStatusException cse)
        {
            fail(UNEXPECTED_EXCEPTION_STRING + cse);
        }
        finally
        {
            amfConnection.close();
        }
    }

    public void testAddRemoveAllAMFHeaders()
    {
        Object val1 = 1;
        Object val2 = 2;
        AMFConnection amfConnection = new AMFConnection();
        try
        {
            amfConnection.connect(DEFAULT_URL);
            Assert.assertEquals(DEFAULT_URL, amfConnection.getUrl());
            amfConnection.addAmfHeader(FOO_STRING,true,val1);
            amfConnection.addAmfHeader(BAR_STRING,true,val2);
            amfConnection.removeAllAmfHeaders();
            Assert.assertTrue(true);
        }
        catch (ClientStatusException cse)
        {
            fail(UNEXPECTED_EXCEPTION_STRING + cse);
        }
        finally
        {
            amfConnection.close();
        }
    }

    public void testAddRemoveHTTPRequestHeader()
    {
        boolean retHttp;
        AMFConnection amfConnection = new AMFConnection();
        try
        {
            amfConnection.connect(DEFAULT_URL);
            Assert.assertEquals(DEFAULT_URL, amfConnection.getUrl());
            amfConnection.addHttpRequestHeader(FOO_STRING,BAR_STRING);
            retHttp = amfConnection.removeHttpRequestHeader(FOO_STRING);
            Assert.assertTrue(retHttp);
        }
        catch (ClientStatusException cse)
        {
            fail(UNEXPECTED_EXCEPTION_STRING + cse);
        }
        finally
        {
            amfConnection.close();
        }
    }

    public void testAddRemoveAllHTTPRequestHeaders()
    {
        AMFConnection amfConnection = new AMFConnection();
        try
        {
            amfConnection.connect(DEFAULT_URL);
            Assert.assertEquals(DEFAULT_URL, amfConnection.getUrl());
            amfConnection.addHttpRequestHeader(FOO_STRING,BAR_STRING);
            amfConnection.addHttpRequestHeader(BAR_STRING,FOO_STRING);
            amfConnection.removeAllHttpRequestHeaders();
            Assert.assertTrue(true);
        }
        catch (ClientStatusException cse)
        {
            fail(UNEXPECTED_EXCEPTION_STRING + cse);
        }
        finally
        {
            amfConnection.close();
        }
    }

    public void testRemoveAMFHeader()
    {
        boolean retAMF;
        AMFConnection amfConnection = new AMFConnection();
        try
        {
            amfConnection.connect(DEFAULT_URL);
            Assert.assertEquals(DEFAULT_URL, amfConnection.getUrl());
            retAMF = amfConnection.removeAmfHeader(FOO_STRING);
            Assert.assertFalse(retAMF);
        }
        catch (ClientStatusException cse)
        {
            fail(UNEXPECTED_EXCEPTION_STRING + cse);
        }
        finally
        {
            amfConnection.close();
        }
    }

    public void testRemoveAllAMFHeaders()
    {
        AMFConnection amfConnection = new AMFConnection();
        try
        {
            amfConnection.connect(DEFAULT_URL);
            Assert.assertEquals(DEFAULT_URL, amfConnection.getUrl());
            amfConnection.removeAllAmfHeaders();
            Assert.assertTrue(true);
        }
        catch (ClientStatusException cse)
        {
            fail(UNEXPECTED_EXCEPTION_STRING + cse);
        }
        finally
        {
            amfConnection.close();
        }
    }

    public void testRemoveHTTPRequestHeader()
    {
        boolean retHttp;
        AMFConnection amfConnection = new AMFConnection();
        try
        {
            amfConnection.connect(DEFAULT_URL);
            Assert.assertEquals(DEFAULT_URL, amfConnection.getUrl());
            retHttp = amfConnection.removeHttpRequestHeader(FOO_STRING);
            Assert.assertFalse(retHttp);
        }
        catch (ClientStatusException cse)
        {
            fail(UNEXPECTED_EXCEPTION_STRING + cse);
        }
        finally
        {
            amfConnection.close();
        }
    }

    public void testRemoveAllHTTPRequestHeaders()
    {
        AMFConnection amfConnection = new AMFConnection();
        try
        {
            amfConnection.connect(DEFAULT_URL);
            Assert.assertEquals(DEFAULT_URL, amfConnection.getUrl());
            amfConnection.removeAllHttpRequestHeaders();
            Assert.assertTrue(true);
        }
        catch (ClientStatusException cse)
        {
            fail(UNEXPECTED_EXCEPTION_STRING + cse);
        }
        finally
        {
            amfConnection.close();
        }
    }

    
    public void testInstantiateTypes()
    {
        String method = "getObject2";
        try
        {
            AMFConnection amfConnection = new AMFConnection();
            amfConnection.connect(DEFAULT_URL);

            // First, make sure we get the strong type.
            Object result = amfConnection.call(getOperationCall(method));
            Assert.assertTrue(result instanceof ClientCustomType);

            // Now, call again with instantiateTypes=false and expect an Object.
            amfConnection.setInstantiateTypes(false);
            result = amfConnection.call(getOperationCall(method));
            Assert.assertTrue(!(result instanceof ClientCustomType));
            amfConnection.close();
        }
        catch (Exception e)
        {
            fail(UNEXPECTED_EXCEPTION_STRING + e);
        }
    }
    

    // A simple interface to handle AMF call results.
    private interface CallResultHandler
    {
        void onResult(Object result);
    }

    // Helper method used by JUnit tests to pass in an operation and method argument
    // When the AMF call returns, CallResultHandler.onResult is called to Assert things.
    private void internalTestCall(String operation, Object methodArg, CallResultHandler resultHandler) throws ClientStatusException, ServerStatusException
    {
        AMFConnection amfConnection = new AMFConnection();
        // Connect.
        amfConnection.connect(DEFAULT_URL);
        // Make a remoting call and retrieve the result.
        Object result;
        if (methodArg == null)
            result = amfConnection.call(operation);
        else
            result = amfConnection.call(operation, methodArg);
        resultHandler.onResult(result);
        amfConnection.close();
    }
}
