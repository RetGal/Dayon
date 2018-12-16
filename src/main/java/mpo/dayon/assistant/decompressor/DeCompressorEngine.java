package mpo.dayon.assistant.decompressor;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.Nullable;

import mpo.dayon.assisted.compressor.CompressorEngineConfiguration;
import mpo.dayon.common.capture.Capture;
import mpo.dayon.common.concurrent.DefaultThreadFactoryEx;
import mpo.dayon.common.concurrent.Executable;
import mpo.dayon.common.configuration.Configurable;
import mpo.dayon.common.error.FatalErrorHandler;
import mpo.dayon.common.event.Listeners;
import mpo.dayon.common.log.Log;
import mpo.dayon.common.network.message.NetworkCaptureMessage;
import mpo.dayon.common.network.message.NetworkCaptureMessageHandler;
import mpo.dayon.common.squeeze.Compressor;
import mpo.dayon.common.squeeze.NullTileCache;
import mpo.dayon.common.squeeze.RegularTileCache;
import mpo.dayon.common.squeeze.TileCache;

public class DeCompressorEngine implements Configurable<DeCompressorEngineConfiguration>, NetworkCaptureMessageHandler {
	private final Listeners<DeCompressorEngineListener> listeners = new Listeners<>(DeCompressorEngineListener.class);

	private ThreadPoolExecutor executor;

	private Semaphore semaphore;

	private TileCache cache;

	public DeCompressorEngine() {
	}

	public void configure(DeCompressorEngineConfiguration configuration) {
	}

	public void addListener(DeCompressorEngineListener listener) {
		listeners.add(listener);
	}

	public void removeListener(DeCompressorEngineListener listener) {
		listeners.remove(listener);
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
	public void handleCapture(NetworkCaptureMessage capture) {
		try {
			semaphore.acquire();

			try {
				executor.execute(new MyExecutable(executor, semaphore, capture));
			} catch (RejectedExecutionException ex) {
				semaphore.release(); // unlikely as we have an unbounded queue
										// (!)
			}
		} catch (InterruptedException ex) {
			FatalErrorHandler.bye("The [" + Thread.currentThread().getName() + "] thread is has been interrupted!", ex);
		}
	}

	private void fireOnDeCompressed(Capture capture, int cacheHits, double compressionRatio) {
		final List<DeCompressorEngineListener> xlisteners = listeners.getListeners();

		for (final DeCompressorEngineListener xlistener : xlisteners) {
			xlistener.onDeCompressed(capture, cacheHits, compressionRatio);
		}
	}

	private class MyExecutable extends Executable {
		private final NetworkCaptureMessage message;

		public MyExecutable(ExecutorService executor, Semaphore semaphore, NetworkCaptureMessage message) {
			super(executor, semaphore);

			this.message = message;
		}

		protected void execute() throws Exception {
			try {
				final Compressor compressor = Compressor.get(message.getCompressionMethod());

				@Nullable
				final CompressorEngineConfiguration configuration = message.getCompressionConfiguration();
				if (configuration != null) {
					cache = configuration.useCache() ? new RegularTileCache(configuration.getCacheMaxSize(), configuration.getCachePurgeSize())
							: new NullTileCache();

					Log.info("De-Compressor engine has been reconfigured [tile:" + message.getId() + "] " + configuration);
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
	}

}
