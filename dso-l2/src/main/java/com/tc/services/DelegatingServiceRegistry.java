/*
 *
 *  The contents of this file are subject to the Terracotta Public License Version
 *  2.0 (the "License"); You may not use this file except in compliance with the
 *  License. You may obtain a copy of the License at
 *
 *  http://terracotta.org/legal/terracotta-public-license.
 *
 *  Software distributed under the License is distributed on an "AS IS" basis,
 *  WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 *  the specific language governing rights and limitations under the License.
 *
 *  The Covered Software is Terracotta Core.
 *
 *  The Initial Developer of the Covered Software is
 *  Terracotta, Inc., a Software AG company
 *
 */
package com.tc.services;

import com.google.common.collect.ImmutableMap;
import com.tc.objectserver.api.ManagedEntity;
import com.tc.util.Assert;

import org.terracotta.entity.ServiceConfiguration;
import org.terracotta.entity.ServiceProvider;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


public class DelegatingServiceRegistry implements InternalServiceRegistry {
  private final long consumerID;
  private final Map<Class<?>, List<ServiceProvider>> serviceProviderMap;
  private final Map<Class<?>, List<BuiltInServiceProvider>> builtInServiceProviderMap;
  // Both the registry and the entity refer to each other so this is late-bound.
  private ManagedEntity owningEntity;

  public DelegatingServiceRegistry(long consumerID, ServiceProvider[] providers, BuiltInServiceProvider[] builtInProviders) {
    this.consumerID = consumerID;
    
    Map<Class<?>, List<ServiceProvider>> tempProviders = new HashMap<>();
    for(ServiceProvider provider : providers) {
      for (Class<?> serviceType : provider.getProvidedServiceTypes()) {
        List<ServiceProvider> listForType = tempProviders.get(serviceType);
        if (null == listForType) {
          listForType = new LinkedList<>();
          tempProviders.put(serviceType, listForType);
        }
        listForType.add(provider);
      }
    }
    serviceProviderMap = ImmutableMap.copyOf(tempProviders);
    
    Map<Class<?>, List<BuiltInServiceProvider>> tempBuiltInProviders = new HashMap<>();
    for(BuiltInServiceProvider provider : builtInProviders) {
      for (Class<?> serviceType : provider.getProvidedServiceTypes()) {
        List<BuiltInServiceProvider> listForType = tempBuiltInProviders.get(serviceType);
        if (null == listForType) {
          listForType = new LinkedList<>();
          tempBuiltInProviders.put(serviceType, listForType);
        }
        listForType.add(provider);
      }
    }
    builtInServiceProviderMap = ImmutableMap.copyOf(tempBuiltInProviders);
  }

  @Override
  public <T> T getService(ServiceConfiguration<T> configuration) {
    // It is possible that there is no owning entity (if this is a synthetic consumer) but that means no access to
    // built-in services.
    T builtInService = (null != this.owningEntity) ? getBuiltInService(configuration) : null;
    T externalService = getExternalService(configuration);
    // TODO:  Determine how to rationalize multiple matches.  For now, we will force either 1 or 0.
    Assert.assertFalse((null != builtInService) && (null != externalService));
    return (null != builtInService)
        ? builtInService
        : externalService;
  }

  public void setOwningEntity(ManagedEntity entity) {
    Assert.assertNull(this.owningEntity);
    Assert.assertNotNull(entity);
    this.owningEntity = entity;
  }

  private <T> T getBuiltInService(ServiceConfiguration<T> configuration) {
    List<BuiltInServiceProvider> serviceProviders = builtInServiceProviderMap.get(configuration.getServiceType());
    T service = null;
    if (null != serviceProviders) {
      for (BuiltInServiceProvider provider : serviceProviders) {
        T oneService = provider.getService(this.consumerID, this.owningEntity, configuration);
        if (null != oneService) {
          // TODO:  Determine how to rationalize multiple matches.  For now, we will force either 1 or 0.
          Assert.assertNull(service);
          service = oneService;
        }
      }
    }
    return service;
  }

  private <T> T getExternalService(ServiceConfiguration<T> configuration) {
    List<ServiceProvider> serviceProviders = serviceProviderMap.get(configuration.getServiceType());
    if (serviceProviders == null) {
     return null;
    }
    T service = null;
    for (ServiceProvider provider : serviceProviders) {
      T oneService = provider.getService(this.consumerID, configuration);
      if (null != oneService) {
        // TODO:  Determine how to rationalize multiple matches.  For now, we will force either 1 or 0.
        Assert.assertNull(service);
        service = oneService;
      }
    }
    return service;
  }
}
