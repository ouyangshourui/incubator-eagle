/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.eagle.service.alert.resource.impl;

import com.typesafe.config.ConfigFactory;
import org.apache.eagle.alert.engine.coordinator.PolicyDefinition;
import org.apache.eagle.alert.metadata.IMetadataDao;
import org.apache.eagle.alert.metadata.impl.InMemMetadataDaoImpl;
import org.apache.eagle.alert.metadata.impl.MetadataDaoFactory;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.LoggerFactory;

/**
 * @since May 1, 2016
 */
public class InMemoryTest {

    private IMetadataDao dao = new InMemMetadataDaoImpl(ConfigFactory.load());

    @Test
    public void test_AddPolicy() {

        LoggerFactory.getLogger(InMemoryTest.class);

        MetadataDaoFactory.getInstance().getMetadataDao();

        PolicyDefinition pd = new PolicyDefinition();
        pd.setName("pd1");
        dao.addPolicy(pd);

        Assert.assertEquals(1, dao.listPolicies().size());
    }
}
