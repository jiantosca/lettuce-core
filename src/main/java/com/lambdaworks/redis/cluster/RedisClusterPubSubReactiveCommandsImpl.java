/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lambdaworks.redis.cluster;

import static com.lambdaworks.redis.cluster.NodeSelectionInvocationHandler.ExecutionModel.REACTIVE;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.lambdaworks.redis.RedisURI;
import com.lambdaworks.redis.cluster.api.NodeSelectionSupport;
import com.lambdaworks.redis.cluster.models.partitions.RedisClusterNode;
import com.lambdaworks.redis.cluster.pubsub.StatefulRedisClusterPubSubConnection;
import com.lambdaworks.redis.cluster.pubsub.api.rx.NodeSelectionPubSubReactiveCommands;
import com.lambdaworks.redis.cluster.pubsub.api.rx.PubSubReactiveNodeSelection;
import com.lambdaworks.redis.cluster.pubsub.api.rx.RedisClusterPubSubReactiveCommands;
import com.lambdaworks.redis.codec.RedisCodec;
import com.lambdaworks.redis.pubsub.RedisPubSubReactiveCommandsImpl;
import com.lambdaworks.redis.pubsub.StatefulRedisPubSubConnection;
import com.lambdaworks.redis.pubsub.api.rx.RedisPubSubReactiveCommands;

/**
 * @author Mark Paluch
 */
class RedisClusterPubSubReactiveCommandsImpl<K, V> extends RedisPubSubReactiveCommandsImpl<K, V>
        implements RedisClusterPubSubReactiveCommands<K, V> {

    /**
     * Initialize a new connection.
     *
     * @param connection the connection .
     * @param codec Codec used to encode/decode keys and values.
     */
    public RedisClusterPubSubReactiveCommandsImpl(StatefulRedisClusterPubSubConnection<K, V> connection,
            RedisCodec<K, V> codec) {
        super(connection, codec);
    }

    @Override
    public StatefulRedisClusterPubSubConnection<K, V> getStatefulConnection() {
        return (StatefulRedisClusterPubSubConnection<K, V>) super.getStatefulConnection();
    }

    @SuppressWarnings("unchecked")
    @Override
    public PubSubReactiveNodeSelection<K, V> nodes(Predicate<RedisClusterNode> predicate) {

        PubSubReactiveNodeSelection<K, V> selection = new StaticPubSubReactiveNodeSelection<K, V>(getStatefulConnection(),
                predicate);

        NodeSelectionInvocationHandler h = new NodeSelectionInvocationHandler((AbstractNodeSelection<?, ?, ?, ?>) selection,
                RedisPubSubReactiveCommands.class, REACTIVE);
        return (PubSubReactiveNodeSelection<K, V>) Proxy.newProxyInstance(NodeSelectionSupport.class.getClassLoader(),
                new Class<?>[] { NodeSelectionPubSubReactiveCommands.class, PubSubReactiveNodeSelection.class }, h);
    }

    private static class StaticPubSubReactiveNodeSelection<K, V>
            extends AbstractNodeSelection<RedisPubSubReactiveCommands<K, V>, NodeSelectionPubSubReactiveCommands<K, V>, K, V>
            implements PubSubReactiveNodeSelection<K, V> {

        private final List<RedisClusterNode> redisClusterNodes;
        private final ClusterDistributionChannelWriter<K, V> writer;

        @SuppressWarnings("unchecked")
        public StaticPubSubReactiveNodeSelection(StatefulRedisClusterPubSubConnection<K, V> globalConnection,
                Predicate<RedisClusterNode> selector) {

            this.redisClusterNodes = globalConnection.getPartitions().getPartitions().stream().filter(selector)
                    .collect(Collectors.toList());
            writer = ((StatefulRedisClusterPubSubConnectionImpl) globalConnection).getClusterDistributionChannelWriter();
        }

        @Override
        protected RedisPubSubReactiveCommands<K, V> getApi(RedisClusterNode redisClusterNode) {
            return getConnection(redisClusterNode).reactive();
        }

        protected List<RedisClusterNode> nodes() {
            return redisClusterNodes;
        }

        @SuppressWarnings("unchecked")
        protected StatefulRedisPubSubConnection<K, V> getConnection(RedisClusterNode redisClusterNode) {
            RedisURI uri = redisClusterNode.getUri();
            return (StatefulRedisPubSubConnection<K, V>) writer.getClusterConnectionProvider()
                    .getConnection(ClusterConnectionProvider.Intent.WRITE, uri.getHost(), uri.getPort());
        }
    }
}
