/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
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

package org.elasticsearch.test.integration.updatebyquery;

import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthStatus;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.count.CountResponse;
import org.elasticsearch.action.updatebyquery.BulkResponseOption;
import org.elasticsearch.action.updatebyquery.IndexUpdateByQueryResponse;
import org.elasticsearch.action.updatebyquery.UpdateByQueryResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.test.integration.AbstractNodesTests;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.*;

import static org.elasticsearch.cluster.metadata.AliasAction.newAddAliasAction;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class UpdateByQueryTests extends AbstractNodesTests {

    private Client client;

    @BeforeClass
    public void startNodes() throws Exception {
        startNode("node1", nodeSettings());
        startNode("node2", nodeSettings());
        client = getClient();
    }

    protected void createIndex(String indexName) throws Exception {
        try {
            client.admin().indices().prepareDelete(indexName).execute().actionGet();
        } catch (Exception e) {
            // ignore
        }
        logger.info("--> creating index test");
        client.admin().indices().prepareCreate(indexName)
                .addMapping("type1", XContentFactory.jsonBuilder()
                        .startObject()
                        .startObject("type1")
                        .startObject("_timestamp").field("enabled", true).field("store", "yes").endObject()
                        .startObject("_ttl").field("enabled", true).field("store", "yes").endObject()
                        .endObject()
                        .endObject())
                .execute().actionGet();
    }

    protected Settings nodeSettings() {
        return ImmutableSettings.settingsBuilder()
                .put("action.updatebyquery.bulk_size", 5)
                .put("index.number_of_shards", 2)
                .put("index.number_of_replicas", 1)
                .build();
    }

    @AfterClass
    public void closeNodes() {
        client.close();
        closeAllNodes();
    }

    protected Client getClient() {
        return client("node1");
    }

    @Test
    public void testUpdateByQuery() throws Exception {
        createIndex("test");
        ClusterHealthResponse clusterHealth = client.admin().cluster().prepareHealth().setWaitForGreenStatus().execute().actionGet();
        assertThat(clusterHealth.timedOut(), equalTo(false));
        assertThat(clusterHealth.status(), equalTo(ClusterHealthStatus.GREEN));

        final long numDocs = 25;
        for (int i = 1; i <= numDocs; i++) {
            client.prepareIndex("test", "type1", Integer.toString(i)).setSource("field1", 1).execute().actionGet();
            if (i % 10 == 0) {
                client.admin().indices().prepareFlush("test").execute().actionGet();
            }
        }
        // Add one doc with a different type.
        client.prepareIndex("test", "type2", "1").setSource("field1", 1).execute().actionGet();
        client.admin().indices().prepareRefresh("test").execute().actionGet();

        CountResponse countResponse = client.prepareCount("test")
                .setQuery(termQuery("field1", 2).buildAsBytes())
                .execute()
                .actionGet();
        assertThat(countResponse.count(), equalTo(0L));

        Map<String, Object> scriptParams = new HashMap<String, Object>();
        UpdateByQueryResponse response = client.prepareUpdateByQuery()
                .setIndices("test")
                .setTypes("type1")
                .setIncludeBulkResponses(BulkResponseOption.ALL)
                .setScript("ctx._source.field1 += 1").setScriptParams(scriptParams)
                .setQuery(matchAllQuery())
                .execute()
                .actionGet();

        assertThat(response, notNullValue());
        assertThat(response.mainFailures().length, equalTo(0));
        assertThat(response.totalHits(), equalTo(numDocs));
        assertThat(response.updated(), equalTo(numDocs));
        assertThat(response.indexResponses().length, equalTo(1));
        assertThat(response.indexResponses()[0].countShardResponses(), equalTo(numDocs));

        assertThat(response.indexResponses()[0].failuresByShard().isEmpty(), equalTo(true));
        for (BulkItemResponse[] shardResponses : response.indexResponses()[0].responsesByShard().values()) {
            for (BulkItemResponse shardResponse : shardResponses) {
                assertThat(shardResponse.version(), equalTo(2L));
                assertThat(shardResponse.failed(), equalTo(false));
                assertThat(shardResponse.failure(), nullValue());
                assertThat(shardResponse.failureMessage(), nullValue());
            }
        }

        client.admin().indices().prepareRefresh("test").execute().actionGet();
        countResponse = client.prepareCount("test")
                .setQuery(termQuery("field1", 2).buildAsBytes())
                .execute()
                .actionGet();
        assertThat(countResponse.count(), equalTo(numDocs));

        response = client.prepareUpdateByQuery()
                .setIndices("test")
                .setTypes("type1")
                .setScript("ctx._source.field1 += 1").setScriptParams(scriptParams)
                .setQuery(matchAllQuery())
                .execute()
                .actionGet();

        assertThat(response, notNullValue());
        assertThat(response.totalHits(), equalTo(numDocs));
        assertThat(response.updated(), equalTo(numDocs));
        assertThat(response.indexResponses().length, equalTo(1));
        assertThat(response.indexResponses()[0].totalHits(), equalTo(numDocs));
        assertThat(response.indexResponses()[0].updated(), equalTo(numDocs));
        assertThat(response.indexResponses()[0].failuresByShard().size(), equalTo(0));
        assertThat(response.indexResponses()[0].responsesByShard().size(), equalTo(0));

        client.admin().indices().prepareRefresh("test").execute().actionGet();
        countResponse = client.prepareCount("test")
                .setQuery(termQuery("field1", 3).buildAsBytes())
                .execute()
                .actionGet();
        assertThat(countResponse.count(), equalTo(numDocs));
    }

    @Test
    public void testUpdateByQuery_multipleIndices() throws Exception {
        try {
            client.admin().indices().prepareDelete("test").execute().actionGet();
        } catch (Exception e) {
            // ignore
        }
        createIndex("test1");
        createIndex("test2");
        ClusterHealthResponse clusterHealth = client.admin().cluster().prepareHealth().setWaitForGreenStatus().execute().actionGet();
        assertThat(clusterHealth.timedOut(), equalTo(false));
        assertThat(clusterHealth.status(), equalTo(ClusterHealthStatus.GREEN));

        final long numDocs = 100;
        final long docsPerIndex = 10;
        String current = "test0";
        int id = 1;
        for (int i = 0; i < numDocs; i++) {
            if (i % docsPerIndex == 0) {
                current = "test" + (i / docsPerIndex);
                id = 1;
            }
            client.prepareIndex(current, "type1", Integer.toString(id++)).setSource("field1", 1).execute().actionGet();
            if (i % 5 == 0) {
                client.admin().indices().prepareFlush(current).execute().actionGet();
            }
        }
        // Add one doc with a different type.
        client.admin().indices().prepareRefresh("*").execute().actionGet();

        CountResponse countResponse = client.prepareCount("*")
                .setQuery(termQuery("field1", 2).buildAsBytes())
                .execute()
                .actionGet();
        assertThat(countResponse.count(), equalTo(0L));

        Map<String, Object> scriptParams = new HashMap<String, Object>();
        UpdateByQueryResponse response = client.prepareUpdateByQuery()
                .setIndices("*")
                .setTypes("type1")
                .setIncludeBulkResponses(BulkResponseOption.ALL)
                .setScript("ctx._source.field1 += 1").setScriptParams(scriptParams)
                .setQuery(matchAllQuery())
                .execute()
                .actionGet();

        assertThat(response, notNullValue());
        assertThat(response.totalHits(), equalTo(numDocs));
        assertThat(response.updated(), equalTo(numDocs));
        assertThat(response.indexResponses().length, equalTo(10));
        Arrays.sort(response.indexResponses(), new Comparator<IndexUpdateByQueryResponse>() {

            public int compare(IndexUpdateByQueryResponse res1, IndexUpdateByQueryResponse res2) {
                int index1 = res1.index().charAt(res1.index().length() - 1);
                int index2 = res2.index().charAt(res2.index().length() - 1);
                return index1 - index2;
            }

        });

        for (int i = 0; i < response.indexResponses().length; i++) {
            String index = "test" + i;
            assertThat(response.indexResponses()[i].index(), equalTo(index));
            assertThat(response.indexResponses()[i].countShardResponses(), equalTo(docsPerIndex));

            assertThat(response.indexResponses()[i].failuresByShard().isEmpty(), equalTo(true));
            for (BulkItemResponse[] shardResponses : response.indexResponses()[i].responsesByShard().values()) {
                for (BulkItemResponse shardResponse : shardResponses) {
                    assertThat(shardResponse.version(), equalTo(2L));
                    assertThat(shardResponse.failed(), equalTo(false));
                    assertThat(shardResponse.failure(), nullValue());
                    assertThat(shardResponse.failureMessage(), nullValue());
                }
            }
        }

        assertThat(response.mainFailures().length, equalTo(0));

        client.admin().indices().prepareRefresh("*").execute().actionGet();
        countResponse = client.prepareCount("*")
                .setQuery(termQuery("field1", 2).buildAsBytes())
                .execute()
                .actionGet();
        assertThat(countResponse.count(), equalTo(numDocs));
    }

    @Test
    public void testUpdateByQuery_usingAliases() {
        try {
            client.admin().indices().prepareDelete("test").execute().actionGet();
        } catch (Exception e) {
            // ignore
        }
        client.admin().indices().prepareCreate("test").execute().actionGet();
        client.admin().cluster().prepareHealth().setWaitForGreenStatus().execute().actionGet();

        client.admin().indices().prepareAliases().addAliasAction(
                newAddAliasAction("test", "alias0").routing("0")
        ).execute().actionGet();

        client.admin().indices().prepareAliases().addAliasAction(
                newAddAliasAction("test", "alias1").filter(FilterBuilders.termFilter("field", "value2")).routing("1")
        ).execute().actionGet();

        client.prepareIndex("alias0", "type1", "1").setSource("field", "value1").setRefresh(true).execute().actionGet();
        client.prepareIndex("alias0", "type1", "2").setSource("field", "value2").setRefresh(true).execute().actionGet();
        client.admin().indices().prepareFlush("test").execute().actionGet();
        client.prepareIndex("alias1", "type1", "3").setSource("field", "value1").setRefresh(true).execute().actionGet();
        client.prepareIndex("alias1", "type1", "4").setSource("field", "value2").setRefresh(true).execute().actionGet();

        assertThat(client.prepareGet("alias0", "type1", "1").execute().actionGet().exists(), equalTo(true));
        assertThat(client.prepareGet("alias0", "type1", "2").execute().actionGet().exists(), equalTo(true));
        assertThat(client.prepareGet("alias1", "type1", "3").execute().actionGet().exists(), equalTo(true));
        assertThat(client.prepareGet("alias1", "type1", "4").execute().actionGet().exists(), equalTo(true));

        UpdateByQueryResponse response = client.prepareUpdateByQuery()
                .setIndices("alias1")
                .setQuery(matchAllQuery())
                .setScript("ctx.op = \"delete\"")
                .execute().actionGet();
        assertThat(response.totalHits(), equalTo(1L));
        assertThat(response.updated(), equalTo(1L));

        response = client.prepareUpdateByQuery()
                .setIndices("alias0")
                .setQuery(matchAllQuery())
                .setScript("ctx.op = \"delete\"")
                .execute().actionGet();
        assertThat(response.totalHits(), equalTo(2L));
        assertThat(response.updated(), equalTo(2L));

        assertThat(client.prepareGet("alias0", "type1", "1").execute().actionGet().exists(), equalTo(false));
        assertThat(client.prepareGet("alias0", "type1", "2").execute().actionGet().exists(), equalTo(false));
        assertThat(client.prepareGet("alias1", "type1", "3").execute().actionGet().exists(), equalTo(true));
        assertThat(client.prepareGet("alias1", "type1", "4").execute().actionGet().exists(), equalTo(false));
    }

}
