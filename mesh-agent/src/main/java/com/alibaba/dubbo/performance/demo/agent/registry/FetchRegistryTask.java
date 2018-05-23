package com.alibaba.dubbo.performance.demo.agent.registry;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by xuan on 2018/5/23.
 */
public class FetchRegistryTask implements Runnable{


    private Logger logger = LoggerFactory.getLogger(FetchRegistryTask.class);

    private final EtcdRegistry etcdRegistry;
    private final int replicationIntervalSeconds;
    private final ScheduledExecutorService scheduler;
    private final AtomicReference<Future> taskRef;
    private final AtomicBoolean started;



    FetchRegistryTask(EtcdRegistry etcdRegistry, int replicationIntervalSeconds) {
        this.etcdRegistry = etcdRegistry;
        this.replicationIntervalSeconds = replicationIntervalSeconds;
        this.scheduler = Executors.newScheduledThreadPool(1,
                new ThreadFactoryBuilder()
                        .setNameFormat("EtcdRegistry-FetchRegistryTask-%d")
                        .setDaemon(true)
                        .build());
        this.taskRef = new AtomicReference<>();
        this.started = new AtomicBoolean(false);
        logger.info("FetchRegistryTask init");

    }


    public void start(int delayMs) {
        if (started.compareAndSet(false, true)) {
            Future next = scheduler.schedule(this, delayMs, TimeUnit.SECONDS);
            taskRef.set(next);
        }

    }


    @Override
    public void run() {
        try {
            // TODO 2018-05-23 目前每次都是全量刷新
            etcdRegistry.fetchRegistry(true);
        } catch (Exception e) {
            logger.warn("There was a problem with the system info replicator", e);
        } finally {
            Future next = scheduler.schedule(this, replicationIntervalSeconds, TimeUnit.SECONDS);
            taskRef.set(next);
        }
        
    }
}
