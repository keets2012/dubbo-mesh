package com.alibaba.dubbo.performance.demo.agent.dubbo.agent.consumer;

import com.alibaba.dubbo.performance.demo.agent.cluster.loadbalance.LoadBalance;
import com.alibaba.dubbo.performance.demo.agent.cluster.loadbalance.WeightRoundRobinLoadBalance;
import com.alibaba.dubbo.performance.demo.agent.dubbo.codec.DubboRpcBatchDecoder;
import com.alibaba.dubbo.performance.demo.agent.dubbo.codec.DubboRpcDecoder;
import com.alibaba.dubbo.performance.demo.agent.dubbo.codec.DubboRpcEncoder;
import com.alibaba.dubbo.performance.demo.agent.registry.EndpointHolder;
import com.alibaba.dubbo.performance.demo.agent.rpc.Endpoint;
import com.alibaba.dubbo.performance.demo.agent.transport.Client;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author 徐靖峰
 * Date 2018-05-31
 */
public class NormalClient implements Client {

    private Map<Endpoint,Channel> channelMap = new HashMap<>(3);
    private LoadBalance loadBalance;
    private volatile boolean available = false;
    private NioEventLoopGroup workerGroup = new NioEventLoopGroup();

    @Override
    public Channel getChannel(){
        if(available){
            return  channelMap.get(loadBalance.select());
        }
        throw new RuntimeException("client不可用");
    }

    @Override
    public void init() {
        this.loadBalance = new WeightRoundRobinLoadBalance();
        List<Endpoint> endpoints = EndpointHolder.getEndpoints();
        System.out.println(endpoints);
        this.loadBalance.onRefresh(endpoints);
        for (Endpoint endpoint : endpoints) {
            Channel channel = connect(endpoint);
            channelMap.put(endpoint, channel);
        }
        available = true;
    }

    private Channel connect(Endpoint endpoint){
        Bootstrap b = new Bootstrap();
        b.group(workerGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline()
                                .addLast(new DubboRpcEncoder())
                                .addLast(new DubboRpcBatchDecoder())
                                .addLast(new ConsumerAgentBatchHandler());
                    }
                });
        ChannelFuture f = b.connect(endpoint.getHost(), endpoint.getPort());
        return f.channel();
    }
}
