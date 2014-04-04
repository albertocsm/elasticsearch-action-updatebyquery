/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.common.lucene;

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.util.FixedBitSet;

import java.io.IOException;

/**
 * Collector that collects the hits in a {@link FixedBitSet} instance.
 */
public class TopLevelFixedBitSetCollector extends Collector {

    private final FixedBitSet bitSet;
    private int docBase;

    /**
     * @param maxDoc The higest Lucene docid + 1 from a toplevel IndexReader / IndexSearcher.
     */
    public TopLevelFixedBitSetCollector(int maxDoc) {
        this.bitSet = new FixedBitSet(maxDoc);
    }

    public void setScorer(Scorer scorer) throws IOException {
    }

    public void collect(int doc) throws IOException {
        bitSet.set(docBase + doc);
    }

    @Override
    public void setNextReader(AtomicReaderContext context) throws IOException {
        this.docBase = context.docBase;
    }

    public boolean acceptsDocsOutOfOrder() {
        return true;
    }

    /**
     * @return The matched Lucene doc ids as {@link FixedBitSet}
     */
    public FixedBitSet getBitSet() {
        return bitSet;
    }
}
