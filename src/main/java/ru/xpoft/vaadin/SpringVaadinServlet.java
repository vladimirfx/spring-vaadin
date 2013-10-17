package ru.xpoft.vaadin;

import com.vaadin.server.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextException;
import org.springframework.util.ClassUtils;
import org.springframework.web.context.ConfigurableWebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.springframework.web.context.support.XmlWebApplicationContext;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

/**
 * @author xpoft
 */
public class SpringVaadinServlet extends VaadinServlet
{
    private static Logger logger = LoggerFactory.getLogger(SpringVaadinServlet.class);
    /**
     * Servlet parameter name for system message bean
     */
    private static final String SYSTEM_MESSAGES_BEAN_NAME_PARAMETER = "systemMessagesBeanName";
    private static final String CONTEXT_CONFIG_LOCATION_PARAMETER = "contextConfigLocation";
    private static final String CONTEXT_CLASS_PARAMETER = "contextClass";
    /**
     * Spring Application Context
     */
    private transient ApplicationContext applicationContext;
    /**
     * system message bean name
     */
    private String systemMessagesBeanName = "";

    @Override
    public void init(ServletConfig config) throws ServletException {
        applicationContext = WebApplicationContextUtils.getWebApplicationContext(config.getServletContext());

        if (config.getInitParameter(CONTEXT_CONFIG_LOCATION_PARAMETER) != null) {
            Class contextClass = XmlWebApplicationContext.class;
            if (config.getInitParameter(CONTEXT_CLASS_PARAMETER) != null) {
                String contextClassName = config.getInitParameter(CONTEXT_CLASS_PARAMETER);
                try {
                    contextClass = ClassUtils.forName(contextClassName, ClassUtils.getDefaultClassLoader());
                } catch (ClassNotFoundException ex) {
                    throw new ApplicationContextException(
                            "Failed to load custom context class [" + contextClassName + "]", ex);
                }
            }
            if (!ConfigurableWebApplicationContext.class.isAssignableFrom(contextClass)) {
                throw new ApplicationContextException("Custom context class [" + contextClass.getName() +
                        "] is not of type [" + ConfigurableWebApplicationContext.class.getName() + "]");
            }
            ConfigurableWebApplicationContext context = (ConfigurableWebApplicationContext) BeanUtils.instantiateClass(contextClass);
            context.setParent(applicationContext);
            context.setConfigLocation(config.getInitParameter(CONTEXT_CONFIG_LOCATION_PARAMETER));
            context.setServletConfig(config);
            context.setServletContext(config.getServletContext());
            context.refresh();

            applicationContext = context;
        }

        if (config.getInitParameter(SYSTEM_MESSAGES_BEAN_NAME_PARAMETER) != null)
        {
            systemMessagesBeanName = config.getInitParameter(SYSTEM_MESSAGES_BEAN_NAME_PARAMETER);
            logger.debug("found SYSTEM_MESSAGES_BEAN_NAME_PARAMETER: {}", systemMessagesBeanName);
        }

        if (SpringApplicationContext.getApplicationContext() == null)
        {
            SpringApplicationContext.setApplicationContext(applicationContext);
        }

        super.init(config);
    }

    protected void initializePlugin(VaadinServletService service)
    {
        // Spring system messages provider
        if (systemMessagesBeanName != null && systemMessagesBeanName != "")
        {
            SpringVaadinSystemMessagesProvider messagesProvider = new SpringVaadinSystemMessagesProvider(applicationContext, systemMessagesBeanName);
            logger.debug("set SpringVaadinSystemMessagesProvider");
            service.setSystemMessagesProvider(messagesProvider);
        }

        String uiProviderProperty = service.getDeploymentConfiguration().getApplicationOrSystemProperty(Constants.SERVLET_PARAMETER_UI_PROVIDER, null);

        // Add SpringUIProvider if custom provider doesn't defined.
        if (uiProviderProperty == null)
        {
            service.addSessionInitListener(new SessionInitListener()
            {
                @Override
                public void sessionInit(SessionInitEvent event) throws ServiceException
                {
                    event.getSession().addUIProvider(new SpringUIProvider());
                }
            });
        }
    }

    @Override
    protected VaadinServletService createServletService(DeploymentConfiguration deploymentConfiguration) throws ServiceException
    {
        final VaadinServletService service = super.createServletService(deploymentConfiguration);
        initializePlugin(service);
        return service;
    }
}
