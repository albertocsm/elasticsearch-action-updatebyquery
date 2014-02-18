ElasticSearch Update By Query Plugin
====================================

The update by query API allows all documents that with the query to be updated with a script.
This is experimental.

This plugin is an adaptation of [elasticsearch/elasticsearch#2230][es#2230].

Installation
-----------

Simply run at the root of your ElasticSearch v0.20.2+ installation:

    bin/plugin -install com.yakaz.elasticsearch.plugins/elasticsearch-action-updatebyquery/1.6.0

This will download the plugin from the Central Maven Repository.

For older versions of ElasticSearch, you can still use the longer:

    bin/plugin -url http://oss.sonatype.org/content/repositories/releases/com/yakaz/elasticsearch/plugins/elasticsearch-action-updatebyquery/1.0.0/elasticsearch-action-updatebyquery-1.0.0.zip install elasticsearch-action-updatebyquery

In order to declare this plugin as a dependency, add the following to your `pom.xml`:

```xml
<dependency>
    <groupId>com.yakaz.elasticsearch.plugins</groupId>
    <artifactId>elasticsearch-action-updatebyquery</artifactId>
    <version>1.6.0</version>
</dependency>
```

Version matrix:

    ┌───────────────────────────────┬────────────────────────┐
    │ Update By Query Action Plugin │ ElasticSearch          │
    ├───────────────────────────────┼────────────────────────┤
    │ 1.6.x                         │ 1.0.0 ─► (?)           │
    ├───────────────────────────────┼────────────────────────┤
    │ 1.5.x                         │ 1.0.0.Beta1            │
    ├───────────────────────────────┼────────────────────────┤
    │ 1.4.1                         │ 0.90.10 ─► (0.90.11)   │
    ├───────────────────────────────┼────────────────────────┤
    │ 1.4.0                         │ 0.90.6 ─► 0.90.9       │
    ├───────────────────────────────┼────────────────────────┤
    │ 1.3.x                         │ 0.90.4 ─► 0.90.5       │
    ├───────────────────────────────┼────────────────────────┤
    │ 1.2.x                         │ 0.90.3                 │
    ├───────────────────────────────┼────────────────────────┤
    │ 1.1.x                         │ 0.90.0.beta1 ─► 0.90.2 │
    ├───────────────────────────────┼────────────────────────┤
    │ 1.0.x                         │ 0.20.0 ─► 0.20.4       │
    └───────────────────────────────┴────────────────────────┘

Description
-----------

The update by query API allows all documents that with the query to be updated with a script.
This feature is experimental.

The update by query works a bit different than the delete by query.
The update by query api translates the documents that match into bulk index / delete requests.
After the bulk limit has been reached, the bulk requests created thus far will be executed.
After the bulk requests have been executed the next batch of requests will be prepared and executed.
This behavior continues until all documents that matched the query have been processed.
The bulk size can be configured with the `action.updatebyquery.bulk_size` option in the elasticsearch configuration.
For example: `action.updatebyquery.bulk_size=2500`

Example usage
-------------

Index an example document:

```sh
curl -XPUT 'localhost:9200/twitter/tweet/1' -d '
{
    "text" : {
        "message" : "you know for search"
    },
    "likes": 0
}'
```

Execute the following update by query command:

```sh
curl -XPOST 'localhost:9200/twitter/_update_by_query' -d '
{
    "query" : {
        "term" : {
            "message" : "you"
        }
    },
    "script" : "ctx._source.likes += 1"
}'
```

This will yield the following response:

```js
{
  "ok" : true,
  "took" : 9,
  "total" : 1,
  "updated" : 1,
  "indices" : [ {
    "twitter" : { }
  } ]
}
```

By default no bulk item responses are included in the response.
If there are bulk item responses included in the response, the bulk response items are grouped by index and shard.
This can be controlled by the `response` option.

Options:
--------

Additional general options in request body:

* `lang`: The script language.
* `params`: The script parameters.

### Query string options:

* `replication`: The replication type for the delete/index operation (sync or async).
* `consistency`: The write consistency of the index/delete operation.
* `response`: What bulk response items to include into the update by query response.
  This can be set to the following: `none`, `failed` and `all`.
  Defaults to `none`.
  Warning: `all` can result in out of memory errors when the query results in many hits.
* `routing`: Sets the routing that will be used to route the document to the relevant shard.
* `timeout`: Timeout waiting for a shard to become available.

Context variables
-----------------

The script has access to the following variables:

* `ctx`
  * `_index`
  * `_uid`
  * `_type`
  * `_id`
  * `_version`
  * `_source`
  * `_routing`
  * `_parent`
  * `_timestamp` (in milliseconds)
  * `_ttl` (in milliseconds)

### Output variables

You may update the following variables:

* `ctx`
  * `_timestamp`
  * `_ttl`

They are parsed as time values: either milliseconds since epoch, or a duration string like `"30m"`.
If you wish to change the timestamp of your document, to make it more recent, either set a new value to `_timestamp` or set it to `null`.
Otherwise the previous `_timestamp` is preserved.
`_ttl` is preserved too if you don't change or remove it.


[es#2230]: https://github.com/elasticsearch/elasticsearch/issues/2230
