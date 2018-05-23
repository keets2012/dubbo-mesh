package com.alibaba.dubbo.performance.demo.agent.registry;

import com.coreos.jetcd.Client;
import com.coreos.jetcd.Watch;
import com.coreos.jetcd.data.ByteSequence;
import com.coreos.jetcd.watch.WatchEvent;
import com.coreos.jetcd.watch.WatchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @author keets
 * @data 2018/5/22.
 */
public class EtcdRegistryTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(EtcdRegistryTest.class);

    public static void main(String[] args) throws Exception {
        Args cmd = new Args();


        try (Client client = Client.builder().endpoints(cmd.endpoints).build();
             Watch watch = client.getWatchClient();
             Watch.Watcher watcher = watch.watch(ByteSequence.fromString(cmd.key))) {
            for (int i = 0; i < cmd.maxEvents; i++) {
                LOGGER.info("Watching for key={}", cmd.key);
                WatchResponse response = watcher.listen();

                for (WatchEvent event : response.getEvents()) {
                    LOGGER.info("type={}, key={}, value={}",
                            event.getEventType(),
                            Optional.ofNullable(event.getKeyValue().getKey())
                                    .map(ByteSequence::toStringUtf8)
                                    .orElse(""),
                            Optional.ofNullable(event.getKeyValue().getValue())
                                    .map(ByteSequence::toStringUtf8)
                                    .orElse("")
                    );
                }
            }
        } catch (Exception e) {
            LOGGER.error("Watching Error {}", e);
            System.exit(1);
        }
    }

    public static class Args {

        private List<String> endpoints = new ArrayList<>();


        private String key;


        private Integer maxEvents = Integer.MAX_VALUE;
    }
}
