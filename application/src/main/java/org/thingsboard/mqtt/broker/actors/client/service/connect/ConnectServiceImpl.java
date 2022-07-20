/**
 * Copyright © 2016-2022 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.mqtt.broker.actors.client.service.connect;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.netty.handler.codec.mqtt.MqttConnAckMessage;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thingsboard.mqtt.broker.actors.TbActorRef;
import org.thingsboard.mqtt.broker.actors.client.messages.ConnectionAcceptedMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.DisconnectMsg;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttConnectMsg;
import org.thingsboard.mqtt.broker.actors.client.service.MqttMessageHandlerImpl;
import org.thingsboard.mqtt.broker.actors.client.state.ClientActorStateInfo;
import org.thingsboard.mqtt.broker.cluster.ServiceInfoProvider;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.data.ClientType;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.exception.MqttException;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventService;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ConnectionResponse;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCtxService;
import org.thingsboard.mqtt.broker.service.mqtt.keepalive.KeepAliveService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.MsgPersistenceManager;
import org.thingsboard.mqtt.broker.service.mqtt.will.LastWillService;
import org.thingsboard.mqtt.broker.session.ClientMqttActorManager;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;
import org.thingsboard.mqtt.broker.util.ClientSessionInfoFactory;

import javax.annotation.PreDestroy;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_ACCEPTED;
import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConnectServiceImpl implements ConnectService {
    private final ExecutorService connectHandlerExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2,
            ThingsBoardThreadFactory.forName("connect-handler-executor"));

    private final ClientMqttActorManager clientMqttActorManager;
    private final MqttMessageGenerator mqttMessageGenerator;
    private final ClientSessionEventService clientSessionEventService;
    private final KeepAliveService keepAliveService;
    private final ServiceInfoProvider serviceInfoProvider;
    private final LastWillService lastWillService;
    private final ClientSessionCtxService clientSessionCtxService;
    private final MsgPersistenceManager msgPersistenceManager;
    private final MqttMessageHandlerImpl messageHandler;

    @Override
    public void startConnection(ClientActorStateInfo actorState, MqttConnectMsg msg) throws MqttException {
        UUID sessionId = actorState.getCurrentSessionId();
        ClientSessionCtx sessionCtx = actorState.getCurrentSessionCtx();
        String clientId = actorState.getClientId();

        log.trace("[{}][{}] Processing connect msg.", clientId, sessionId);

        validate(sessionCtx, msg);

        sessionCtx.setSessionInfo(getSessionInfo(msg, sessionId, clientId, sessionCtx.getClientType()));

        keepAliveService.registerSession(clientId, sessionId, msg.getKeepAliveTimeSeconds());

        ListenableFuture<ConnectionResponse> connectFuture = clientSessionEventService.requestConnection(sessionCtx.getSessionInfo());
        Futures.addCallback(connectFuture, new FutureCallback<>() {
            @Override
            public void onSuccess(ConnectionResponse connectionResponse) {
                if (connectionResponse.isSuccess()) {
                    notifyConnectionAccepted(connectionResponse);
                } else {
                    refuseConnection(sessionCtx, null);
                }
            }

            private void notifyConnectionAccepted(ConnectionResponse connectionResponse) {
                clientMqttActorManager.notifyConnectionAccepted(clientId, new ConnectionAcceptedMsg(
                        sessionId, connectionResponse.isPrevSessionPersistent(), msg.getLastWillMsg()));
            }

            @Override
            public void onFailure(Throwable t) {
                refuseConnection(sessionCtx, t);
            }
        }, connectHandlerExecutor);
    }

    @Override
    public void acceptConnection(ClientActorStateInfo actorState, ConnectionAcceptedMsg connectionAcceptedMsg, TbActorRef actorRef) {
        ClientSessionCtx sessionCtx = actorState.getCurrentSessionCtx();
        SessionInfo sessionInfo = sessionCtx.getSessionInfo();

        if (connectionAcceptedMsg.getLastWillMsg() != null) {
            lastWillService.saveLastWillMsg(sessionInfo, connectionAcceptedMsg.getLastWillMsg());
        }

        pushConnAckMsg(sessionCtx, connectionAcceptedMsg);

        log.debug("[{}] [{}] Client connected!", actorState.getClientId(), actorState.getCurrentSessionId());

        clientSessionCtxService.registerSession(sessionCtx);

        if (sessionCtx.getSessionInfo().isPersistent()) {
            msgPersistenceManager.startProcessingPersistedMessages(actorState, connectionAcceptedMsg.isPrevSessionPersistent());
        }

        actorState.getQueuedMessages().process(msg -> messageHandler.process(sessionCtx, msg, actorRef));
    }

    private void pushConnAckMsg(ClientSessionCtx sessionCtx, ConnectionAcceptedMsg connectionAcceptedMsg) {
        boolean sessionPresent = connectionAcceptedMsg.isPrevSessionPersistent() && sessionCtx.getSessionInfo().isPersistent();
        sessionCtx.getChannel().writeAndFlush(createMqttConnAckMsg(CONNECTION_ACCEPTED, sessionPresent));
    }

    void refuseConnection(ClientSessionCtx clientSessionCtx, Throwable t) {
        logConnectionRefused(t, clientSessionCtx);

        sendConnectionRefusedMsgAndDisconnect(clientSessionCtx);
    }

    private void sendConnectionRefusedMsgAndDisconnect(ClientSessionCtx clientSessionCtx) {
        try {
            MqttConnAckMessage mqttConnAckMsg = createMqttConnAckMsg();
            clientSessionCtx.getChannel().writeAndFlush(mqttConnAckMsg);
        } catch (Exception e) {
            log.warn("[{}][{}] Failed to send CONN_ACK response.",
                    clientSessionCtx.getClientId(), clientSessionCtx.getSessionId());
        } finally {
            disconnect(clientSessionCtx);
        }
    }

    private void disconnect(ClientSessionCtx clientSessionCtx) {
        clientMqttActorManager.disconnect(
                clientSessionCtx.getClientId(), newDisconnectMsg(clientSessionCtx.getSessionId()));
    }

    private DisconnectMsg newDisconnectMsg(UUID sessionId) {
        return new DisconnectMsg(sessionId,
                new DisconnectReason(DisconnectReasonType.ON_ERROR, "Failed to connect client"));
    }

    private MqttConnAckMessage createMqttConnAckMsg() {
        return createMqttConnAckMsg(CONNECTION_REFUSED_IDENTIFIER_REJECTED, false);
    }

    private MqttConnAckMessage createMqttConnAckMsg(MqttConnectReturnCode returnCode, boolean sessionPresent) {
        return mqttMessageGenerator.createMqttConnAckMsg(returnCode, sessionPresent);
    }

    private void logConnectionRefused(Throwable t, ClientSessionCtx clientSessionCtx) {
        if (t == null) {
            log.debug("[{}][{}] Client wasn't connected.", clientSessionCtx.getClientId(), clientSessionCtx.getSessionId());
        } else {
            log.debug("[{}][{}] Client wasn't connected. Exception - {}, reason - {}.",
                    clientSessionCtx.getClientId(), clientSessionCtx.getSessionId(), t.getClass().getSimpleName(), t.getMessage());
            log.trace("Detailed error: ", t);
        }
    }

    SessionInfo getSessionInfo(MqttConnectMsg msg, UUID sessionId, String clientId, ClientType clientType) {
        return ClientSessionInfoFactory.getSessionInfo(
                sessionId,
                !msg.isCleanSession(),
                serviceInfoProvider.getServiceId(),
                new ClientInfo(clientId, clientType),
                ClientSessionInfoFactory.getConnectionInfo(msg.getKeepAliveTimeSeconds()));
    }

    void validate(ClientSessionCtx ctx, MqttConnectMsg msg) {
        if (!msg.isCleanSession() && StringUtils.isEmpty(msg.getClientIdentifier())) {
            ctx.getChannel().writeAndFlush(createMqttConnAckMsg());
            throw new MqttException("Client identifier is empty and 'clean session' flag is set to 'false'!");
        }
    }

    @PreDestroy
    public void destroy() {
        log.debug("Shutting down executors");
        connectHandlerExecutor.shutdownNow();
    }
}
