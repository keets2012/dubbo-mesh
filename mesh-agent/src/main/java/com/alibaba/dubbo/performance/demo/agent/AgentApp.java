package com.alibaba.dubbo.performance.demo.agent;

import com.alibaba.dubbo.performance.demo.agent.dubbo.agent.server.ProviderAgentServer;
import com.alibaba.dubbo.performance.demo.agent.dubbo.consumer.ConsumerAgentHttpServer;
import com.alibaba.dubbo.performance.demo.agent.registry.EtcdRegistry;
import com.alibaba.dubbo.performance.demo.agent.registry.IRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.endpoint.SystemPublicMetrics;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@SpringBootApplication
@Configuration
public class AgentApp {

    static final Logger logger = LoggerFactory.getLogger(AgentApp.class);

    // agent会作为sidecar，部署在每一个Provider和Consumer机器上
    // 在Provider端启动agent时，添加JVM参数
    // -Dtype=provider -Dserver.port=30000 -Ddubbo.protocol.port=20880 -Detcd.url=http://localhost:2379
    // 在Consumer端启动agent时，添加JVM参数
    // -Dtype=consumer -Dserver.port=20000 -Detcd.url=http://localhost:2379
    // 添加日志保存目录: -Dlogs.dir=/path/to/your/logs/dir。请安装自己的环境来设置日志目录。

    public static void main(String[] args) {
        SpringApplication.run(AgentApp.class, args);

        String type = System.getProperty("type");   // 获取type参数
        if ("provider".equals(type)) {
            new Thread(() -> new ProviderAgentServer().startServer()).start();
        }
        if ("consumer".equals(type)) {
            new Thread(() -> new ConsumerAgentHttpServer().startServer()).start();
        }

    }


    @Bean
    public IRegistry etcdRegistry(SystemPublicMetrics systemPublicMetrics) {
        return new EtcdRegistry(System.getProperty("etcd.url"), systemPublicMetrics);
    }
}
