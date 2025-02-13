package mpo.dayon.common.network;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public class Token {

    private final AtomicReference<String> tokenString = new AtomicReference<>();
    private final AtomicInteger peerPort = new AtomicInteger();
    private final AtomicReference<String> peerAddress = new AtomicReference<>();
    private final AtomicReference<String> peerLocalAddress = new AtomicReference<>();
    private final AtomicReference<Boolean> peerAccessible = new AtomicReference<>();
    private final AtomicInteger localPort = new AtomicInteger();
    private final String queryParams;
    private final ReentrantLock lock = new ReentrantLock();

    public Token(String tokenParams) {
        queryParams = tokenParams;
    }

    public void setTokenString(String tokenString) {
        lock.lock();
        try {
            reset(tokenString);
        } finally {
            lock.unlock();
        }
    }

    public void updateToken(String peerAddress, int peerPort, String peerLocalAddress, Boolean peerAccessible, int localPort) {
        lock.lock();
        try {
            this.peerPort.set(peerPort);
            this.peerAddress.set(peerAddress);
            this.peerLocalAddress.set(peerLocalAddress);
            this.peerAccessible.set(peerAccessible);
            this.localPort.set(localPort);
        } finally {
            lock.unlock();
        }
    }

    public String getTokenString() {
        lock.lock();
        try {
            return tokenString.get();
        } finally {
            lock.unlock();
        }
    }

    public int getPeerPort() {
        lock.lock();
        try {
            return peerPort.get();
        } finally {
            lock.unlock();
        }
    }

    public int getLocalPort() {
        lock.lock();
        try {
            return localPort.get();
        } finally {
            lock.unlock();
        }
    }

    public Boolean isPeerAccessible() {
        lock.lock();
        try {
            return peerAccessible.get();
        } finally {
            lock.unlock();
        }
    }

    public String getPeerAddress() {
        lock.lock();
        try {
            return peerAddress.get();
        } finally {
            lock.unlock();
        }
    }

    public String getPeerLocalAddress() {
        lock.lock();
        try {
            return peerLocalAddress.get();
        } finally {
            lock.unlock();
        }
    }

    public String getQueryParams() {
        return queryParams;
    }

    public void reset() {
        lock.lock();
        try {
            reset(null);
        } finally {
            lock.unlock();
        }
    }

    private void reset(String newToken) {
        tokenString.set(newToken);
        peerPort.set(0);
        peerAddress.set(null);
        peerLocalAddress.set(null);
        peerAccessible.set(null);
        localPort.set(0);
    }

    @Override
    public String toString() {
        lock.lock();
        try {
            return "Token " + tokenString + " [peerPort=" + peerPort + ", peerAddress=" + peerAddress + ", peerAccessible=" + peerAccessible + ", peerLocalAddress=" + peerLocalAddress + ", localPort=" + localPort + "]";
        } finally {
            lock.unlock();
        }
    }
}
