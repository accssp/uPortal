/**
 * Licensed to Jasig under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Jasig licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jasig.portal.portlet.container.cache;

import java.io.Serializable;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import javax.portlet.CacheControl;
import javax.portlet.MimeResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.config.CacheConfiguration;

import org.apache.commons.lang.StringUtils;
import org.apache.pluto.container.om.portlet.PortletDefinition;
import org.jasig.portal.portlet.container.CacheControlImpl;
import org.jasig.portal.portlet.om.IPortletDefinitionId;
import org.jasig.portal.portlet.om.IPortletEntity;
import org.jasig.portal.portlet.om.IPortletEntityId;
import org.jasig.portal.portlet.om.IPortletWindow;
import org.jasig.portal.portlet.om.IPortletWindowId;
import org.jasig.portal.portlet.registry.IPortletDefinitionRegistry;
import org.jasig.portal.portlet.registry.IPortletEntityRegistry;
import org.jasig.portal.portlet.registry.IPortletWindowRegistry;
import org.jasig.portal.url.IPortalRequestInfo;
import org.jasig.portal.url.IPortletRequestInfo;
import org.jasig.portal.url.IUrlSyntaxProvider;
import org.jasig.portal.utils.web.PortalWebUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.security.web.session.HttpSessionCreatedEvent;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.RequestContextUtils;


/**
 * Default implementation of {@link IPortletCacheControlService}.
 * {@link CacheControl}s are stored in a {@link Map} stored as a {@link HttpServletRequest} attribute.
 * 
 * @author Nicholas Blair
 * @version $Id$
 */
@Service
public class PortletCacheControlServiceImpl implements IPortletCacheControlService, ApplicationListener<ApplicationEvent> {
    private static final String IF_NONE_MATCH = "If-None-Match";
    private static final String IF_MODIFIED_SINCE = "If-Modified-Since";
    
	private static final String SESSION_ATTRIBUTE__PORTLET_RENDER_HEADER_CACHE_KEYS_MAP = PortletCacheControlServiceImpl.class.getName() + ".PORTLET_RENDER_HEADER_CACHE_KEYS_MAP";
	private static final String SESSION_ATTRIBUTE__PORTLET_RENDER_CACHE_KEYS_MAP = PortletCacheControlServiceImpl.class.getName() + ".PORTLET_RENDER_CACHE_KEYS_MAP";
	private static final String SESSION_ATTRIBUTE__PORTLET_RESOURCE_CACHE_KEYS_MAP = PortletCacheControlServiceImpl.class.getName() + ".PORTLET_RESOURCE_CACHE_KEYS_MAP";
	
	protected final Logger logger = LoggerFactory.getLogger(this.getClass());

	//Used to coordinate mass-purge of cached data for portlets at action/event requests
	private final PublicPortletCacheKeyTracker publicPortletRenderHeaderCacheKeyTracker = new PublicPortletCacheKeyTracker();
    private final PublicPortletCacheKeyTracker publicPortletRenderCacheKeyTracker = new PublicPortletCacheKeyTracker();
	private final PublicPortletCacheKeyTracker publicPortletResourceCacheKeyTracker = new PublicPortletCacheKeyTracker();
	private final PrivatePortletCacheKeyTracker privatePortletRenderHeaderCacheKeyTracker = new PrivatePortletCacheKeyTracker(SESSION_ATTRIBUTE__PORTLET_RENDER_HEADER_CACHE_KEYS_MAP);
    private final PrivatePortletCacheKeyTracker privatePortletRenderCacheKeyTracker = new PrivatePortletCacheKeyTracker(SESSION_ATTRIBUTE__PORTLET_RENDER_CACHE_KEYS_MAP);
	private final PrivatePortletCacheKeyTracker privatePortletResourceCacheKeyTracker = new PrivatePortletCacheKeyTracker(SESSION_ATTRIBUTE__PORTLET_RESOURCE_CACHE_KEYS_MAP);
	
	private IPortletWindowRegistry portletWindowRegistry;
	private IPortletEntityRegistry portletEntityRegistry;
	private IPortletDefinitionRegistry portletDefinitionRegistry;
	private IUrlSyntaxProvider urlSyntaxProvider;
	
    private Ehcache privateScopePortletRenderHeaderOutputCache;
    private Ehcache publicScopePortletRenderHeaderOutputCache;
    
    private Ehcache privateScopePortletRenderOutputCache;
    private Ehcache publicScopePortletRenderOutputCache;
    
    private Ehcache privateScopePortletResourceOutputCache;
    private Ehcache publicScopePortletResourceOutputCache;
    
    // default to 100 KB
    private int cacheSizeThreshold = 102400;
    
    
    @Autowired
    @Qualifier("org.jasig.portal.portlet.container.cache.PortletCacheControlServiceImpl.privateScopePortletRenderHeaderOutputCache")
    public void setPrivateScopePortletRenderHeaderOutputCache(Ehcache privateScopePortletRenderHeaderOutputCache) {
        this.privateScopePortletRenderHeaderOutputCache = privateScopePortletRenderHeaderOutputCache;
        this.privateScopePortletRenderHeaderOutputCache.getCacheEventNotificationService()
                .registerListener(privatePortletRenderHeaderCacheKeyTracker);
    }

    @Autowired
    @Qualifier("org.jasig.portal.portlet.container.cache.PortletCacheControlServiceImpl.publicScopePortletRenderHeaderOutputCache")
    public void setPublicScopePortletRenderHeaderOutputCache(Ehcache publicScopePortletRenderHeaderOutputCache) {
        this.publicScopePortletRenderHeaderOutputCache = publicScopePortletRenderHeaderOutputCache;
        this.publicScopePortletRenderHeaderOutputCache.getCacheEventNotificationService()
                .registerListener(publicPortletRenderHeaderCacheKeyTracker);
    }
    
    
    @Autowired
    @Qualifier("org.jasig.portal.portlet.container.cache.PortletCacheControlServiceImpl.privateScopePortletRenderOutputCache")
    public void setPrivateScopePortletRenderOutputCache(Ehcache privateScopePortletRenderOutputCache) {
        this.privateScopePortletRenderOutputCache = privateScopePortletRenderOutputCache;
        this.privateScopePortletRenderOutputCache.getCacheEventNotificationService()
                .registerListener(privatePortletRenderCacheKeyTracker);
    }

    @Autowired
    @Qualifier("org.jasig.portal.portlet.container.cache.PortletCacheControlServiceImpl.publicScopePortletRenderOutputCache")
    public void setPublicScopePortletRenderOutputCache(Ehcache publicScopePortletRenderOutputCache) {
        this.publicScopePortletRenderOutputCache = publicScopePortletRenderOutputCache;
        this.publicScopePortletRenderOutputCache.getCacheEventNotificationService()
                .registerListener(publicPortletRenderCacheKeyTracker);
    }

    @Autowired
    @Qualifier("org.jasig.portal.portlet.container.cache.PortletCacheControlServiceImpl.privateScopePortletResourceOutputCache")
    public void setPrivateScopePortletResourceOutputCache(Ehcache privateScopePortletResourceOutputCache) {
        this.privateScopePortletResourceOutputCache = privateScopePortletResourceOutputCache;
        this.privateScopePortletResourceOutputCache.getCacheEventNotificationService()
                .registerListener(privatePortletResourceCacheKeyTracker);
    }

    @Autowired
    @Qualifier("org.jasig.portal.portlet.container.cache.PortletCacheControlServiceImpl.publicScopePortletResourceOutputCache")
    public void setPublicScopePortletResourceOutputCache(Ehcache publicScopePortletResourceOutputCache) {
        this.publicScopePortletResourceOutputCache = publicScopePortletResourceOutputCache;
        this.publicScopePortletResourceOutputCache.getCacheEventNotificationService()
                .registerListener(publicPortletResourceCacheKeyTracker);
    }
    
	/**
	 * @param cacheSizeThreshold the cacheSizeThreshold to set in bytes
	 */
    @Value("${org.jasig.portal.portlet.container.cache.PortletCacheControlServiceImpl.cacheSizeThreshold:102400}")
	public void setCacheSizeThreshold(int cacheSizeThreshold) {
		this.cacheSizeThreshold = cacheSizeThreshold;
	}
    
	@Override
	public int getCacheSizeThreshold() {
		return cacheSizeThreshold;
	}
	@Autowired
	public void setPortletWindowRegistry(
			IPortletWindowRegistry portletWindowRegistry) {
		this.portletWindowRegistry = portletWindowRegistry;
	}
	@Autowired
	public void setPortletEntityRegistry(
			IPortletEntityRegistry portletEntityRegistry) {
		this.portletEntityRegistry = portletEntityRegistry;
	}
	@Autowired
	public void setPortletDefinitionRegistry(
			IPortletDefinitionRegistry portletDefinitionRegistry) {
		this.portletDefinitionRegistry = portletDefinitionRegistry;
	}
	@Autowired
	public void setUrlSyntaxProvider(IUrlSyntaxProvider urlSyntaxProvider) {
        this.urlSyntaxProvider = urlSyntaxProvider;
    }
	
    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof HttpSessionCreatedEvent) {
            final HttpSession session = ((HttpSessionCreatedEvent) event).getSession();
            this.privatePortletRenderHeaderCacheKeyTracker.initPrivateKeyCache(session);
            this.privatePortletRenderCacheKeyTracker.initPrivateKeyCache(session);
            this.privatePortletResourceCacheKeyTracker.initPrivateKeyCache(session);
        }
        else if (event instanceof HttpSessionDestroyedEvent) {
            final HttpSession session = ((HttpSessionDestroyedEvent) event).getSession();
            this.privatePortletRenderHeaderCacheKeyTracker.destroyPrivateKeyCache(session);
            this.privatePortletRenderCacheKeyTracker.destroyPrivateKeyCache(session);
            this.privatePortletResourceCacheKeyTracker.destroyPrivateKeyCache(session);
        }
    }
    
    @Override
    public CacheState getPortletRenderHeaderState(HttpServletRequest request, IPortletWindowId portletWindowId) {
        final IPortletWindow portletWindow = this.portletWindowRegistry.getPortletWindow(request, portletWindowId);
        if (portletWindow == null) {
            logger.warn("portletWindowRegistry returned null for {}, returning default cacheControl and no cached portlet data", portletWindowId);
            return new CacheState(new CacheControlImpl(), null, false, false);
        }
        
        //Generate the public render-header cache key
        final IPortletEntity entity = portletWindow.getPortletEntity();
        final IPortletDefinitionId definitionId = entity.getPortletDefinitionId();
        final Locale locale = RequestContextUtils.getLocale(request);
        final PublicPortletCacheKey publicCacheKey = PublicPortletCacheKey.createPublicPortletRenderHeaderCacheKey(definitionId, portletWindow, locale);
        
        return this.getPortletState(request, portletWindow, publicCacheKey, false);
    }
    
    @Override
    public CacheState getPortletRenderState(HttpServletRequest request, IPortletWindowId portletWindowId) {
        final IPortletWindow portletWindow = this.portletWindowRegistry.getPortletWindow(request, portletWindowId);
        if (portletWindow == null) {
            logger.warn("portletWindowRegistry returned null for {}, returning default cacheControl and no cached portlet data", portletWindowId);
            return new CacheState(new CacheControlImpl(), null, false, false);
        }
        
        //Generate the public render cache key
        final IPortletEntity entity = portletWindow.getPortletEntity();
        final IPortletDefinitionId definitionId = entity.getPortletDefinitionId();
        final Locale locale = RequestContextUtils.getLocale(request);
        final PublicPortletCacheKey publicCacheKey = PublicPortletCacheKey.createPublicPortletRenderCacheKey(definitionId, portletWindow, locale);
        
        return this.getPortletState(request, portletWindow, publicCacheKey, false);
    }
    
    @Override
    public CacheState getPortletResourceState(HttpServletRequest request, IPortletWindowId portletWindowId) {
        final IPortletWindow portletWindow = this.portletWindowRegistry.getPortletWindow(request, portletWindowId);
        if (portletWindow == null) {
            logger.warn("portletWindowRegistry returned null for {}, returning default cacheControl and no cached portlet data", portletWindowId);
            return new CacheState(new CacheControlImpl(), null, false, false);
        }
        
        //Generate the public resource cache key
        final IPortletEntity entity = portletWindow.getPortletEntity();
        final IPortletDefinitionId definitionId = entity.getPortletDefinitionId();
        final String resourceId = getResourceId(portletWindowId, request);
        final Locale locale = RequestContextUtils.getLocale(request);
        final PublicPortletCacheKey publicCacheKey = PublicPortletCacheKey.createPublicPortletResourceCacheKey(definitionId, portletWindow, resourceId, locale);
        
        return this.getPortletState(request, portletWindow, publicCacheKey, true);
    }
    
    protected CacheState getPortletState(HttpServletRequest request,
            IPortletWindow portletWindow, PublicPortletCacheKey publicCacheKey, boolean useHttpHeaders) {
        
        //See if there is any cached data for the portlet header request
        final CachedPortletData cachedPortletData = this.getCachedPortletData(request, portletWindow, publicCacheKey, this.publicScopePortletRenderOutputCache, this.privateScopePortletRenderOutputCache);
        
        if (cachedPortletData != null) {
            //Cached data exists, see if it can be used with no additional work
            
            if (!cachedPortletData.isExpired()) {
                //Cached data is not expired, check if browser data should be used
                
                if (useHttpHeaders) {
                    //Browser headers being used, check ETag and Last Modified
                    
                    final String etagHeader = request.getHeader(IF_NONE_MATCH);
                    if (etagHeader != null && etagHeader.equals(cachedPortletData.getEtag())) {
                        //ETag is valid, return the cached content and note that the browser data can be used
                        return new CacheState(null, cachedPortletData, true, true);
                    }
                    
                    long ifModifiedSince = request.getDateHeader(IF_MODIFIED_SINCE);
                    if (cachedPortletData.getTimeStored() <= ifModifiedSince) {
                        //Cached content hasn't been modified since header date, return the cached content and note that the browser data can be used
                        return new CacheState(null, cachedPortletData, true, true);
                    }
                }
            
                //No browser side data to be used, return the cached data for replay
                return new CacheState(null, cachedPortletData, true, false);
            }
        }
        
        //Build CacheControl structure
        final CacheControlImpl cacheControl = new CacheControlImpl();
        
        //Get the portlet descriptor
        final IPortletEntity entity = portletWindow.getPortletEntity();
        final IPortletDefinitionId definitionId = entity.getPortletDefinitionId();
        final PortletDefinition portletDescriptor = this.portletDefinitionRegistry.getParentPortletDescriptor(definitionId);
        
        //Set the default scope
        final String cacheScopeValue = portletDescriptor.getCacheScope();
        if (MimeResponse.PUBLIC_SCOPE.equalsIgnoreCase(cacheScopeValue)) {
            cacheControl.setPublicScope(true);
        }
        
        //Set the default expiration time
        cacheControl.setExpirationTime(portletDescriptor.getExpirationCache());
        
        // If there is cached data copy the etag
        if (cachedPortletData != null) {
            cacheControl.setETag(cachedPortletData.getEtag());
        }
        // If no cached data fall back on the request Etag 
        else if (useHttpHeaders) {
            final String etagHeader = request.getHeader(IF_NONE_MATCH);
            cacheControl.setETag(etagHeader);
        }
        
        return new CacheState(cacheControl, cachedPortletData, false, false);
    }
    
    /**
     * Get the cached portlet data looking in both the public and then private caches returning the first found
     * 
     * @param request The current request
     * @param portletWindow The window to get data for
     * @param publicCacheKey The public cache key
     * @param publicOutputCache The public cache
     * @param privateOutputCache The private cache
     */
    protected CachedPortletData getCachedPortletData(HttpServletRequest request, IPortletWindow portletWindow, 
            PublicPortletCacheKey publicCacheKey, Ehcache publicOutputCache, Ehcache privateOutputCache) {
        
        final IPortletWindowId portletWindowId = portletWindow.getPortletWindowId();

        //Check for publicly cached data
        CachedPortletData cachedPortletData = this.getCachedPortletData(publicCacheKey, publicOutputCache, portletWindow);
        if (cachedPortletData != null) {
            return cachedPortletData;
        }
        
        //Check for privately cached data
        final HttpSession session = request.getSession();
        final String sessionId = session.getId();
        final IPortletEntityId entityId = portletWindow.getPortletEntityId();
        final PrivatePortletCacheKey privateCacheKey = new PrivatePortletCacheKey(sessionId, portletWindowId, entityId, publicCacheKey);
        cachedPortletData = this.getCachedPortletData(privateCacheKey, privateOutputCache, portletWindow);
        if (cachedPortletData != null) {
            return cachedPortletData;
        }

        logger.debug("No cached output exists for portlet window {}", portletWindow);
        return null;
    }

    /**
     * Get the cached portlet data for the key, cache and window. If there is {@link CachedPortletData}
     * in the cache it will only be returned if {@link CachedPortletData#isExpired()} is false or
     * {@link CachedPortletData#getEtag()} is not null.
     * 
     * @param cacheKey The cache key
     * @param outputCache The cache
     * @param portletWindow The portlet window the lookup is for
     * @return The cache data for the portlet window
     */
    protected CachedPortletData getCachedPortletData(Serializable cacheKey, Ehcache outputCache,
            IPortletWindow portletWindow) {

        final Element publicCacheElement = outputCache.get(cacheKey);
        if (publicCacheElement == null) {
            logger.debug("No cached output for key {}", cacheKey);
            return null;
        }

        final CachedPortletData cachedPortletData = (CachedPortletData) publicCacheElement.getValue();
        if (publicCacheElement.isExpired() && cachedPortletData.getEtag() == null) {
            logger.debug("Cached output for key {} is expired", cacheKey);
            outputCache.remove(cacheKey);
            return null;
        }

        logger.debug("Returning cached output with key {} for {}", cacheKey, portletWindow);
        return (CachedPortletData) publicCacheElement.getValue();
    }    
	
    /**
     * Get the resourceId for the portlet request
     */
    protected String getResourceId(IPortletWindowId portletWindowId, HttpServletRequest httpRequest) {
        final IPortalRequestInfo portalRequestInfo = this.urlSyntaxProvider.getPortalRequestInfo(httpRequest);
        final Map<IPortletWindowId, ? extends IPortletRequestInfo> portletRequestInfoMap = portalRequestInfo.getPortletRequestInfoMap();
        final IPortletRequestInfo portletRequestInfo = portletRequestInfoMap.get(portletWindowId);
        return portletRequestInfo != null ? portletRequestInfo.getResourceId() : null;
    }
    
    
    
    
    

    @Override
	public boolean shouldOutputBeCached(CacheControl cacheControl) {
		if(cacheControl.getExpirationTime() != 0) {
			return true;
		} else {
			return false;
		}
	}
	
	@Override
	public void cachePortletRenderOutput(IPortletWindowId portletWindowId,
			HttpServletRequest httpRequest, String content,
			CacheControl cacheControl) {
		final IPortletWindow portletWindow = this.portletWindowRegistry.getPortletWindow(httpRequest, portletWindowId);
        
        final IPortletEntityId entityId = portletWindow.getPortletEntityId();
        final IPortletEntity entity = this.portletEntityRegistry.getPortletEntity(httpRequest, entityId);
        final IPortletDefinitionId definitionId = entity.getPortletDefinitionId();	
		
        final int expirationTime = cacheControl.getExpirationTime();
		CachedPortletData newData = new CachedPortletData();
		newData.setExpirationTimeSeconds(expirationTime);
		newData.setTimeStored(System.currentTimeMillis());
		newData.setStringData(content);
		newData.setEtag(cacheControl.getETag());
		
		final Locale locale = RequestContextUtils.getLocale(httpRequest);
        final PublicPortletCacheKey publicCacheKey = new PublicPortletCacheKey(definitionId, portletWindow, locale);
		if(cacheControl.isPublicScope()) {
			newData.setCacheConfigurationMaxTTL((int)publicScopePortletRenderOutputCache.getCacheConfiguration().getTimeToLiveSeconds());
			Element publicCacheElement = constructCacheElement(publicCacheKey, newData, publicScopePortletRenderOutputCache.getCacheConfiguration(), cacheControl);
			this.publicScopePortletRenderOutputCache.put(publicCacheElement);
			
			logger.debug("Cached public render data under key {} for {}", publicCacheKey, portletWindow);
		} else {
		    final HttpSession session = httpRequest.getSession();
			newData.setCacheConfigurationMaxTTL((int)privateScopePortletRenderOutputCache.getCacheConfiguration().getTimeToLiveSeconds());
            final PrivatePortletCacheKey privateCacheKey = new PrivatePortletCacheKey(session.getId(), portletWindowId, entityId, publicCacheKey);
			Element privateCacheElement = constructCacheElement(privateCacheKey, newData, privateScopePortletRenderOutputCache.getCacheConfiguration(), cacheControl);
			this.privateScopePortletRenderOutputCache.put(privateCacheElement);
			
			logger.debug("Cached private render data under key {} for {}", privateCacheKey, portletWindow);
		}
	}

	@Override
    public void cachePortletResourceOutput(IPortletWindowId portletWindowId, HttpServletRequest httpRequest,
            CachedPortletData cachedPortletData, CacheControl cacheControl) {
	    
		final IPortletWindow portletWindow = this.portletWindowRegistry.getPortletWindow(httpRequest, portletWindowId);
        
        final IPortletEntityId entityId = portletWindow.getPortletEntityId();
        final IPortletEntity entity = this.portletEntityRegistry.getPortletEntity(httpRequest, entityId);
        final IPortletDefinitionId definitionId = entity.getPortletDefinitionId();	
		
        final int expirationTime = cacheControl.getExpirationTime();
        cachedPortletData.setEtag(cacheControl.getETag());
        cachedPortletData.setExpirationTimeSeconds(expirationTime);
        cachedPortletData.setTimeStored(System.currentTimeMillis());
        
        final String resourceId = getResourceId(portletWindowId, httpRequest);
        
        //TODO look at cacheability of the resource req/res
        
        final Locale locale = RequestContextUtils.getLocale(httpRequest);
        final PublicPortletCacheKey publicCacheKey = new PublicPortletCacheKey(definitionId, portletWindow, resourceId, locale);
		
		if(cacheControl.isPublicScope()) {
		    cachedPortletData.setCacheConfigurationMaxTTL((int)publicScopePortletResourceOutputCache.getCacheConfiguration().getTimeToLiveSeconds());
			Element publicCacheElement = constructCacheElement(publicCacheKey, cachedPortletData, publicScopePortletResourceOutputCache.getCacheConfiguration(), cacheControl);
			this.publicScopePortletResourceOutputCache.put(publicCacheElement);
			
			logger.debug("Cached public resource data under key {} for {}", publicCacheKey, portletWindow);
		} else {
		    final HttpSession session = httpRequest.getSession();
		    cachedPortletData.setCacheConfigurationMaxTTL((int)privateScopePortletResourceOutputCache.getCacheConfiguration().getTimeToLiveSeconds());
            final PrivatePortletCacheKey privateCacheKey = new PrivatePortletCacheKey(session.getId(), portletWindowId, entityId, publicCacheKey);
			Element privateCacheElement = constructCacheElement(privateCacheKey, cachedPortletData, privateScopePortletResourceOutputCache.getCacheConfiguration(), cacheControl);
			this.privateScopePortletResourceOutputCache.put(privateCacheElement);
			
			logger.debug("Cached private resource data under key {} for {}", privateCacheKey, portletWindow);
		}
	}
	
	/**
	 * Construct an appropriate Cache {@link Element} for the cacheKey and data.
	 * The element's ttl will be set depending on whether expiration or validation method is indicated from the CacheControl and the cache's configuration.
	 * 
	 * @param cacheKey
	 * @param data
	 * @param cacheConfig
	 * @param cacheControl
	 * @return
	 */
	protected Element constructCacheElement(Serializable cacheKey, CachedPortletData data, CacheConfiguration cacheConfig, CacheControl cacheControl) {
		// if validation method is being triggered, ignore expirationTime and defer to cache configuration
		if(StringUtils.isNotBlank(cacheControl.getETag())) {
			return new Element(cacheKey, data);
		}
		
		Integer cacheControlTTL = cacheControl.getExpirationTime();
		if(cacheControlTTL < 0) {
			// using expiration method, negative value for CacheControl#expirationTime means "forever" (e.g. ignore and defer to cache configuration)
			return new Element(cacheKey, data);
		}
		Long cacheConfigTTL = cacheConfig.getTimeToLiveSeconds();
		Long min = Math.min(cacheConfigTTL, cacheControlTTL.longValue());
		
		return new Element(cacheKey, data, null, null, min.intValue());
	}
	
	@Override
	public boolean purgeCachedPortletData(IPortletWindowId portletWindowId,
			HttpServletRequest httpRequest) {
		final IPortletWindow portletWindow = this.portletWindowRegistry.getPortletWindow(httpRequest, portletWindowId);
        final IPortletEntityId entityId = portletWindow.getPortletEntityId();
        final IPortletEntity entity = this.portletEntityRegistry.getPortletEntity(httpRequest, entityId);
        final IPortletDefinitionId definitionId = entity.getPortletDefinitionId();
        
        logger.debug("Purging all cached data for {}", portletWindow);
        
        boolean removed = false;

        //Remove all publicly cached render data for the portlet
        final Set<PublicPortletCacheKey> publicRenderKeys = this.publicPortletRenderCacheKeyTracker.getCacheKeys(definitionId);
        removed = removed || !publicRenderKeys.isEmpty();
        this.publicScopePortletRenderOutputCache.removeAll(publicRenderKeys);
        
        //Remove all publicly cached resource data for the portlet
        final Set<PublicPortletCacheKey> publicResourceKeys = this.publicPortletResourceCacheKeyTracker.getCacheKeys(definitionId);
        removed = removed || !publicResourceKeys.isEmpty();
        this.publicScopePortletResourceOutputCache.removeAll(publicResourceKeys);
        
        final HttpSession session = httpRequest.getSession();
        
        //Remove all privately cached render data
        final Set<PrivatePortletCacheKey> privateRenderKeys = this.privatePortletRenderCacheKeyTracker.getCacheKeys(session, portletWindowId);
        removed = removed || !privateRenderKeys.isEmpty();
        this.privateScopePortletRenderOutputCache.removeAll(privateRenderKeys);
        
        //Remove all privately cached render data
        final Set<PrivatePortletCacheKey> privateResourceKeys = this.privatePortletResourceCacheKeyTracker.getCacheKeys(session, portletWindowId);
        removed = removed || !privateResourceKeys.isEmpty();
        this.privateScopePortletResourceOutputCache.removeAll(privateResourceKeys);

        //If any keys were found remove them
        return removed;
	}
}
