package com.alibaba.dubbo.performance.demo.agent.loadbalance;

import com.alibaba.dubbo.performance.demo.agent.dubbo.model.RpcInvocation;
import com.alibaba.dubbo.performance.demo.agent.registry.Endpoint;
import com.alibaba.dubbo.performance.demo.agent.registry.IRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.style.ToStringCreator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author keets
 * @data 2018/5/22.
 */
public class CpuLoadBalance implements LoadBalance {

    private Logger logger = LoggerFactory.getLogger(CpuLoadBalance.class);

    private IRegistry iRegistry;

    private Map<String, GroupWeightConfig> groupWeights = new ConcurrentHashMap<>();

    private Random random = new Random();

    private List<Endpoint> endpoints = new ArrayList<>();

    public CpuLoadBalance(IRegistry iRegistry) {
        this.iRegistry = iRegistry;
    }

    @Override
    public Endpoint select(RpcInvocation invocation) {
        String serviceName = "provider";
        this.endpoints = iRegistry.getEndpoints(serviceName);
        this.endpoints.stream().forEach(this::addWeightConfig);
        return filter(serviceName);
    }

    public Endpoint filter(String serviceName) {
        double r = this.random.nextDouble();
        AtomicReference<String> routeId = new AtomicReference<>("");

        groupWeights.forEach((group, config) -> {
            if (!config.group.equals(serviceName)) {
                return;
            }
            List<Double> ranges = config.ranges;

            if (logger.isTraceEnabled()) {
                logger.trace("Weight for group: " + group + ", ranges: " + ranges + ", r: " + r);
            }

            for (int i = 0; i < ranges.size() - 1; i++) {
                if (r >= ranges.get(i) && r < ranges.get(i + 1)) {
                    routeId.set(config.rangeIndexes.get(i));
                    break;
                }
            }
        });
        //endpoint.getServiceName().equals(serviceName) &&
        return this.endpoints.stream().filter((endpoint) -> endpoint.getRouteId().equals(routeId.get())).findFirst().orElse(this.endpoints.get(0));
    }

    private void addWeightConfig(Endpoint endpoint) {
        String group = endpoint.getServiceName();
        GroupWeightConfig c = groupWeights.get(group);
        if (c == null) {
            c = new GroupWeightConfig(group);
            groupWeights.put(group, c);
        }
        GroupWeightConfig config = c;
        config.weights.put(endpoint.getRouteId(), Integer.valueOf(endpoint.getCpuLoad()));

        //recalculate

        // normalize weights
        int weightsSum = config.weights.values().stream().mapToInt(Integer::intValue).sum();

        final AtomicInteger index = new AtomicInteger(0);
        config.weights.forEach((routeId, weight) -> {
            Double nomalizedWeight = weight / (double) weightsSum;
            config.normalizedWeights.put(routeId, nomalizedWeight);

            // recalculate rangeIndexes
            config.rangeIndexes.put(index.getAndIncrement(), routeId);
        });

        //TODO: calculate ranges
        config.ranges.clear();

        config.ranges.add(0.0);

        List<Double> values = new ArrayList<>(config.normalizedWeights.values());
        for (int i = 0; i < values.size(); i++) {
            Double currentWeight = values.get(i);
            Double previousRange = config.ranges.get(i);
            Double range = previousRange + currentWeight;
            config.ranges.add(range);
        }

        if (logger.isTraceEnabled()) {
            logger.trace("Recalculated group weight config " + config);
        }
    }

    static class GroupWeightConfig {
        String group;

        LinkedHashMap<String, Integer> weights = new LinkedHashMap<>();

        LinkedHashMap<String, Double> normalizedWeights = new LinkedHashMap<>();

        LinkedHashMap<Integer, String> rangeIndexes = new LinkedHashMap<>();
        List<Double> ranges = new ArrayList<>();

        GroupWeightConfig(String group) {
            this.group = group;
        }

        @Override
        public String toString() {
            return new ToStringCreator(this)
                    .append("group", group)
                    .append("weights", weights)
                    .append("normalizedWeights", normalizedWeights)
                    .append("rangeIndexes", rangeIndexes)
                    .toString();
        }
    }

    /*    public static void main(String[] args) {
        CpuLoadBalance cpuLoadBalance = new CpuLoadBalance();
        List<Endpoint> endpoints = new ArrayList<>();
        Endpoint e1 = new Endpoint("1", 111, "123333", "12");
        Endpoint e2 = new Endpoint("1", 112, "1111", "1");
        Endpoint e3 = new Endpoint("1", 113, "444", "1");
        endpoints.add(e1);
        endpoints.add(e2);
        endpoints.add(e3);
        System.out.println(cpuLoadBalance.select(null, endpoints));
        System.out.println(cpuLoadBalance.groupWeights);
        System.out.println(cpuLoadBalance.filter(endpoints));
    }*/

}
