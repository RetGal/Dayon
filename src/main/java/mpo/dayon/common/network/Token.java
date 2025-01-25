package mpo.dayon.common.network;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public class Token {

    private final AtomicReference<String> tokenString = new AtomicReference<>();
    private final AtomicInteger port = new AtomicInteger();
    private final AtomicReference<String> peerAddress = new AtomicReference<>();
    private final AtomicReference<Boolean> peerAccessible = new AtomicReference<>();
    private final ReentrantLock lock = new ReentrantLock();

    public void setTokenString(String tokenString) {
        lock.lock();
        try {
            reset(tokenString);
        } finally {
            lock.unlock();
        }
    }

    public void updateToken(String peerAddress, int port, Boolean peerAccessible) {
        lock.lock();
        try {
            this.port.set(port);
            this.peerAddress.set(peerAddress);
            this.peerAccessible.set(peerAccessible);
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

    public void setPort(int port) {
        lock.lock();
        try {
            this.port.set(port);
        } finally {
            lock.unlock();
        }
    }

    public int getPort() {
        lock.lock();
        try {
            return port.get();
        } finally {
            lock.unlock();
        }
    }

    public void setPeerAccessible(Boolean is) {
        lock.lock();
        try {
            this.peerAccessible.set(is);
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

    public void setPeerAddress(String peerAddress) {
        lock.lock();
        try {
            this.peerAddress.set(peerAddress);
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
        port.set(0);
        peerAddress.set(null);
        peerAccessible.set(false);
    }

    @Override
    public String toString() {
        lock.lock();
        try {
            return "Token [token=" + tokenString + ", port=" + port + ", peerAddress=" + peerAddress + ", peerAccessible=" + peerAccessible + "]";
        } finally {
            lock.unlock();
        }
    }
}
