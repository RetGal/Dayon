package mpo.dayon.assistant.network.https;

import mpo.dayon.common.log.Log;
import mpo.dayon.common.utils.SystemUtilities;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.ssl.SslSocketConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

public class NetworkAssistantHttpsEngine
{
    private final int port;

    private final Server server;

    private final MySocketConnector acceptor;

    private final MyHttpHandler handler;


    public NetworkAssistantHttpsEngine(int port) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException
    {
        this.port = port;

        this.server = new Server();
        this.server.setSendServerVersion(false);
               
        SslContextFactory contextFactory = new SslContextFactory(true);
               
        // this looks like fun, doesn't it?!?
        // contextFactory.setKeyStorePath() would be easier, but it can't handle paths from within the jar..
        // ..and contextFactory.setKeyStoreInputStream() is deprecated
        final String keyStorePath = "/mpo/dayon/common/security/X509";
        final String keyStorePass = "spasspass";
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());    
        keyStore.load(NetworkAssistantHttpsEngine.class.getResourceAsStream(keyStorePath), keyStorePass.toCharArray());
        
        contextFactory.setKeyStore(keyStore);
        contextFactory.setKeyStorePassword(keyStorePass);
        this.acceptor = new MySocketConnector(contextFactory);

        this.server.setConnectors(new Connector[]{this.acceptor});

        final HandlerList httpHandlers = new HandlerList();
        {
            final File jnlp = SystemUtilities.getOrCreateAppDirectory("jnlp");
            if (jnlp == null)
            {
                throw new RuntimeException("No JNLP directory!");
            }

            httpHandlers.addHandler(handler = new MyHttpHandler(jnlp.getAbsolutePath()));
        }

        this.server.setHandler(httpHandlers);
    }

    public void start() throws IOException
    {
        Log.info("[HTTPS] The engine is starting...");

        try
        {
            server.start();
        }
        catch (Exception ex)
        {
            if (ex instanceof IOException)
            {
                throw (IOException) ex;
            }

            throw new RuntimeException(ex); // dunno (!)
        }

        Log.info("[HTTPS] The engine is waiting on its acceptor...");

        synchronized (acceptor.__acceptLOCK)
        {
            while (!acceptor.__acceptStopped)
            {
                try
                {
                    acceptor.__acceptLOCK.wait();
                }
                catch (InterruptedException ignored)
                {
                }
            }
        }

        Log.info("[HTTPS] The engine is done - bye!");
    }

    public void cancel()
    {
        try
        {
            server.stop();
        }
        catch (Exception ex)
        {
            Log.warn("[HTTPS] Exception while closing Jetty!", ex);
        }
    }

    public void onDayonAccepting()
    {
        Log.info("[HTTPS] engine.onDayonAccepting() received");

        synchronized (handler.__dayonLOCK)
        {
            handler.__dayonStarted = true;
            handler.__dayonLOCK.notifyAll();
        }
    }

    private class MySocketConnector extends SslSocketConnector
    {
        private final Object __acceptLOCK = new Object();

        private boolean __acceptClosed;

        private boolean __acceptStopped;

        public MySocketConnector(SslContextFactory contextFactory)
        {
            super(contextFactory);
            setPort(port);
        }

        @Override
        public void accept(int acceptorID) throws IOException, InterruptedException
        {
            try
            {
                Log.info("[HTTPS] The engine acceptor [" + acceptorID + "] is accepting...");

                super.accept(acceptorID);

            }
            finally
            {
                Log.info("[HTTPS] The engine acceptor has accepted.");

                synchronized (__acceptLOCK)
                {
                    if (__acceptClosed)
                    {
                        Log.info("[HTTPS] The engine acceptor is stopping...");

                        __acceptStopped = true;
                        __acceptLOCK.notifyAll();
                    }
                }
            }
        }
        
    	@Override
    	public void close() throws IOException {
    		synchronized (__acceptLOCK) {
    			__acceptClosed = true;
    		}

    		super.close();
    	}

    }

    /**
     * Serving all the static (once this engine configured) resources required for JAVA WEB start.
     */
    private class MyHttpHandler extends ResourceHandler
    {
        private final Object __dayonLOCK = new Object();

        private boolean __dayonStarted;

        public MyHttpHandler(String root)
        {
            setResourceBase(root);
        }

        public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
        {
            Log.info("[HTTPS] Processing the request \n-----\n" + request + "\n-----");

            if (target.contains("/hello"))
            {
                Log.info("[HTTPS] The handler is processing the /hello request");

                // That keeps all the connections open => then I can reply to this request ...
                acceptor.close();

                // Wait for the start of the Dayon! acceptor before replying to this HTTP request (I want to ensure
                // we're now ready to receive a Dayon! message coming from the assisted side).

                Log.info("[HTTPS] The handler is waiting on Dayon! server start...");

                synchronized (__dayonLOCK)
                {
                    while (!__dayonStarted)
                    {
                        try
                        {
                            __dayonLOCK.wait();
                        }
                        catch (InterruptedException ignored)
                        {
                        }
                    }
                }

                // Currently do not care about the actual response (!)
                Log.info("[HTTPS] The handler is replying to the /hello message [404]...");
            }

            super.handle(target, baseRequest, request, response);

            Log.info("[HTTPS] Response \n-----\n" + response + "\n-----");
        }

    }


}
