/**
 * Copyright © 2016-2023 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.processing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.queue.common.TbProtoQueueMsg;
import org.thingsboard.mqtt.broker.queue.provider.PublishMsgQueueFactory;
import org.thingsboard.mqtt.broker.queue.publish.TbPublishServiceImpl;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@Slf4j
@Service
@RequiredArgsConstructor
public class PublishMsgQueuePublisherImpl implements PublishMsgQueuePublisher {

    private final PublishMsgQueueFactory publishMsgQueueFactory;

    private TbPublishServiceImpl<QueueProtos.PublishMsgProto> publisher;

    @PostConstruct
    public void init() {
        this.publisher = TbPublishServiceImpl.<QueueProtos.PublishMsgProto>builder()
                .queueName("publishMsg")
                .producer(publishMsgQueueFactory.createProducer())
                .build();
        this.publisher.init();
    }

    @Override
    public void sendMsg(QueueProtos.PublishMsgProto msgProto, TbQueueCallback callback) {
        publisher.send(new TbProtoQueueMsg<>(msgProto.getTopicName(), msgProto), callback);
    }

    @PreDestroy
    public void destroy() {
        publisher.destroy();
    }
}
