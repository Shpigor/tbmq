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

import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SharedModule } from '@shared/shared.module';
import { HomeComponentsModule } from '@home/components/home-components.module';
import { RetainedMessagesComponent } from "@home/pages/retained-messages/retained-messages.component";
import { RetainedMessagesRoutingModule } from "@home/pages/retained-messages/retained-messages-routing.module";

@NgModule({
  declarations: [
    RetainedMessagesComponent
  ],
  imports: [
    CommonModule,
    SharedModule,
    HomeComponentsModule,
    RetainedMessagesRoutingModule
  ]
})

export class RetainedMessagesModule {
}
