///
/// Copyright © 2016-2023 The Thingsboard Authors
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///     http://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///

import { Component } from '@angular/core';
import { EntityColumn, EntityTableColumn } from '@home/models/entity/entities-table-config.models';
import { DomSanitizer } from '@angular/platform-browser';
import { KafkaService } from '@core/http/kafka.service';
import { KafkaBroker } from '@shared/models/kafka.model';
import { formatBytes, KafkaTableComponent } from '@home/components/entity/kafka-table.component';
import { HomePageTitleType } from '@shared/models/home-page.model';

@Component({
  selector: 'tb-kafka-brokers-table',
  templateUrl: './kafka-brokers-table.component.html',
  styleUrls: ['./kafka-brokers-table.component.scss']
})
export class KafkaBrokersTableComponent extends KafkaTableComponent<KafkaBroker> {

  cardType = HomePageTitleType.KAFKA_BROKERS;
  fetchEntities$ = () => this.kafkaService.getKafkaBrokers(this.pageLink);

  constructor(private kafkaService: KafkaService,
              protected domSanitizer: DomSanitizer) {
    super(domSanitizer);
  }

  getColumns() {
    const columns: Array<EntityColumn<KafkaBroker>> = [];
    columns.push(
      new EntityTableColumn<KafkaBroker>('brokerId', 'kafka.id', '25%', entity => entity.brokerId),
      new EntityTableColumn<KafkaBroker>('address', 'kafka.address', '25%'),
      new EntityTableColumn<KafkaBroker>('brokerSize', 'kafka.size', '25%', entity => {
        return formatBytes(entity.brokerSize);
      })
    );
    return columns;
  }
}
