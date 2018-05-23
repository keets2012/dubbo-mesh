package com.alibaba.dubbo.performance.demo.agent.registry;

import com.alibaba.fastjson.JSON;
import com.coreos.jetcd.Client;
import com.coreos.jetcd.KV;
import com.coreos.jetcd.Lease;
import com.coreos.jetcd.data.ByteSequence;
import com.coreos.jetcd.kv.GetResponse;
import com.coreos.jetcd.options.GetOption;
import com.coreos.jetcd.options.PutOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.endpoint.SystemPublicMetrics;
import org.springframework.boot.actuate.metrics.Metric;

import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class EtcdRegistry implements IRegistry {
    private Logger logger = LoggerFactory.getLogger(EtcdRegistry.class);
    // 该EtcdRegistry没有使用etcd的Watch机制来监听etcd的事件
    // 添加watch，在本地内存缓存地址列表，可减少网络调用的次数
    // 使用的是简单的随机负载均衡，如果provider性能不一致，随机策略会影响性能

    private final String rootPath = "dubbomesh";
    private Lease lease;
    private KV kv;
    private long leaseId;
    private final AtomicLong fetchRegistryGeneration;
    private final AtomicReference<Map<String, List<Endpoint>>> localRegistry = new AtomicReference<>();

    private List<Endpoint> endpoints;
    private final SystemPublicMetrics systemPublicMetrics;

    public EtcdRegistry(String registryAddress, SystemPublicMetrics systemPublicMetrics) {
        this.systemPublicMetrics = systemPublicMetrics;
        fetchRegistryGeneration = new AtomicLong(0);
        Client client = Client.builder().endpoints(registryAddress).build();
        this.lease = client.getLeaseClient();
        this.kv = client.getKVClient();
        try {
            this.leaseId = lease.grant(30).get().getID();
        } catch (Exception e) {
            e.printStackTrace();
        }

        keepAlive();
        // 获取注册表的信息


        try {
            fetchRegistry(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
        // TODO 2018-05-23 建议后面使用watch机制，避免每次全量更新
        // 30s更新一次全量的本地注册表
        FetchRegistryTask fetchRegistryTask = new FetchRegistryTask(this, 30);
        fetchRegistryTask.start(30);
        String type = System.getProperty("type");   // 获取type参数
        if ("provider".equals(type)) {
            // 如果是provider，去etcd注册服务
            try {
                int port = Integer.valueOf(System.getProperty("server.port"));
                register("com.alibaba.dubbo.performance.demo.provider.IHelloService", port + 50);
                // 30s重新注册一次
                SystemInfoReplicator systemInfoReplicator = new SystemInfoReplicator(this, 30, "com.alibaba.dubbo.performance.demo.provider.IHelloService", port + 50);
                systemInfoReplicator.start(30);
                logger.info("provider-agent server register to etcd at port {}", port + 50);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    // 向ETCD中注册服务
    public void register(String serviceName, int port) throws Exception {
        // 服务注册的key为:    /dubbomesh/com.some.package.IHelloService/192.168.100.100:2000
        String strKey = MessageFormat.format("/{0}/{1}/{2}:{3}", rootPath, serviceName, IpHelper.getHostIp(), String.valueOf(port));
        ByteSequence key = ByteSequence.fromString(strKey);
        ByteSequence val = ByteSequence.fromString(getSystemLoad());     // 目前只需要创建这个key,对应的value暂不使用,先留空
        kv.put(key, val, PutOption.newBuilder().withLeaseId(leaseId).build()).get();
        logger.info("Register a new service at:" + strKey);
    }

    private String getSystemLoad() {
        Collection<Metric<?>> metrics = systemPublicMetrics.metrics();
        Optional<Metric<?>> freeMemoryMetric = metrics.stream()
                .filter(t -> "mem.free".equals(t.getName()))
                .findFirst();

        if (!freeMemoryMetric.isPresent()) {
            System.out.println("can not get memory");
        }
        long freeMemory = freeMemoryMetric.get()
                .getValue()
                .longValue();
        return String.valueOf(freeMemory);
    }

    // 发送心跳到ETCD,表明该host是活着的
    public void keepAlive() {
        Executors.newSingleThreadExecutor().submit(
                () -> {
                    try {
                        Lease.KeepAliveListener listener = lease.keepAlive(leaseId);
                        listener.listen();
                        logger.info("KeepAlive lease:" + leaseId + "; Hex format:" + Long.toHexString(leaseId));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
        );
    }

    public List<Endpoint> find(String serviceName) throws Exception {

        String strKey = MessageFormat.format("/{0}/{1}", rootPath, serviceName);
        ByteSequence key = ByteSequence.fromString(strKey);
        GetResponse response = kv.get(key, GetOption.newBuilder().withPrefix(key).build()).get();

        List<Endpoint> endpoints = new ArrayList<>();

        for (com.coreos.jetcd.data.KeyValue kv : response.getKvs()) {
            String s = kv.getKey().toStringUtf8();
            int index = s.lastIndexOf("/");
            String endpointStr = s.substring(index + 1, s.length());
            String host = endpointStr.split(":")[0];
            int port = Integer.valueOf(endpointStr.split(":")[1]);
            String cpu = kv.getValue().toStringUtf8();
            endpoints.add(new Endpoint(host, port, cpu, serviceName));
        }
        this.endpoints = endpoints;
        return endpoints;
    }

    @Override
    public List<Endpoint> getEndpoints() {
        return endpoints;
    }

    public boolean fetchRegistry(boolean forceFullRegistryFetch) throws Exception {
        // 全量拉取
        if(forceFullRegistryFetch){
            getAndStoreFullRegistry();
            logger.info("fetch full registry");
        // 增量式拉取
        }else{
            getAndUpdateDelta();
        }
        return true;
    }

    private void getAndStoreFullRegistry() throws Exception {
        long currentGeneration = fetchRegistryGeneration.get();
        String strKey = MessageFormat.format("/{0}", rootPath);
        ByteSequence key = ByteSequence.fromString(strKey);
        GetResponse response = kv.get(key, GetOption.newBuilder().withPrefix(key).build()).get();

        // TODO 2018-05-23 建议使用ConcurrentHashMap
        Map<String, List<Endpoint>> registry = new HashMap<>();
        for (com.coreos.jetcd.data.KeyValue kv : response.getKvs()) {
            String s = kv.getKey().toStringUtf8();
            String [] endpointInfos = s.split("/");
            String serviceName = endpointInfos[2];
            List<Endpoint> endpoints = registry.computeIfAbsent(serviceName, k -> new ArrayList<>());
            String endpointStr = endpointInfos[3];
            String host = endpointStr.split(":")[0];
            int port = Integer.valueOf(endpointStr.split(":")[1]);
            String cpu = kv.getValue().toStringUtf8();
            endpoints.add(new Endpoint(host, port, cpu, serviceName));
        }
        if(fetchRegistryGeneration.compareAndSet(currentGeneration, currentGeneration + 1)){
            localRegistry.set(registry);
        }
    }

    //TODO 2018-05-23 建议该方法使用watch监听注册表变化，实现增量式刷新
    private void getAndUpdateDelta(){

    }




}
