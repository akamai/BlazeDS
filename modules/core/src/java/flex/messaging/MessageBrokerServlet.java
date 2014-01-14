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
package flex.messaging;

import edu.emory.mathcs.backport.java.util.concurrent.ConcurrentHashMap;

import flex.management.MBeanLifecycleManager;
import flex.management.MBeanServerLocatorFactory;
import flex.messaging.config.ConfigurationManager;
import flex.messaging.config.FlexConfigurationManager;
import flex.messaging.config.MessagingConfiguration;
import flex.messaging.endpoints.Endpoint;
import flex.messaging.log.LogCategories;
import flex.messaging.log.ServletLogTarget;
import flex.messaging.log.Log;
import flex.messaging.log.Logger;
import flex.messaging.services.AuthenticationService;
import flex.messaging.util.ExceptionUtil;
import flex.messaging.util.Trace;
import flex.messaging.util.ClassUtil;
import flex.messaging.io.SerializationContext;
import flex.messaging.io.TypeMarshallingContext;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.InputStream;
import java.io.IOException;
import java.security.Principal;

/**
 * The MessageBrokerServlet bootstraps the MessageBroker,
 * adds endpoints to it, and starts the broker. The servlet
 * also acts as a facade for all http-based endpoints, in that
 * the servlet receives the http request and then delegates to
 * an endpoint that can handle the request's content type. This
 * does not occur for non-http endpoints, such as the rtmp endpoint.
 *
 * @author sneville
 * @see flex.messaging.MessageBroker
 * @exclude
 */
public class MessageBrokerServlet extends HttpServlet
{
    static final long serialVersionUID = -5293855229461612246L;

    public static final String LOG_CATEGORY_STARTUP_BROKER = LogCategories.STARTUP_MESSAGEBROKER;

    private MessageBroker broker;
    private static String FLEXDIR = "/WEB-INF/flex/";

    /**
     * Initializes the servlet in its web container, then creates
     * the MessageBroker and adds Endpoints and Services to that broker.
     * This servlet may keep a reference to an endpoint if it needs to
     * delegate to it in the <code>service</code> method.
     */
    public void init(ServletConfig servletConfig)
            throws ServletException, UnavailableException
    {
        super.init(servletConfig);

        // allocate thread local variables
        createThreadLocals();
        
        // Set the servlet config as thread local
        FlexContext.setThreadLocalObjects(null, null, null, null, null, servletConfig);

        ServletLogTarget.setServletContext(servletConfig.getServletContext());

        ClassLoader loader = getClassLoader();

        String useCCLoader;

        if ((useCCLoader = servletConfig.getInitParameter("useContextClassLoader")) != null &&
             useCCLoader.equalsIgnoreCase("true"))
            loader = Thread.currentThread().getContextClassLoader();

        // Start the broker
        try
        {
            // Get the configuration manager
            ConfigurationManager configManager = loadMessagingConfiguration(servletConfig);

            // Load configuration
            MessagingConfiguration config = configManager.getMessagingConfiguration(servletConfig);

            // Set up logging system ahead of everything else.
            config.createLogAndTargets();

            // Create broker.
            broker = config.createBroker(servletConfig.getInitParameter("messageBrokerId"), loader);

            // Set the servlet config as thread local
            FlexContext.setThreadLocalObjects(null, null, broker, null, null, servletConfig);

            setupInternalPathResolver();

            // Set initial servlet context on broker
            broker.setInitServletContext(servletConfig.getServletContext());

            Logger logger = Log.getLogger(ConfigurationManager.LOG_CATEGORY);
            if (Log.isInfo())
            {
                logger.info(VersionInfo.buildMessage());
            }

            // Create endpoints, services, security, and logger on the broker based on configuration
            config.configureBroker(broker);

            long timeBeforeStartup = 0;
            if (Log.isDebug())
            {
                timeBeforeStartup = System.currentTimeMillis();
                Log.getLogger(LOG_CATEGORY_STARTUP_BROKER).debug("MessageBroker with id '{0}' is starting.",
                        new Object[]{broker.getId()});
            }

            //initialize the httpSessionToFlexSessionMap
            synchronized(HttpFlexSession.mapLock)
            {
                if (servletConfig.getServletContext().getAttribute(HttpFlexSession.SESSION_MAP) == null)
                    servletConfig.getServletContext().setAttribute(HttpFlexSession.SESSION_MAP, new ConcurrentHashMap());
            }

            broker.start();

            if (Log.isDebug())
            {
                long timeAfterStartup = System.currentTimeMillis();
                Long diffMillis = new Long(timeAfterStartup - timeBeforeStartup);
                Log.getLogger(LOG_CATEGORY_STARTUP_BROKER).debug("MessageBroker with id '{0}' is ready (startup time: '{1}' ms)",
                        new Object[]{broker.getId(), diffMillis});
            }

            // Report replaced tokens
            configManager.reportTokens();

            // Report any unused properties.
            config.reportUnusedProperties();

            // clear the broker and servlet config as this thread is done
            FlexContext.clearThreadLocalObjects();
        }
        catch (Throwable t)
        {
            // On any unhandled exception destroy the broker, log it and rethrow.
            System.err.println("**** MessageBrokerServlet failed to initialize due to runtime exception: " + ExceptionUtil.exceptionFollowedByRootCausesToString(t));
            destroy();
            throw new UnavailableException(t.getMessage());
        }
    }

    private void setupInternalPathResolver()
    {
        broker.setInternalPathResolver(
            new MessageBroker.InternalPathResolver()
            {
                public InputStream resolve(String filename)
                {
                    InputStream is = getServletContext().getResourceAsStream(FLEXDIR + filename);
                    return is;
                }
            }
        );
    }

    private ConfigurationManager loadMessagingConfiguration(ServletConfig servletConfig)
    {
        ConfigurationManager manager = null;
        Class managerClass = null;
        String className = null;

        // Check for Custom Configuration Manager Specification
        if (servletConfig != null)
        {
            String p = servletConfig.getInitParameter("services.configuration.manager");
            if (p != null)
            {
                className = p.trim();
                try
                {
                    managerClass = ClassUtil.createClass(className);
                    manager = (ConfigurationManager)managerClass.newInstance();
                }
                catch (Throwable t)
                {
                    if (Trace.config) // Log is not initialized yet.
                        Trace.trace("Could not load configuration manager as: " + className);
                }
            }
        }

        if (manager == null)
        {
            manager = (ConfigurationManager)new FlexConfigurationManager();
        }

        return manager;
    }

    /**
     * Stops all endpoints in the MessageBroker, giving them a chance
     * to perform any endpoint-specific clean up.
     */
    public void destroy()
    {
        if (broker != null)
        {
            broker.stop();
            if (broker.isManaged())
            {
                MBeanLifecycleManager.unregisterRuntimeMBeans(broker);
            }
            // release static thread locals
            destroyThreadLocals();
        }
    }

    /**
     * Handle an incoming request, and delegate to an endpoint based on
     * content type, if appropriate. The content type mappings for endpoints
     * are not externally configurable, and currently the AmfEndpoint
     * is the only delegate.
     */
    public void service(HttpServletRequest req, HttpServletResponse res)
    {
        try
        {
            // Update thread locals
            broker.initThreadLocals();
            // Set this first so it is in place for the session creation event.  The
            // current session is set by the FlexSession stuff right when it is available.
            // The threadlocal FlexClient is set up during message deserialization in the
            // MessageBrokerFilter.
            FlexContext.setThreadLocalObjects(null, null, broker, req, res, getServletConfig());
            HttpFlexSession fs = HttpFlexSession.getFlexSession(req);
            Principal principal = null;
            if(FlexContext.isPerClientAuthentication())
            {
                principal = FlexContext.getUserPrincipal();
            }
            else
            {
                principal = fs.getUserPrincipal();
            }

            if (principal == null && req.getHeader("Authorization") != null)
            {
                String encoded = req.getHeader("Authorization");
                if (encoded.indexOf("Basic") > -1)
                {
                    encoded = encoded.substring(6); //Basic.length()+1
                    try
                    {
                        AuthenticationService.decodeAndLogin(encoded, broker.getLoginManager());
                    }
                    catch (Exception e)
                    {
                        if (Log.isDebug())
                            Log.getLogger(LogCategories.SECURITY).info("Authentication service could not decode and login: " + e.getMessage());
                    }
                }
            }

            String contextPath = req.getContextPath();
            String pathInfo = req.getPathInfo();
            String endpointPath = req.getServletPath();
            if (pathInfo != null)
                endpointPath = endpointPath + pathInfo;

            Endpoint endpoint = null;
            try
            {
                endpoint = broker.getEndpoint(endpointPath, contextPath);
            }
            catch (MessageException me)
            {
                if (Log.isInfo())
                    Log.getLogger(LogCategories.ENDPOINT_GENERAL).info("Received invalid request for endpoint path '{0}'.", new Object[] {endpointPath});
                
                if (!res.isCommitted())
                {
                    try
                    {                    
                        res.sendError(HttpServletResponse.SC_NOT_FOUND);
                    }
                    catch (IOException ignore)
                    {}
                }
                
                return;
            }
            
            try
            {
                if (Log.isInfo())
                {
                    Log.getLogger(LogCategories.ENDPOINT_GENERAL).info("Channel endpoint {0} received request.", 
                                                                       new Object[] {endpoint.getId()});
                }
                endpoint.service(req, res);
            }
            catch (UnsupportedOperationException ue)
            {
                if (Log.isInfo())
                {
                    Log.getLogger(LogCategories.ENDPOINT_GENERAL).info("Channel endpoint {0} received request for an unsupported operation.", 
                                                                       new Object[] {endpoint.getId()}, 
                                                                       ue);
                }
                
                if (!res.isCommitted())
                {
                    try
                    {                        
                        res.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                    }
                    catch (IOException ignore)
                    {}
                }
            }
        }
        finally
        {
            FlexContext.clearThreadLocalObjects();
        }
    }

    /**
     * Hook for subclasses to override the class loader to use for loading
     * user defined classes.
     */
    protected ClassLoader getClassLoader()
    {
        return this.getClass().getClassLoader();
    }

    /** @exclude */
    // Call ONLY on servlet startup
    public void createThreadLocals()
    {
        // allocate static thread local objects
        MessageBroker.createThreadLocalObjects();
        FlexContext.createThreadLocalObjects();
        SerializationContext.createThreadLocalObjects();
        TypeMarshallingContext.createThreadLocalObjects();
    }
    
    /** @exclude */
    // Call ONLY on servlet shutdown
    protected void destroyThreadLocals()
    {
        // clear static member variables
        Log.clear();
        MBeanServerLocatorFactory.clear();

        // Destroy static thread local objects
        MessageBroker.releaseThreadLocalObjects();
        FlexContext.releaseThreadLocalObjects();
        SerializationContext.releaseThreadLocalObjects();
        TypeMarshallingContext.releaseThreadLocalObjects();
    }
    
}
