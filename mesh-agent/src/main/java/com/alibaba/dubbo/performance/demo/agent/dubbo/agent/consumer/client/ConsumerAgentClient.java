package com.alibaba.dubbo.performance.demo.agent.dubbo.agent.consumer.client;

import com.alibaba.dubbo.performance.demo.agent.cluster.loadbalance.LoadBalance;
import com.alibaba.dubbo.performance.demo.agent.cluster.loadbalance.WeightRoundRobinLoadBalance;
import com.alibaba.dubbo.performance.demo.agent.protocol.pb.DubboMeshProto;
import com.alibaba.dubbo.performance.demo.agent.registry.EndpointHolder;
import com.alibaba.dubbo.performance.demo.agent.rpc.Endpoint;
import com.alibaba.dubbo.performance.demo.agent.transport.Client;
import com.alibaba.dubbo.performance.demo.agent.transport.MeshChannel;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author 徐靖峰
 * Date 2018-05-31
 */
public class ConsumerAgentClient implements Client {

    private static Map<String, ConsumerAgentClient> threadBoundClientMap = new HashMap<>(8);

    public static void put(String eventLoopName, ConsumerAgentClient consumerAgentClient) {
        threadBoundClientMap.put(eventLoopName, consumerAgentClient);
    }

    public static ConsumerAgentClient get(String eventLoopName) {
        return threadBoundClientMap.get(eventLoopName);
    }

    private Map<Endpoint, MeshChannel> channelMap = new HashMap<>(3);
    private LoadBalance loadBalance;
    private volatile boolean available = false;
    private EventLoop sharedEventLoop;

    public ConsumerAgentClient(EventLoop sharedEventLoop) {
        this.sharedEventLoop = sharedEventLoop;
    }

    @Override
    public MeshChannel getMeshChannel() {
        if (available) {
            Endpoint selectEndpoint = loadBalance.select();
            return channelMap.get(selectEndpoint);
        }
        throw new RuntimeException("client不可用");
    }

    @Override
    public MeshChannel getMeshChannel(Endpoint endpoint) {
        if (available) {
            return channelMap.get(endpoint);
        }
        throw new RuntimeException("client不可用");

    }

    @Override
    public void init() {
        this.loadBalance = new WeightRoundRobinLoadBalance();
        List<Endpoint> endpoints = EndpointHolder.getEndpoints();
        this.loadBalance.onRefresh(endpoints);
        for (Endpoint endpoint : endpoints) {
            Channel channel = connect(endpoint);
            MeshChannel meshChannel = new MeshChannel();
            meshChannel.setEndpoint(endpoint);
            meshChannel.setChannel(channel);
            channelMap.put(endpoint, meshChannel);
        }
        available = true;
    }

    private Channel connect(Endpoint endpoint) {
        Bootstrap b = new Bootstrap();
        b.group(sharedEventLoop)//复用sharedEventLoop就发不出去请求
                .channel(Epoll.isAvailable() ? EpollSocketChannel.class : NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline()
                                .addLast("protobufVarint32FrameDecoder", new ProtobufVarint32FrameDecoder())
                                .addLast("protobufDecoder", new ProtobufDecoder(DubboMeshProto.AgentResponse.getDefaultInstance()))
                                .addLast("protobufVarint32LengthFieldPrepender", new ProtobufVarint32LengthFieldPrepender())
                                .addLast("protobufEncodeConsumerAgentHttpServerHandlerr", new ProtobufEncoder())
                                .addLast(new ConsumerAgentClientHandler());
                    }
                });
        ChannelFuture f = b.connect(endpoint.getHost(), endpoint.getPort());
        return f.channel();
    }


}
