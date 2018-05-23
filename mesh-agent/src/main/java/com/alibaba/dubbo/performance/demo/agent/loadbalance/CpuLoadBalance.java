package com.alibaba.dubbo.performance.demo.agent.loadbalance;

import com.alibaba.dubbo.performance.demo.agent.dubbo.model.RpcInvocation;
import com.alibaba.dubbo.performance.demo.agent.registry.Endpoint;

/**
 * @author keets
 * @data 2018/5/22.
 */
public class CpuLoadBalance implements LoadBalance {

    @Override
    public Endpoint select(RpcInvocation invocation) {

        return null;
    }
}
