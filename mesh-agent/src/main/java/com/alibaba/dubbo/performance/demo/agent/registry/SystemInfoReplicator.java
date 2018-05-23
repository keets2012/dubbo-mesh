package com.alibaba.dubbo.performance.demo.agent.registry;

import com.alibaba.fastjson.JSON;
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

public class SystemInfoReplicator implements Runnable {

    private Logger logger = LoggerFactory.getLogger(SystemInfoReplicator.class);


    private final EtcdRegistry etcdRegistry;
    private final String serviceName;
    private final int port;
    private final int replicationIntervalSeconds;
    private final ScheduledExecutorService scheduler;
    private final AtomicReference<Future> taskRef;
    private final AtomicBoolean started;


    SystemInfoReplicator(EtcdRegistry etcdRegistry, int replicationIntervalSeconds, String serviceName, int port) {
        this.etcdRegistry = etcdRegistry;
        this.replicationIntervalSeconds = replicationIntervalSeconds;
        this.serviceName = serviceName;
        this.port = port;
        this.scheduler = Executors.newScheduledThreadPool(1,
                new ThreadFactoryBuilder()
                        .setNameFormat("EtcdRegistry-SystemInfoReplicator-%d")
                        .setDaemon(true)
                        .build());
        this.taskRef = new AtomicReference<>();
        this.started = new AtomicBoolean(false);
        logger.info("systemInfoReplicator init");

    }

    public void start(int delayMs) {
        if (started.compareAndSet(false, true)) {
            Future next = scheduler.schedule(this, delayMs, TimeUnit.SECONDS);
            taskRef.set(next);
        }

    }

    /**
     * 用于事件监听的回调
     *
     * @return
     */
    public boolean onDemandUpdate() {
        if (!scheduler.isShutdown()) {
            scheduler.submit(() -> {
                logger.debug("Executing on-demand update of local InstanceInfo");

                Future latestTask = taskRef.get();
                if (latestTask != null && !latestTask.isDone()) {
                    logger.debug("Canceling the latest scheduled update");
                    latestTask.cancel(false);
                }

                SystemInfoReplicator.this.run();
            });
            return true;
        } else {
            logger.warn("Ignoring onDemand update due to stopped scheduler");
            return false;
        }

    }

    /**
     * 获取本地的负载信息，如果需要的话重新注册
     */
    @Override
    public void run() {
        try {
            etcdRegistry.register(serviceName, port);
            String s = JSON.toJSONString(etcdRegistry.find(serviceName));
            logger.info(s);
        } catch (Exception e) {
            logger.warn("There was a problem with the system info replicator", e);
        } finally {
            Future next = scheduler.schedule(this, replicationIntervalSeconds, TimeUnit.SECONDS);
            taskRef.set(next);
        }


    }
}
