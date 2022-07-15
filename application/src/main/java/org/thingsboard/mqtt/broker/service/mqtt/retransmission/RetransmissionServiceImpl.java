/**
 * Copyright © 2016-2020 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.mqtt.retransmission;

import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.util.ThingsBoardThreadFactory;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Service
@Slf4j
public class RetransmissionServiceImpl implements RetransmissionService {

    @Value("${mqtt.retransmission.scheduler-pool-size:0}")
    private int schedulerPoolSize;
    @Value("${mqtt.retransmission.initial-delay:10}")
    private int retransmissionInitDelay;
    @Value("${mqtt.retransmission.period:5}")
    private int retransmissionPeriod;

    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void init() {
        this.scheduler = Executors.newScheduledThreadPool(getCorePoolSize(), ThingsBoardThreadFactory.forName("retransmission-scheduler"));
    }

    private int getCorePoolSize() {
        if (schedulerPoolSize == 0) {
            return Math.max(1, Runtime.getRuntime().availableProcessors() * 2);
        }
        return schedulerPoolSize;
    }

    @PreDestroy
    public void destroy() {
        if (this.scheduler != null) {
            this.scheduler.shutdownNow();
        }
    }

    public void startPublishRetransmission(ClientSessionCtx sessionCtx, MqttPublishMessage mqttPubMsg, ChannelFuture channelFuture) {
        log.trace("[{}][{}] Executing startPublishRetransmission", sessionCtx.getClientId(), mqttPubMsg);
        ConcurrentMap<Integer, MqttPendingPublish> pendingPublishes = sessionCtx.getPendingPublishes();

        MqttPendingPublish pendingPublish = newMqttPendingPublish(sessionCtx, mqttPubMsg);
        pendingPublishes.put(pendingPublish.getPacketId(), pendingPublish);

        channelFuture.addListener(result -> {
            pendingPublish.setSent(true);
            if (result.cause() != null) {
                pendingPublishes.remove(pendingPublish.getPacketId());
            } else {
                if (pendingPublish.isSent() && pendingPublish.getQos() == MqttQoS.AT_MOST_ONCE) {
                    pendingPublishes.remove(pendingPublish.getPacketId());
                } else if (pendingPublish.isSent()) {
                    pendingPublish.startPublishRetransmissionTimer(this.scheduler, this::sendAndFlush);
                } else {
                    pendingPublishes.remove(pendingPublish.getPacketId());
                }
            }
        });
    }

    private void sendAndFlush(ClientSessionCtx sessionCtx, MqttMessage mqttMsg) {
        sessionCtx.getChannel().writeAndFlush(mqttMsg);
    }

    private MqttPendingPublish newMqttPendingPublish(ClientSessionCtx sessionCtx,
                                                     MqttPublishMessage mqttPubMsg) {
        return new MqttPendingPublish(
                sessionCtx,
                mqttPubMsg.variableHeader().packetId(),
                mqttPubMsg.payload().retain(),
                mqttPubMsg.fixedHeader().qosLevel(),
                mqttPubMsg,
                retransmissionInitDelay,
                retransmissionPeriod);
    }

    @Override
    public void onPubAckReceived(ClientSessionCtx ctx, int messageId) {
        log.trace("[{}][{}] Executing onPubAckReceived", ctx.getClientId(), messageId);
        MqttPendingPublish pendingPublish = ctx.getPendingPublishes().get(messageId);
        if (pendingPublish == null) {
            return;
        }
        pendingPublish.getPayload().release();
        ctx.getPendingPublishes().remove(messageId);
        pendingPublish.onPubAckReceived();
    }

    @Override
    public void onPubRecReceived(ClientSessionCtx ctx, MqttMessage pubRelMsg) {
        log.trace("[{}][{}] Executing onPubRecReceived", ctx.getClientId(), pubRelMsg);
        MqttPendingPublish pendingPublish = ctx.getPendingPublishes().get(((MqttMessageIdVariableHeader) pubRelMsg.variableHeader()).messageId());
        if (pendingPublish == null) {
            return;
        }
        pendingPublish.onPubAckReceived();

        ctx.getChannel().writeAndFlush(pubRelMsg);

        pendingPublish.setPubRelMessage(pubRelMsg);
        pendingPublish.startPubRelRetransmissionTimer(this.scheduler, this::sendAndFlush);
    }

    @Override
    public void onPubCompReceived(ClientSessionCtx ctx, int messageId) {
        log.trace("[{}][{}] Executing onPubCompReceived", ctx.getClientId(), messageId);
        MqttPendingPublish pendingPublish = ctx.getPendingPublishes().get(messageId);
        if (pendingPublish == null) {
            return;
        }
        pendingPublish.getPayload().release();
        ctx.getPendingPublishes().remove(messageId);
        pendingPublish.onPubCompReceived();
    }
}
