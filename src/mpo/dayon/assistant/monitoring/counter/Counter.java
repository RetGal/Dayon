package mpo.dayon.assistant.monitoring.counter;

import mpo.dayon.assistant.monitoring.BigBrother;
import mpo.dayon.common.event.Listeners;

public abstract class Counter<T>
{
    private final Listeners<CounterListener<T>> listeners = new Listeners<>(CounterListener.class);

    private final String uid;

    private final String shortDescription;

    private long totalStart = -1;

    long instantStart = -1;

    Counter(String uid, String shortDescription)
    {
        this.uid = uid;
        this.shortDescription = shortDescription;
    }

    public void addListener(CounterListener<T> listener)
    {
        listeners.add(listener);
    }

    public void removeListener(CounterListener<T> listener)
    {
        listeners.remove(listener);
    }

    public String getUid()
    {
        return uid;
    }

    public String getShortDescription()
    {
        return shortDescription;
    }

    /**
     * Setup the starting time of this counter.
     *
     * @see #start(long)
     */
    private void initialize()
    {
        synchronized (this)
        {
            this.totalStart = this.instantStart = System.currentTimeMillis();
        }
    }

    /**
     * Initializes that counter and registers it to the {@link BigBrother}.
     *
     * @param instantPeriod millis
     */
    public void start(long instantPeriod)
    {
        initialize();
        BigBrother.get().registerCounter(this, instantPeriod);
    }

    public abstract void computeAndResetInstantValue();

    public abstract String formatInstantValue(T value);

    public abstract int getWidth();

    void fireOnInstantValueUpdated(T value)
    {
        final CounterListener<T>[] xlisteners = listeners.getListeners();

        if (xlisteners == null)
        {
            return;
        }

        for (final CounterListener<T> xlistener : xlisteners) {
            xlistener.onInstantValueUpdated(this, value);
        }
    }
}
