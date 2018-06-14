package mpo.dayon.common.network;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

/**
 * Both the assistant and the assisted are talking to each other using a very
 * simple asynchronous network message layer. The network engine is handling
 * both the sending and the receiving sides.
 */
public abstract class NetworkEngine {
	public abstract void start() throws IOException, NoSuchAlgorithmException, KeyManagementException;
}
