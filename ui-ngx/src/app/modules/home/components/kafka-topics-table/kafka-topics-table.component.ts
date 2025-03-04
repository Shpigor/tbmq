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

import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { KafkaService } from '@core/http/kafka.service';
import { KafkaTopicsTableConfig } from '@home/components/kafka-topics-table/kafka-topics-table-config';
import { TranslateService } from '@ngx-translate/core';

@Component({
  selector: 'tb-kafka-topics-table',
  templateUrl: './kafka-topics-table.component.html',
  styleUrls: ['./kafka-topics-table.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class KafkaTopicsTableComponent implements OnInit {

  kafkaTableConfig: KafkaTopicsTableConfig;

  constructor(private kafkaService: KafkaService,
              private translate: TranslateService,
              ) {
  }

  ngOnInit(): void {
    this.kafkaTableConfig = new KafkaTopicsTableConfig(
      this.kafkaService,
      this.translate);
  }
}
