package com.terracotta.connection.entity;

import org.terracotta.connection.entity.Entity;
import org.terracotta.connection.entity.EntityRef;
import org.terracotta.entity.EntityClientEndpoint;
import org.terracotta.entity.EntityClientService;

import com.tc.logging.TCLogger;
import com.tc.logging.TCLogging;
import com.tc.object.ClientEntityManager;
import com.tc.object.ClientInstanceID;
import com.tc.object.EntityDescriptor;
import com.tc.object.EntityID;
import com.tc.util.Util;


public class TerracottaEntityRef<T extends Entity> implements EntityRef<T> {
  private final TCLogger logger = TCLogging.getLogger(TerracottaEntityRef.class);
  private final ClientEntityManager entityManager;
  private final MaintenanceModeService maintenanceModeService;
  private final Class<T> type;
  private final long version;
  private final String name;
  private final EntityClientService<T, ?> entityClientService;

  // Each instance fetched by this ref can be individually addressed by the server so it needs a unique ID.
  private long nextClientInstanceID = 1;

  public TerracottaEntityRef(ClientEntityManager entityManager, MaintenanceModeService maintenanceModeService,
                             Class<T> type, long version, String name, EntityClientService<T, ?> entityClientService) {
    this.entityManager = entityManager;
    this.maintenanceModeService = maintenanceModeService;
    this.type = type;
    this.version = version;
    this.name = name;
    this.entityClientService = entityClientService;
  }

  @SuppressWarnings("resource")
  @Override
  public synchronized T fetchEntity() {
    maintenanceModeService.readLockEntity(type, name);
    // We need to pass the corresponding unlock into the lookupEntity.  It is responsible for calling the hook
    //  if it fails to look up the entity OR delegating that responsibility to the end-point it found for it to
    //  call when it is closed.
    Runnable closeHook = () -> {
      maintenanceModeService.readUnlockEntity(type, name);
    };
    
    EntityClientEndpoint endpoint = null;
    try {
      ClientInstanceID clientInstanceID = new ClientInstanceID(this.nextClientInstanceID);
      this.nextClientInstanceID += 1;
      EntityDescriptor entityDescriptor = new EntityDescriptor(getEntityID(), clientInstanceID, this.version);
      endpoint = this.entityManager.fetchEntity(entityDescriptor, closeHook);
    } catch (final Throwable t) {
      closeHook.run();
      Util.printLogAndRethrowError(t, logger);
    }
    
    if (null == endpoint) {
      throw new IllegalStateException("doesn't exist");
    }
    return entityClientService.create(endpoint);
  }

  @Override
  public String getName() {
    return name;
  }

  private EntityID getEntityID() {
    return new EntityID(type.getName(), name);
  }
}
