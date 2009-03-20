/**
 * Copyright (c) 2000-2009, Jasig, Inc.
 * See license distributed with this file and available online at
 * https://www.ja-sig.org/svn/jasig-parent/tags/rel-10/license-header.txt
 */
package org.jasig.portal.spring.locator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.pluto.OptionalContainerServices;
import org.jasig.portal.spring.PortalApplicationContextLocator;
import org.springframework.context.ApplicationContext;

/**
 * @author Eric Dalquist
 * @version $Revision$
 * @deprecated code that needs an OptionalContainerServices should use direct dependency injection where possible
 */
@Deprecated
public class OptionalContainerServicesLocator extends AbstractBeanLocator<OptionalContainerServices> {
    public static final String BEAN_NAME = "optionalContainerServices";
    
    private static final Log LOG = LogFactory.getLog(OptionalContainerServicesLocator.class);
    private static AbstractBeanLocator<OptionalContainerServices> locatorInstance;

    public static OptionalContainerServices getOptionalContainerServices() {
        AbstractBeanLocator<OptionalContainerServices> locator = locatorInstance;
        if (locator == null) {
            LOG.info("Looking up bean '" + BEAN_NAME + "' in ApplicationContext due to context not yet being initialized");
            final ApplicationContext applicationContext = PortalApplicationContextLocator.getApplicationContext();
            applicationContext.getBean(OptionalContainerServicesLocator.class.getName());
            
            locator = locatorInstance;
            if (locator == null) {
                LOG.warn("Instance of '" + BEAN_NAME + "' still null after portal application context has been initialized");
                return (OptionalContainerServices)applicationContext.getBean(BEAN_NAME, OptionalContainerServices.class);
            }
        }
        
        return locator.getInstance();
    }

    public OptionalContainerServicesLocator(OptionalContainerServices instance) {
        super(instance, OptionalContainerServices.class);
    }

    /* (non-Javadoc)
     * @see org.jasig.portal.spring.locator.AbstractBeanLocator#getLocator()
     */
    @Override
    protected AbstractBeanLocator<OptionalContainerServices> getLocator() {
        return locatorInstance;
    }

    /* (non-Javadoc)
     * @see org.jasig.portal.spring.locator.AbstractBeanLocator#setLocator(org.jasig.portal.spring.locator.AbstractBeanLocator)
     */
    @Override
    protected void setLocator(AbstractBeanLocator<OptionalContainerServices> locator) {
        locatorInstance = locator;
    }
}