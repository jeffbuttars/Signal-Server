/**
 * Copyright (C) 2013 Open WhisperSystems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.whispersystems.textsecuregcm.push;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.SharedMetricRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.textsecuregcm.entities.ApnMessage;
import org.whispersystems.textsecuregcm.entities.GcmMessage;
import org.whispersystems.textsecuregcm.entities.CcsmMessage;
import org.whispersystems.textsecuregcm.push.ApnFallbackManager.ApnFallbackTask;
import org.whispersystems.textsecuregcm.push.WebsocketSender.DeliveryStatus;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.Device;
import org.whispersystems.textsecuregcm.util.BlockingThreadPoolExecutor;
import org.whispersystems.textsecuregcm.util.Constants;
import org.whispersystems.textsecuregcm.util.Util;
import org.whispersystems.textsecuregcm.websocket.WebsocketAddress;

import java.util.concurrent.TimeUnit;

import static com.codahale.metrics.MetricRegistry.name;
import io.dropwizard.lifecycle.Managed;
import static org.whispersystems.textsecuregcm.entities.MessageProtos.Envelope;

public class PushSender implements Managed {

  private final Logger logger = LoggerFactory.getLogger(PushSender.class);

  private static final String APN_PAYLOAD = "{\"aps\":{\"sound\":\"default\",\"badge\":%d,\"alert\":{\"loc-key\":\"APN_Message\"}}}";

  private final ApnFallbackManager         apnFallbackManager;
  private final PushServiceClient          pushServiceClient;
  private final WebsocketSender            webSocketSender;
  private final BlockingThreadPoolExecutor executor;
  private final int                        queueSize;

  public PushSender(ApnFallbackManager apnFallbackManager, PushServiceClient pushServiceClient,
                    WebsocketSender websocketSender, int queueSize)
  {
    this.apnFallbackManager = apnFallbackManager;
    this.pushServiceClient  = pushServiceClient;
    this.webSocketSender    = websocketSender;
    this.queueSize          = queueSize;
    this.executor           = new BlockingThreadPoolExecutor(50, queueSize);

    SharedMetricRegistries.getOrCreate(Constants.METRICS_NAME)
                          .register(name(PushSender.class, "send_queue_depth"),
                                    new Gauge<Integer>() {
                                      @Override
                                      public Integer getValue() {
                                        return executor.getSize();
                                      }
                                    });
  }

  public void sendMessage(final Account account, final Device device, final Envelope message)
      throws NotPushRegisteredException
  {
      logger.debug("sendMessage account " + account);
      logger.debug("sendMessage device " + device);

    if (device.getGcmId() == null &&
            device.getApnId() == null &&
            device.getCcsmId() == null &&
            !device.getFetchesMessages()) {
      throw new NotPushRegisteredException("No delivery possible!");
    }

    if (queueSize > 0) {
      executor.execute(new Runnable() {
        @Override
        public void run() {
          sendSynchronousMessage(account, device, message);
        }
      });
    } else {
      sendSynchronousMessage(account, device, message);
    }
  }

  public void sendQueuedNotification(Account account, Device device, int messageQueueDepth, boolean fallback)
      throws NotPushRegisteredException, TransientPushFailureException
  {
    if      (device.getGcmId() != null)    sendGcmNotification(account, device);
    else if (device.getApnId() != null)    sendApnNotification(account, device, messageQueueDepth, fallback);
    else if (!device.getFetchesMessages()) throw new NotPushRegisteredException("No notification possible!");
  }

  public WebsocketSender getWebSocketSender() {
    return webSocketSender;
  }

  private void sendSynchronousMessage(Account account, Device device, Envelope message) {
    logger.debug("sendSynchronousMessage account " + account);
    logger.debug("sendSynchronousMessage device " + device);
    logger.debug("sendSynchronousMessage device.getCcsmId() " + device.getCcsmId());

    if      (device.getGcmId() != null)   sendGcmMessage(account, device, message);
    else if (device.getApnId() != null)   sendApnMessage(account, device, message);
    else if (device.getCcsmId() != null)  sendCcsmMessage(account, device, message);
    else if (device.getFetchesMessages()) sendWebSocketMessage(account, device, message);
    else                                  throw new AssertionError();
  }

  private void sendGcmMessage(Account account, Device device, Envelope message) {
    logger.debug("sendGcmMessage");
    DeliveryStatus deliveryStatus = webSocketSender.sendMessage(account, device, message, WebsocketSender.Type.GCM);

    if (!deliveryStatus.isDelivered()) {
      sendGcmNotification(account, device);
    }
  }

  private void sendGcmNotification(Account account, Device device) {
    logger.debug("sendGcmNotification");
    try {
      GcmMessage gcmMessage = new GcmMessage(device.getGcmId(), account.getNumber(),
                                             (int)device.getId(), "", false, true);

      pushServiceClient.send(gcmMessage);
    } catch (TransientPushFailureException e) {
      logger.warn("SILENT PUSH LOSS", e);
    }
  }

  private void sendCcsmMessage(Account account, Device device, Envelope message) {
    logger.debug("sendCcsmMessage");
    try {
      CcsmMessage ccsmMessage = new CcsmMessage(device.getCcsmId(), account.getNumber(),
                                             (int)device.getId(), "", false, true);

      pushServiceClient.send(ccsmMessage);
    } catch (TransientPushFailureException e) {
      logger.warn("SILENT PUSH LOSS", e);
    }
  }

  private void sendApnMessage(Account account, Device device, Envelope outgoingMessage) {
    logger.debug("sendApnMessage");
    DeliveryStatus deliveryStatus = webSocketSender.sendMessage(account, device, outgoingMessage, WebsocketSender.Type.APN);

    if (!deliveryStatus.isDelivered() && outgoingMessage.getType() != Envelope.Type.RECEIPT) {
      boolean fallback = !outgoingMessage.getSource().equals(account.getNumber());
      sendApnNotification(account, device, deliveryStatus.getMessageQueueDepth(), fallback);
    }
  }

  private void sendApnNotification(Account account, Device device, int messageQueueDepth, boolean fallback) {
    logger.debug("sendApnNotification");
    ApnMessage apnMessage;

    if (!Util.isEmpty(device.getVoipApnId())) {
      apnMessage = new ApnMessage(device.getVoipApnId(), account.getNumber(), (int)device.getId(),
                                  String.format(APN_PAYLOAD, messageQueueDepth),
                                  true, System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(30));

      if (fallback) {
        apnFallbackManager.schedule(new WebsocketAddress(account.getNumber(), device.getId()),
                                    new ApnFallbackTask(device.getApnId(), apnMessage));
      }
    } else {
      apnMessage = new ApnMessage(device.getApnId(), account.getNumber(), (int)device.getId(),
                                  String.format(APN_PAYLOAD, messageQueueDepth),
                                  false, ApnMessage.MAX_EXPIRATION);
    }

    try {
      pushServiceClient.send(apnMessage);
    } catch (TransientPushFailureException e) {
      logger.warn("SILENT PUSH LOSS", e);
    }
  }

  private void sendWebSocketMessage(Account account, Device device, Envelope outgoingMessage)
  {
    logger.debug("sendWebSocketMessage");
    webSocketSender.sendMessage(account, device, outgoingMessage, WebsocketSender.Type.WEB);
  }

  @Override
  public void start() throws Exception {

  }

  @Override
  public void stop() throws Exception {
    executor.shutdown();
    executor.awaitTermination(5, TimeUnit.MINUTES);
  }
}
