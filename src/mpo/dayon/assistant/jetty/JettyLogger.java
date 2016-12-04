package mpo.dayon.assistant.jetty;

import mpo.dayon.common.log.Log;
import org.mortbay.log.Logger;

public class JettyLogger implements Logger
{
    public Logger getLogger(String name)
    {
        return this;
    }

    public boolean isDebugEnabled()
    {
        return Log.isDebugEnabled();
    }

    public void setDebugEnabled(boolean enabled)
    {
    }

    public void debug(String message, Throwable error)
    {
        if (isDebugEnabled())
        {
            Log.debug("[JETTY] " + message, error);
        }
    }

    public void debug(String message, Object arg0, Object arg1)
    {
        if (isDebugEnabled())
        {
            Log.debug("[JETTY] " + format(message, arg0, arg1));
        }
    }

    public void info(String message, Object arg0, Object arg1)
    {
        Log.info("[JETTY] " + format(message, arg0, arg1));
    }

    public void warn(String message, Object arg0, Object arg1)
    {
        Log.warn("[JETTY] " + format(message, arg0, arg1));
    }

    public void warn(String message, Throwable error)
    {
        Log.warn("[JETTY] " + message, error);
    }

    private String format(String message, Object arg0, Object arg1)
    {
        int i0 = message.indexOf("{}");
        int i1 = i0 < 0 ? -1 : message.indexOf("{}", i0 + 2);

        if (arg1 != null && i1 >= 0)
        {
            message = message.substring(0, i1) + arg1 + message.substring(i1 + 2);
        }
        if (arg0 != null && i0 >= 0)
        {
            message = message.substring(0, i0) + arg0 + message.substring(i0 + 2);
        }
        return message;
    }


}
