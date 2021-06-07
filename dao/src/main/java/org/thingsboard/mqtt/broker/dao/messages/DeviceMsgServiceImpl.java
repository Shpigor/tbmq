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
package org.thingsboard.mqtt.broker.dao.messages;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.DevicePublishMsg;
import org.thingsboard.mqtt.broker.common.data.PersistedPacketType;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceMsgServiceImpl implements DeviceMsgService {
    @Value("${mqtt.persistent-session.device.persisted-messages.limit:1000}")
    private int messagesLimit;

    private final DeviceMsgDao deviceMsgDao;

    @Override
    public void save(List<DevicePublishMsg> devicePublishMessages) {
        log.trace("Saving device publish messages - {}.", devicePublishMessages);
        deviceMsgDao.save(devicePublishMessages);
    }

    @Override
    public List<DevicePublishMsg> findPersistedMessages(String clientId) {
        log.trace("[{}] Loading persisted messages.", clientId);
        return deviceMsgDao.findPersistedMessages(clientId, messagesLimit);
    }

    @Override
    public void removePersistedMessages(String clientId) {
        log.trace("[{}] Removing persisted messages.", clientId);
        try {
            deviceMsgDao.removePersistedMessages(clientId);
        } catch (Exception e) {
            log.warn("[{}] Failed to remove persisted messages. Reason - {}.", clientId, e.getMessage());
        }
    }

    @Override
    public void removePersistedMessage(String clientId, int packetId) {
        log.trace("[{}] Removing persisted message with packetId {}.", clientId, packetId);
        deviceMsgDao.removePersistedMessage(clientId, packetId);
    }

    @Override
    public void updatePacketReceived(String clientId, int packetId) {
        log.trace("[{}] Updating packet type to PUBREL for packetId {}.", clientId, packetId);
        deviceMsgDao.updatePacketType(clientId, packetId, PersistedPacketType.PUBREL);
    }
}
