package mpo.dayon.common.configuration;

public interface ReConfigurable<T extends Configuration> extends Configurable<T> {
	/**
	 * Allows for dynamic re-configuration.
	 */
	void reconfigure(T configuration);

}