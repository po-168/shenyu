/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shenyu.admin.service.register;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.shenyu.admin.model.entity.MetaDataDO;
import org.apache.shenyu.admin.model.entity.SelectorDO;
import org.apache.shenyu.admin.service.MetaDataService;
import org.apache.shenyu.admin.utils.CommonUpstreamUtils;
import org.apache.shenyu.common.dto.convert.rule.impl.DubboRuleHandle;
import org.apache.shenyu.common.dto.convert.selector.DubboUpstream;
import org.apache.shenyu.common.enums.RpcTypeEnum;
import org.apache.shenyu.common.utils.GsonUtils;
import org.apache.shenyu.register.common.dto.MetaDataRegisterDTO;
import org.apache.shenyu.register.common.dto.URIRegisterDTO;
import org.apache.shenyu.register.common.enums.EventType;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * dubbo service register.
 */
@Service
public class ShenyuClientRegisterDubboServiceImpl extends AbstractShenyuClientRegisterServiceImpl {

    @Override
    public String rpcType() {
        return RpcTypeEnum.DUBBO.getName();
    }

    @Override
    protected String selectorHandler(final MetaDataRegisterDTO metaDataDTO) {
        return "";
    }

    @Override
    protected String ruleHandler() {
        return new DubboRuleHandle().toJson();
    }

    @Override
    protected void registerMetadata(final MetaDataRegisterDTO metaDataDTO) {
        MetaDataService metaDataService = getMetaDataService();
        MetaDataDO exist = metaDataService.findByPathAndNamespaceId(metaDataDTO.getPath(), metaDataDTO.getNamespaceId());
        metaDataService.saveOrUpdateMetaData(exist, metaDataDTO);
    }

    @Override
    protected String buildHandle(final List<URIRegisterDTO> uriList, final SelectorDO selectorDO) {
        List<DubboUpstream> addList = buildDubboUpstreamList(uriList);
        List<DubboUpstream> canAddList = new CopyOnWriteArrayList<>();
        boolean isEventDeleted = uriList.size() == 1 && EventType.DELETED.equals(uriList.get(0).getEventType());
        if (isEventDeleted) {
            addList.get(0).setStatus(false);
        }
        List<DubboUpstream> existList = GsonUtils.getInstance().fromCurrentList(selectorDO.getHandle(), DubboUpstream.class);
        if (CollectionUtils.isEmpty(existList)) {
            canAddList = addList;
        } else {
            List<DubboUpstream> diffList = addList.stream().filter(upstream -> !existList.contains(upstream)).collect(Collectors.toList());
            if (CollectionUtils.isNotEmpty(diffList)) {
                canAddList.addAll(diffList);
                existList.addAll(diffList);
            }
            List<DubboUpstream> diffStatusList = addList.stream().filter(upstream -> !upstream.isStatus()
                    || existList.stream().anyMatch(e -> e.equals(upstream) && e.isStatus() != upstream.isStatus())).collect(Collectors.toList());
            if (CollectionUtils.isNotEmpty(diffStatusList)) {
                canAddList.addAll(diffStatusList);
            }
        }

        if (doSubmit(selectorDO.getId(), canAddList)) {
            return null;
        }
        return GsonUtils.getInstance().toJson(CollectionUtils.isEmpty(existList) ? canAddList : existList);
    }

    private List<DubboUpstream> buildDubboUpstreamList(final List<URIRegisterDTO> uriList) {
        return uriList.stream()
                .map(dto -> CommonUpstreamUtils.buildDefaultDubboUpstream(dto.getHost(), dto.getPort()))
                .collect(Collectors.toCollection(CopyOnWriteArrayList::new));
    }
}
