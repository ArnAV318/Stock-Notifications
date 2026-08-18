package com.projects.stockstream;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StockTickJob {

    private static final Logger LOG =
            LoggerFactory.getLogger(StockTickJob.class);

    private StockTickJob() {
    }

    public static void main(String[] args) throws Exception {
        ParameterTool parameters = ParameterTool.fromArgs(args);

        String brokers = parameters.get("brokers", "broker:29092");
        String topic = parameters.get("topic", "alpaca.marketdata");
        String groupId = parameters.get(
                "group-id",
                "stock-tick-flink-job"
        );

        StreamExecutionEnvironment environment =
                StreamExecutionEnvironment.getExecutionEnvironment();

        environment.setParallelism(2);
        environment.enableCheckpointing(10_000);
        environment.getConfig().setGlobalJobParameters(parameters);

        KafkaSource<String> kafkaSource =
                KafkaSource.<String>builder()
                        .setBootstrapServers(brokers)
                        .setTopics(topic)
                        .setGroupId(groupId)
                        .setStartingOffsets(
                                OffsetsInitializer.committedOffsets(
                                        OffsetResetStrategy.EARLIEST
                                )
                        )
                        .setValueOnlyDeserializer(
                                new org.apache.flink.api.common
                                        .serialization.SimpleStringSchema()
                        )
                        .build();

        DataStream<String> ticks = environment
                .fromSource(
                        kafkaSource,
                        WatermarkStrategy.noWatermarks(),
                        "alpaca-marketdata-source"
                )
                .uid("alpaca-marketdata-source");

        ticks
                .map(tick -> {
                    LOG.info("Received stock tick: {}", tick);
                    return tick;
                })
                .name("log-stock-ticks")
                .uid("log-stock-ticks");

        environment.execute("Stock Tick Logger");
    }
}