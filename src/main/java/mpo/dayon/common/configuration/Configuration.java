package mpo.dayon.common.configuration;

public abstract class Configuration {
	public final void persist() {
		persist(false);
	}

	/**
	 * @param clear
	 *            allows for clearing properties from previous version
	 */
	protected abstract void persist(boolean clear);

}
