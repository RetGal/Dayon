package mpo.dayon.common.concurrent;

import mpo.dayon.common.log.Log;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;

public abstract class Executable extends RunnableEx
{
    private final ExecutorService executor;

    /**
     * Limiting access to an unbounded queue (!)
     */
    @Nullable
    private final Semaphore semaphore;

    public Executable(ExecutorService executor)
    {
        this.executor = executor;
        this.semaphore = null;
    }

    public Executable(ExecutorService executor, Semaphore semaphore)
    {
        this.executor = executor;
        this.semaphore = semaphore;
    }

    public final void doRun() throws Exception
    {
        try
        {
            if (semaphore != null)
            {
                semaphore.release();
            }

            execute();
        }
        catch (InterruptedException ex)
        {
            if (!executor.isShutdown()) // executor.shutdownNow() ...
            {
                throw ex;
            }

            Log.info(Thread.currentThread().getName() + " has cancelled a task (shutdown)!");
        }
    }

    protected abstract void execute() throws Exception;
}