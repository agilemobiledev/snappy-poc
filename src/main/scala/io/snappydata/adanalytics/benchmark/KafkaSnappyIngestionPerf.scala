/*
 * Copyright (c) 2016 SnappyData, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */

package io.snappydata.adanalytics.benchmark

import io.snappydata.adanalytics.aggregator.Constants
import Constants._
import org.apache.spark.SparkContext
import org.apache.spark.streaming.SnappyStreamingContext

/**
  * Simple direct kafka spark streaming program which pulls log messages
  * from kafka broker and ingest those log messages to Snappy store.
  */
object KafkaSnappyIngestionPerf extends App {

  val sparkConf = new org.apache.spark.SparkConf()
    .setAppName(getClass.getSimpleName)
    .set("spark.sql.inMemoryColumnarStorage.compressed", "false")
    .set("spark.sql.inMemoryColumnarStorage.batchSize", "2000")
    .set("spark.streaming.kafka.maxRatePerPartition" , "500")
    .setMaster("local[*]")
    //.setMaster("snappydata://localhost:10334")

  val sc = new SparkContext(sparkConf)
  val snsc = new SnappyStreamingContext(sc, batchDuration)

  snsc.sql("drop table if exists adImpressions")
  snsc.sql("drop table if exists adImpressionStream")

  // Create a stream of AdImpressionLog which will pull the log messages
  // from Kafka broker
  snsc.sql(s"create stream table adImpressionStream (" +
    " timestamp long," +
    " publisher string," +
    " advertiser string," +
    " website string," +
    " geo string," +
    " bid double," +
    " cookie string) " +
    " using directkafka_stream options (" +
    " storagelevel 'MEMORY_AND_DISK_SER_2'," +
    " rowConverter 'io.snappydata.adanalytics.aggregator.AdImpressionToRowsConverter' ," +
    s" kafkaParams 'metadata.broker.list->$brokerList'," +
    s" topics '$kafkaTopic'," +
    " K 'java.lang.String'," +
    " V 'io.snappydata.examples.adanalytics.AdImpressionLog', " +
    " KD 'kafka.serializer.StringDecoder', " +
    " VD 'io.snappydata.adanalytics.aggregator.AdImpressionLogAvroDecoder')")

//  snsc.sql("create table adImpressionsRow(timestamp bigint, publisher varchar(15), " +
//    "advertiser varchar(15), website varchar(20), geo varchar(8), bid double, cookie varchar(20), primary key(timestamp)) " +
//    "using row " +
//    "options ( PARTITION_BY 'PRIMARY KEY', BUCKETS '40')")

  snsc.sql("create table adImpressions(timestamp long, publisher string, " +
    "advertiser string, website string, geo string, bid double, cookie string) " +
    "using column " +
    "options ( BUCKETS '29')")

//  snsc.sql("create sample table adImpressionsSample on adImpressions OPTIONS(buckets '3', qcs 'geo', " +
//    "fraction '0.03', strataReservoirSize '50', persistent '') as (select * from adImpressions)")
//
//  snsc.sql("CREATE SAMPLE TABLE adImpressionsSample2 (timestamp long, publisher string, " +
//    "advertiser string, website string, geo string, bid double, cookie string)" +
//    " OPTIONS(qcs 'publisher', fraction '0.03', strataReservoirSize '50')")

  // Save the streaming data to snappy store per second (btachDuration)
  snsc.getSchemaDStream("adImpressionStream").foreachDataFrame(df => {
    df.write.insertInto("adImpression")
  })

  snsc.start
  snsc.awaitTermination
}
