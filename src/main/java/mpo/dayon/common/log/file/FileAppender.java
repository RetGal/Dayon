package mpo.dayon.common.log.file;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;

import mpo.dayon.common.log.LogAppender;
import mpo.dayon.common.log.LogLevel;
import mpo.dayon.common.log.console.ConsoleAppender;

import static java.nio.charset.StandardCharsets.UTF_8;

public class FileAppender extends LogAppender {
	private static final long MAX_FILE_SIZE = 1024 * 1024L;

	private static final int MAX_BACKUP_INDEX = 3;

	private final ConsoleAppender fallback = new ConsoleAppender();

	private final String filename;

	private PrintWriter writer;

	private long count;

	private long nextRolloverCount;

	public FileAppender(String filename) throws FileNotFoundException {
		this.filename = filename;
		setupFile(filename, true);
	}

    @Override
    public void append(LogLevel level, String message, Throwable error) {
        synchronized (this) {
            try {
                StringBuilder builder = new StringBuilder(64);
                builder.append(format(level, message)).append(System.lineSeparator());
                if (error != null) {
                    builder.append(getStackTrace(error)).append(System.lineSeparator());
                }
                count += builder.length();
                if (count >= MAX_FILE_SIZE && count >= nextRolloverCount) {
                    rollOver();
                }
                writer.write(builder.toString());
                writer.flush();
            } catch (RuntimeException ex) {
                fallback.append(level, message, error);
                fallback.append(LogLevel.WARN, "[FileAppender] error", ex);
            }
        }
    }

	private static String getStackTrace(Throwable error) {
		final StringWriter out = new StringWriter();
		final PrintWriter printer = new PrintWriter(out);
		error.printStackTrace(printer);
		return out.getBuffer().toString();
	}

	private void setupFile(String filename, boolean append) throws FileNotFoundException {
		writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filename, append), UTF_8)));
		final File file = new File(filename);
		count = file.length();
	}

	@java.lang.SuppressWarnings("squid:S106")
	private void rollOver() {
		nextRolloverCount = count + MAX_FILE_SIZE;
		boolean renameSucceeded = deleteSurplus();

		// Rename  .1, .2, ..., .MAX_BACKUP_INDEX-1  to  .2., .3, ..., .MAX_BACKUP_INDEX
		for (int idx = MAX_BACKUP_INDEX - 1; idx >= 1 && renameSucceeded; idx--) {
			final File file = new File(filename + "." + idx);
			if (file.exists()) {
				final File target = new File(filename + '.' + (idx + 1));
				renameSucceeded = file.renameTo(target);
			}
		}

		// Rename fileName to fileName.1
		if (renameSucceeded) {
			writer.close();
			renameSucceeded = new File(filename).renameTo(new File(filename + "." + 1));
			if (!renameSucceeded) {
				try {
					setupFile(filename, true);
				} catch (IOException ex) {
					ex.printStackTrace(System.err);
				}
			}
		}

		if (renameSucceeded) {
			try {
				setupFile(filename, false);
				nextRolloverCount = 0;
			} catch (IOException ex) {
				ex.printStackTrace(System.err);
			}
		}
	}

	@java.lang.SuppressWarnings("squid:S4042")
	private boolean deleteSurplus() {
		final File file = new File(filename + '.' + MAX_BACKUP_INDEX);
		if (file.exists()) {
			// not interested in the cause
			return file.delete();
		}
		return true;
	}

}
