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
package org.thingsboard.mqtt.broker.actors.client;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Component
@ConfigurationProperties(prefix = "actors.system.client")
public class ClientActorConfiguration {
    @Value("${actors.system.client.dispatcher-pool-size:8}")
    private int dispatcherSize;
    @Value("${actors.system.client.wait-before-generated-actor-stop-seconds:10}")
    private int timeToWaitBeforeGeneratedActorStopSeconds;
    @Value("${actors.system.client.wait-before-named-actor-stop-seconds:60}")
    private int timeToWaitBeforeNamedActorStopSeconds;
}
