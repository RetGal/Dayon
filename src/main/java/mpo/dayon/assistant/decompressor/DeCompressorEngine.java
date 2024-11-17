package mpo.dayon.assistant.decompressor;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import mpo.dayon.common.compressor.CompressorEngineConfiguration;
import mpo.dayon.common.capture.Capture;
import mpo.dayon.common.concurrent.DefaultThreadFactoryEx;
import mpo.dayon.common.concurrent.Executable;
import mpo.dayon.common.error.FatalErrorHandler;
import mpo.dayon.common.event.Listeners;
import mpo.dayon.common.log.Log;
import mpo.dayon.common.network.message.NetworkCaptureMessage;
import mpo.dayon.common.network.message.NetworkCaptureMessageHandler;
import mpo.dayon.common.squeeze.Compressor;
import mpo.dayon.common.squeeze.NullTileCache;
import mpo.dayon.common.squeeze.RegularTileCache;
import mpo.dayon.common.squeeze.TileCache;

public class DeCompressorEngine implements NetworkCaptureMessageHandler {
	private final Listeners<DeCompressorEngineListener> listeners = new Listeners<>();

	private ThreadPoolExecutor executor;

	private Semaphore semaphore;

	private TileCache cache;

	public DeCompressorEngine(DeCompressorEngineListener listener) {
		listeners.add(listener);
	}

	public void start(int queueSize) {
		// THREAD = 1
		//
		// The parallel processing is within the de-compressor itself - here we
		// want
		// to ensure a certain order of processing - if need more than one
		// thread then
		// have a look how the de-compressed data are sent to the GUI (!)

		executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());

		executor.setThreadFactory(new DefaultThreadFactoryEx("DeCompressorEngine"));

		// Rejection Policy
		//
		// Blocking pattern when queue full; that means we're not decompressing
		// fast enough; when our queue is full
		// then the network receiving thread is going to stop reading from the
		// assisted side which in turn is going
		// to slow down sending its capture leaving us some time to catch up.
		//
		// Having our queue full is quite unlikely; I would say the network will
		// limit the number of capture/tiles
		// being sent and I guess that decompressing is much faster then
		// compressing (unless our PC is quite weak
		// compared to the assisted one; let's not forget the JAVA capture is
		// awful regarding the performance as
		// well => should be fine here.

		semaphore = new Semaphore(queueSize, true);
	}

	/**
	 * Should not block as called from the network incoming message thread (!)
	 */
	@Override
	public void handleCapture(NetworkCaptureMessage capture) {
		try {
			semaphore.acquire();
			executor.execute(new MyExecutable(executor, semaphore, capture));
		} catch (InterruptedException ex) {
			FatalErrorHandler.bye("The [" + Thread.currentThread().getName() + "] thread is has been interrupted!", ex);
			Thread.currentThread().interrupt();
		} catch (RejectedExecutionException ex) {
			semaphore.release(); // unlikely as we have an unbounded queue
			// (!)
		}
	}

	private class MyExecutable extends Executable {
		private final NetworkCaptureMessage message;

		MyExecutable(ExecutorService executor, Semaphore semaphore, NetworkCaptureMessage message) {
			super(executor, semaphore);

			this.message = message;
		}

		@Override
		protected void execute() throws IOException {
			try {
				final Compressor compressor = Compressor.get(message.getCompressionMethod());

				final CompressorEngineConfiguration configuration = message.getCompressionConfiguration();
				if (configuration != null) {
					cache = configuration.useCache() ? new RegularTileCache(configuration.getCacheMaxSize(), configuration.getCachePurgeSize())
							: new NullTileCache();

					Log.info("De-Compressor engine has been reconfigured [tile:" + message.getId() + "]" + configuration);
				}

				cache.clearHits();

				final Capture capture = compressor.decompress(cache, message.getPayload());
				final double ratio = capture
						.computeCompressionRatio(1/* magic-number */ + message.getWireSize());

				fireOnDeCompressed(capture, cache.getHits(), ratio);
			} finally {
				if (cache != null) {
					cache.onCaptureProcessed();
				}
			}
		}

		private void fireOnDeCompressed(Capture capture, int cacheHits, double compressionRatio) {
			listeners.getListeners().forEach(listener -> listener.onDeCompressed(capture, cacheHits, compressionRatio));
		}
	}

}
