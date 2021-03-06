/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.eagle.alert.engine.runner;

import org.apache.commons.collections.map.HashedMap;
import org.apache.eagle.alert.coordination.model.PublishSpec;
import org.apache.eagle.alert.engine.StreamContextImpl;
import org.apache.eagle.alert.engine.coordinator.*;
import org.apache.eagle.alert.engine.model.AlertPublishEvent;
import org.apache.eagle.alert.engine.model.AlertStreamEvent;
import org.apache.eagle.alert.engine.publisher.AlertPublishSpecListener;
import org.apache.eagle.alert.engine.publisher.AlertPublisher;
import org.apache.eagle.alert.engine.publisher.impl.AlertPublisherImpl;
import org.apache.eagle.alert.utils.AlertConstants;
import backtype.storm.metric.api.MultiCountMetric;
import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import com.typesafe.config.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("serial")
public class AlertPublisherBolt extends AbstractStreamBolt implements AlertPublishSpecListener {
    private static final Logger LOG = LoggerFactory.getLogger(AlertPublisherBolt.class);
    private final AlertPublisher alertPublisher;
    private volatile Map<String, Publishment> cachedPublishments = new HashMap<>();
    private volatile Map<String, PolicyDefinition> policyDefinitionMap;
    private volatile Map<String, StreamDefinition> streamDefinitionMap;

    public AlertPublisherBolt(String alertPublisherName, Config config, IMetadataChangeNotifyService coordinatorService) {
        super(alertPublisherName, coordinatorService, config);
        this.alertPublisher = new AlertPublisherImpl(alertPublisherName);
    }

    @Override
    public void internalPrepare(OutputCollector collector, IMetadataChangeNotifyService coordinatorService, Config config, TopologyContext context) {
        coordinatorService.registerListener(this);
        coordinatorService.init(config, MetadataType.ALERT_PUBLISH_BOLT);
        this.alertPublisher.init(config, stormConf);
        streamContext = new StreamContextImpl(config, context.registerMetric("eagle.publisher", new MultiCountMetric(), 60), context);
    }

    @Override
    public void execute(Tuple input) {
        try {
            streamContext.counter().scope("receive_count");
            AlertStreamEvent event = (AlertStreamEvent) input.getValueByField(AlertConstants.FIELD_1);
            wrapAlertPublishEvent(event);
            alertPublisher.nextEvent(event);
            this.collector.ack(input);
            streamContext.counter().scope("ack_count");
        } catch (Exception ex) {
            streamContext.counter().scope("fail_count");
            LOG.error(ex.getMessage(), ex);
            collector.reportError(ex);
        }
    }

    @Override
    public void cleanup() {
        alertPublisher.close();
        super.cleanup();
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields());
    }

    @Override
    public synchronized void onAlertPublishSpecChange(PublishSpec pubSpec, Map<String, StreamDefinition> sds) {
        if (pubSpec == null) {
            return;
        }
        this.streamDefinitionMap = sds;

        List<Publishment> newPublishments = pubSpec.getPublishments();
        if (newPublishments == null) {
            LOG.info("no publishments with PublishSpec {} for this topology", pubSpec);
            return;
        }

        Map<String, Publishment> newPublishmentsMap = new HashMap<>();
        newPublishments.forEach(p -> newPublishmentsMap.put(p.getName(), p));
        MapComparator<String, Publishment> comparator = new MapComparator<>(newPublishmentsMap, cachedPublishments);
        comparator.compare();

        List<Publishment> beforeModified = new ArrayList<>();
        comparator.getModified().forEach(p -> beforeModified.add(cachedPublishments.get(p.getName())));
        alertPublisher.onPublishChange(comparator.getAdded(), comparator.getRemoved(), comparator.getModified(), beforeModified);

        // switch
        cachedPublishments = newPublishmentsMap;
        specVersion = pubSpec.getVersion();
    }

    @Override
    public void onAlertPolicyChange(Map<String, PolicyDefinition> pds, Map<String, StreamDefinition> sds) {
        this.policyDefinitionMap = pds;
        this.streamDefinitionMap = sds;
    }

    private void wrapAlertPublishEvent(AlertStreamEvent event) {
        Map<String, Object> extraData = new HashedMap();
        List<String> appIds = new ArrayList<>();
        PolicyDefinition policyDefinition = policyDefinitionMap.get(event.getPolicyId());
        if (this.policyDefinitionMap != null && policyDefinition != null) {
            for (String inputStreamId : policyDefinition.getInputStreams()) {
                StreamDefinition sd = this.streamDefinitionMap.get(inputStreamId);
                if (sd != null) {
                    extraData.put(AlertPublishEvent.SITE_ID_KEY, sd.getSiteId());
                    appIds.add(sd.getDataSource());
                }
            }
            extraData.put(AlertPublishEvent.APP_IDS_KEY, appIds);
            extraData.put(AlertPublishEvent.POLICY_VALUE_KEY, policyDefinition.getDefinition().getValue());
        }
        event.setExtraData(extraData);
    }
}