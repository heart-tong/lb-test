package com.lovel.heart.myrule;

import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.AbstractLoadBalancerRule;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.Server;

import java.util.List;


/**
 * 轮序，每次选择的服务使用2次
 */
public class MyRule extends AbstractLoadBalancerRule {
    private int total = 0;
    private int index = 0;

    /**
     * Ribbon负载均衡真正起作用的就是这个方法
     *
     * @param lb
     * @param key
     * @return
     */
    @SuppressWarnings({"RCN_REDUNDANT_NULLCHECK_OF_NULL_VALUE"})
    public Server choose(ILoadBalancer lb, Object key) {
        if (lb == null) {
            return null;
        } else {
            Server server = null;

            while (server == null) {
                if (Thread.interrupted()) {
                    return null;
                }
                List<Server> upList = lb.getReachableServers();

                if (total < 2) {
                    server = upList.get(index);
                    total++;
                } else {
                    total = 0;
                    index++;
                    if (index >= upList.size()){
                        index = 0;
                    }
                }

                if (server == null) {
                    Thread.yield();
                } else {
                    if (server.isAlive()) {
                        return server;
                    }
                    server = null;
                    Thread.yield();
                }
            }
            return server;
        }
    }

    @Override
    public Server choose(Object key) {
        return this.choose(this.getLoadBalancer(), key);
    }

    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {
    }
}
