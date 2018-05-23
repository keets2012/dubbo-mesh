package com.alibaba.dubbo.performance.demo.agent.registry;

import java.util.Objects;
import java.util.UUID;

public class Endpoint {
    private String routeId;
    private final String host;
    private final int port;

    private String serviceName;
    private String cpuLoad;

    public Endpoint(String host, int port, String cpuLoad, String serviceName) {

        this.host = host;
        this.cpuLoad = cpuLoad;
        this.port = port;
        this.serviceName = serviceName;
        this.routeId = UUID.randomUUID().toString();
    }

    public String getRouteId() {
        return routeId;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getCpuLoad() {
        return cpuLoad;
    }

    public String getServiceName() {
        return serviceName;
    }

    @Override
    public String toString() {
        return "Endpoint{" +
                "routeId='" + routeId + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", serviceName='" + serviceName + '\'' +
                ", cpuLoad='" + cpuLoad + '\'' +
                '}';
    }



}
