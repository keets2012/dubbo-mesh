package com.alibaba.dubbo.performance.demo.agent.registry;

import java.util.Objects;

public class Endpoint {
    private final String host;
    private final int port;

    private String cpuLoad;

    public Endpoint(String host, int port, String cpuLoad) {
        this.host = host;
        this.cpuLoad = cpuLoad;
        this.port = port;
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

    public String toString() {
        return host + ":" + port + "/" + cpuLoad;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Endpoint endpoint = (Endpoint) o;
        return port == endpoint.port &&
                Objects.equals(host, endpoint.host) &&
                Objects.equals(cpuLoad, endpoint.cpuLoad);
    }

    @Override
    public int hashCode() {

        return Objects.hash(host, port, cpuLoad);
    }
}
