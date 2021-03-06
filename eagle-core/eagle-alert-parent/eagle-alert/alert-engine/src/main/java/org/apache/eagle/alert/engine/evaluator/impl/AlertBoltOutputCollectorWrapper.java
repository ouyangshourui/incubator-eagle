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
package org.apache.eagle.alert.engine.evaluator.impl;

import org.apache.eagle.alert.engine.AlertStreamCollector;
import org.apache.eagle.alert.engine.StreamContext;
import org.apache.eagle.alert.engine.model.AlertStreamEvent;
import backtype.storm.task.OutputCollector;

import java.util.Arrays;

public class AlertBoltOutputCollectorWrapper implements AlertStreamCollector {
    private final OutputCollector delegate;
    private final Object outputLock;
    private final StreamContext streamContext;

    public AlertBoltOutputCollectorWrapper(OutputCollector outputCollector, Object outputLock, StreamContext streamContext) {
        this.delegate = outputCollector;
        this.outputLock = outputLock;
        this.streamContext = streamContext;
    }

    @Override
    public void emit(AlertStreamEvent event) {
        synchronized (outputLock) {
            streamContext.counter().scope("alert_count").incr();
            delegate.emit(Arrays.asList(event.getStreamId(), event));
        }
    }

    @Override
    public void flush() {
        // do nothing
    }

    @Override
    public void close() {

    }
}