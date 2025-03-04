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

import {BaseData} from '@shared/models/base-data';
import {PageComponent} from '@shared/components/page.component';
import {AfterViewInit, Directive, Input, OnInit, QueryList, ViewChildren} from '@angular/core';
import {Store} from '@ngrx/store';
import {AppState} from '@core/core.state';
import {EntityTableConfig} from '@home/models/entity/entities-table-config.models';
import {MatTab} from '@angular/material/tabs';
import {BehaviorSubject} from 'rxjs';
import {getCurrentAuthUser} from '@core/auth/auth.selectors';
import {AuthUser} from '@shared/models/user.model';
import {EntityType} from '@shared/models/entity-type.models';
import {FormGroup} from '@angular/forms';
import {PageLink} from '@shared/models/page/page-link';
import {NULL_UUID} from "@shared/models/constants";

@Directive()
// tslint:disable-next-line:directive-class-suffix
export abstract class EntityTabsComponent<T extends BaseData,
  P extends PageLink = PageLink,
  L extends BaseData = T,
  C extends EntityTableConfig<T, P, L> = EntityTableConfig<T, P, L>>
  extends PageComponent implements OnInit, AfterViewInit {

  entityTypes = EntityType;

  authUser: AuthUser;

  nullUid = NULL_UUID;

  entityValue: T;

  entitiesTableConfigValue: C;

  @ViewChildren(MatTab) entityTabs: QueryList<MatTab>;

  isEditValue: boolean;

  @Input()
  set isEdit(isEdit: boolean) {
    this.isEditValue = isEdit;
  }

  get isEdit() {
    return this.isEditValue;
  }

  @Input()
  set entity(entity: T) {
    this.setEntity(entity);
  }

  get entity(): T {
    return this.entityValue;
  }

  @Input()
  set entitiesTableConfig(entitiesTableConfig: C) {
    this.setEntitiesTableConfig(entitiesTableConfig);
  }

  get entitiesTableConfig(): C {
    return this.entitiesTableConfigValue;
  }

  @Input()
  detailsForm: FormGroup;

  private entityTabsSubject = new BehaviorSubject<Array<MatTab>>(null);

  entityTabsChanged = this.entityTabsSubject.asObservable();

  protected constructor(protected store: Store<AppState>) {
    super(store);
    this.authUser = getCurrentAuthUser(store);
  }

  ngOnInit() {
  }

  ngAfterViewInit(): void {
    this.entityTabsSubject.next(this.entityTabs.toArray());
    this.entityTabs.changes.subscribe(
      () => {
        this.entityTabsSubject.next(this.entityTabs.toArray());
      }
    );
  }

  protected setEntity(entity: T) {
    this.entityValue = entity;
  }

  protected setEntitiesTableConfig(entitiesTableConfig: C) {
    this.entitiesTableConfigValue = entitiesTableConfig;
  }

}
