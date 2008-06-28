/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.apache.mina.transport.socket.nio;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.Executor;

import org.apache.mina.core.polling.AbstractPollingIoConnector;
import org.apache.mina.core.service.IoConnector;
import org.apache.mina.core.service.IoProcessor;
import org.apache.mina.core.service.SimpleIoProcessorPool;
import org.apache.mina.core.service.TransportMetadata;
import org.apache.mina.transport.socket.DefaultSocketSessionConfig;
import org.apache.mina.transport.socket.SocketConnector;
import org.apache.mina.transport.socket.SocketSessionConfig;

/**
 * {@link IoConnector} for socket transport (TCP/IP).
 *
 * @author The Apache MINA Project (dev@mina.apache.org)
 * @version $Rev: 389042 $, $Date: 2006-03-27 07:49:41Z $
 */
public final class NioSocketConnector
        extends AbstractPollingIoConnector<NioSession, SocketChannel>
        implements SocketConnector {

    private volatile Selector selector;

    /**
     * Constructor for {@link NioSocketConnector} with default configuration.
     */
    public NioSocketConnector() {
        super(new DefaultSocketSessionConfig(), NioProcessor.class);
        ((DefaultSocketSessionConfig) getSessionConfig()).init(this);
    }

    /**
     * Constructor for {@link NioSocketConnector} with default configuration, and 
     * given number of {@link NioProcessor}
     * @param processorCount the number of processor to create and place in a
     * {@link SimpleIoProcessorPool} 
     */
    public NioSocketConnector(int processorCount) {
        super(new DefaultSocketSessionConfig(), NioProcessor.class, processorCount);
        ((DefaultSocketSessionConfig) getSessionConfig()).init(this);
    }

    /**
     *  Constructor for {@link NioSocketConnector} with default configuration but a
     *  specific {@link IoProcessor}
     * @param processor the processor to use for managing I/O events
     */
    public NioSocketConnector(IoProcessor<NioSession> processor) {
        super(new DefaultSocketSessionConfig(), processor);
        ((DefaultSocketSessionConfig) getSessionConfig()).init(this);
    }

    /**
     *  Constructor for {@link NioSocketConnector} with a given {@link Executor} for handling 
     *  connection events and a given {@link IoProcessor} for handling I/O events.
     * @param executor the executor for connection
     * @param processor the processor for I/O operations
     */
    public NioSocketConnector(Executor executor, IoProcessor<NioSession> processor) {
        super(new DefaultSocketSessionConfig(), executor, processor);
        ((DefaultSocketSessionConfig) getSessionConfig()).init(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void init() throws Exception {
        this.selector = Selector.open();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void destroy() throws Exception {
        if (selector != null) {
            selector.close();
        }
    }

    /**
     * {@inheritDoc}
     */
    public TransportMetadata getTransportMetadata() {
        return NioSocketSession.METADATA;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SocketSessionConfig getSessionConfig() {
        return (SocketSessionConfig) super.getSessionConfig();
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public InetSocketAddress getDefaultRemoteAddress() {
        return (InetSocketAddress) super.getDefaultRemoteAddress();
    }
    
    /**
     * {@inheritDoc}
     */
    public void setDefaultRemoteAddress(InetSocketAddress defaultRemoteAddress) {
        super.setDefaultRemoteAddress(defaultRemoteAddress);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Iterator<SocketChannel> allHandles() {
        return new SocketChannelIterator(selector.keys());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean connect(SocketChannel handle, SocketAddress remoteAddress)
            throws Exception {
        return handle.connect(remoteAddress);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ConnectionRequest connectionRequest(SocketChannel handle) {
        SelectionKey key = handle.keyFor(selector);
        if (key == null) {
            return null;
        }

        return (ConnectionRequest) key.attachment();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void close(SocketChannel handle) throws Exception {
        SelectionKey key = handle.keyFor(selector);
        if (key != null) {
            key.cancel();
        }
        handle.close();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean finishConnect(SocketChannel handle) throws Exception {
        SelectionKey key = handle.keyFor(selector);
        if (handle.finishConnect()) {
            if (key != null) {
                key.cancel();
            }
            return true;
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected SocketChannel newHandle(SocketAddress localAddress)
            throws Exception {
        SocketChannel ch = SocketChannel.open();

        int receiveBufferSize =
            (getSessionConfig()).getReceiveBufferSize();
        if (receiveBufferSize > 65535) {
            ch.socket().setReceiveBufferSize(receiveBufferSize);
        }

        if (localAddress != null) {
            ch.socket().bind(localAddress);
        }
        ch.configureBlocking(false);
        return ch;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected NioSession newSession(IoProcessor<NioSession> processor, SocketChannel handle) {
        return new NioSocketSession(this, processor, handle);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void register(SocketChannel handle, ConnectionRequest request)
            throws Exception {
        handle.register(selector, SelectionKey.OP_CONNECT, request);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean select(int timeout) throws Exception {
        return selector.select(timeout) > 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Iterator<SocketChannel> selectedHandles() {
        return new SocketChannelIterator(selector.selectedKeys());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void wakeup() {
        selector.wakeup();
    }

    private static class SocketChannelIterator implements Iterator<SocketChannel> {

        private final Iterator<SelectionKey> i;

        private SocketChannelIterator(Collection<SelectionKey> selectedKeys) {
            this.i = selectedKeys.iterator();
        }

        /**
         * {@inheritDoc}
         */
        public boolean hasNext() {
            return i.hasNext();
        }

        /**
         * {@inheritDoc}
         */
        public SocketChannel next() {
            SelectionKey key = i.next();
            return (SocketChannel) key.channel();
        }

        /**
         * {@inheritDoc}
         */
        public void remove() {
            i.remove();
        }
    }
}
