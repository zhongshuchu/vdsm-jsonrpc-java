package org.ovirt.vdsm.jsonrpc.client.reactors;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ovirt.vdsm.jsonrpc.client.ClientConnectionException;
import org.ovirt.vdsm.jsonrpc.client.utils.ReactorScheduler;

/**
 * Provides <code>Reactor</code> abstraction which reacts on
 * incoming messages and let <code>ReactorClient</code> process
 * them.
 *
 */
public abstract class Reactor extends Thread {
    private static Log log = LogFactory.getLog(Reactor.class);
    private static final int TIMEOUT = 1000;
    private final AbstractSelector selector;
    private final ReactorScheduler scheduler;
    private boolean isRunning;

    public Reactor() throws IOException {
        this.selector = SelectorProvider.provider().openSelector();
        this.scheduler = new ReactorScheduler();
        this.isRunning = false;
        setName(getReactorName());
        setDaemon(true);
        start();
    }

    private void select() {
        try {
            this.selector.select(TIMEOUT);
        } catch (IOException e) {
            log.error("IOException occured", e);
        }
    }

    /**
     * Main loop for message processing.
     */
    public void run() {
        this.isRunning = true;
        while (this.isRunning) {
            select();
            this.scheduler.performPendingOperations();
            processChannels();
        }
    }

    /**
     * Processing channels.
     */
    private void processChannels() {
        for (final SelectionKey key : this.selector.selectedKeys()) {
            if (!key.isValid()) {
                continue;
            }

            if (key.isAcceptable()) {
                final NioListener obj = (NioListener) key.attachment();
                final ReactorClient client = obj.accept();
                if (client == null) {
                    continue;
                }
            }

            if (key.isReadable() || key.isWritable()) {
                final ReactorClient client = (ReactorClient) key.attachment();
                try {
                    client.process();
                } catch (IOException | ClientConnectionException ex) {
                    log.error("Unable to process messages", ex);
                    client.close();
                }
            }

            if (!key.channel().isOpen()) {
                key.cancel();
            }
        }
    }

    public void queueFuture(Future<?> f) {
        this.scheduler.queueFuture(f);
        wakeup();
    }

    public void wakeup() {
        this.selector.wakeup();
    }

    public Future<ReactorListener> createListener(final String hostname,
            final int port,
            final ReactorListener.EventListener owner) {
        final Reactor reactor = this;
        final FutureTask<ReactorListener> task = new FutureTask<>(
                new Callable<ReactorListener>() {
                    @Override
                    public ReactorListener call() throws IOException {
                        InetAddress address = InetAddress.getByName(hostname);
                        return new NioListener(
                                reactor,
                                new InetSocketAddress(address, port),
                                selector, owner);
                    }
                });
        queueFuture(task);
        return task;
    }

    public ReactorClient createClient(String hostname, int port) throws ClientConnectionException {
        return createClient(this, this.selector, hostname, port);
    }

    public ReactorClient createConnectedClient(String hostName, int port, SocketChannel channel)
            throws ClientConnectionException {
        return createConnectedClient(this, this.selector, hostName, port, channel);
    }

    public void close() throws IOException {
        this.isRunning = false;
        wakeup();
    }

    public abstract ReactorClient createClient(Reactor reactor, Selector selector, String hostname, int port)
            throws ClientConnectionException;

    public abstract ReactorClient createConnectedClient(Reactor reactor, Selector selector, String hostname,
            int port, SocketChannel channel) throws ClientConnectionException;

    public abstract String getReactorName();

}