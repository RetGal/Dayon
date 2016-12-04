package mpo.dayon.common.concurrent;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class DefaultThreadFactoryEx implements ThreadFactory
{
    private final AtomicInteger threadNumber = new AtomicInteger(1);

    private final ThreadGroup group;

    private final String namePrefix;

    public DefaultThreadFactoryEx(String name)
    {
        final SecurityManager sm = System.getSecurityManager();

        group = (sm != null) ? sm.getThreadGroup() : Thread.currentThread().getThreadGroup();
        namePrefix = name + "-";
    }

    public Thread newThread(Runnable runnable)
    {
        final Thread thread = new Thread(group, runnable, namePrefix + threadNumber.getAndIncrement(), 0);

        if (thread.isDaemon())
        {
            thread.setDaemon(false);
        }

        if (thread.getPriority() != Thread.NORM_PRIORITY)
        {
            thread.setPriority(Thread.NORM_PRIORITY);
        }

        return thread;
    }

}
