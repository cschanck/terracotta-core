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
package com.tc.objectserver.entity;

import com.tc.exception.ServerException;
import com.tc.objectserver.api.ResultCapture;
import com.tc.tracing.Trace;
import com.tc.util.concurrent.SetOnceFlag;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
 
public class PassiveResultCapture implements ResultCapture {
  private final Runnable received;
  private final Consumer<byte[]> result;
  private final Consumer<ServerException> error;
  private final SetOnceFlag receivedSent = new SetOnceFlag();
  private static final Logger LOGGER = LoggerFactory.getLogger(PassiveResultCapture.class);
  
  public PassiveResultCapture(Runnable received, Consumer<byte[]> result, Consumer<ServerException> error) {
    this.received = received;
    this.result = result;
    this.error = error;
  }

  public void setWaitFor(Supplier<ActivePassiveAckWaiter> waitFor) {

  }

  public void waitForReceived() {

  }

  public void received() {
    Trace.activeTrace().log("received ");
    this.receivedSent.set();
    if (received != null) {
      received.run();
    }
  }

  public void complete() {
    Trace.activeTrace().log("Completed without result ");
    if (!this.receivedSent.isSet()) {
      received();
    }
    if (result != null) {
      result.accept(null);
    }
  }  

  @Override
  public void complete(byte[] value) {
    if (Trace.isTraceEnabled()) {
      Trace.activeTrace().log("Completed with result: " + value);
    }
    if (!this.receivedSent.isSet()) {
      received();
    }
    if (result != null) {
      result.accept(value);
    }
  }

  @Override
  public void failure(ServerException ee) {
    if (Trace.isTraceEnabled()) {
      Trace.activeTrace().log("Failure - exception: " + ee.getLocalizedMessage());
    }
    if (!this.receivedSent.isSet()) {
      received();
    }
    if (error != null) {
      error.accept(ee);
    }
  }
  
  @Override
  public void message(byte[] message) {
    
  }

  @Override
  public CompletionStage<Void> retired() {
    return CompletableFuture.completedFuture(null);
  }
  
  
}
