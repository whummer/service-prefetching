package io.hummer.prefetch;

/**
 * Define the strategy for service prefetching. Specialized 
 * strategy subclasses extend this class.
 * @author Waldemar Hummer (hummer@dsg.tuwien.ac.at)
 */
public abstract class PrefetchStrategy {

	/**
	 * Not required from a functional perspective. Mainly used for testing.
	 */
	public String id;

	/**
	 * Last time that prefetching has been done.
	 */
	protected double lastTime;

	/**
	 * Whether or not we should initiate prefetching now.
	 * 
	 * @param context an object which describes the current context.
	 */
	public abstract boolean doPrefetchNow(Object context);

	/**
	 * Next time to ask this strategy about whether we should prefetch. 
	 * Value in seconds.
	 * - if return value is 0 : nothing specified, caller may decide when.
	 * - if return value is null : terminate prefetching, do not call this method again
	 */
	public abstract Double getNextAskTimeDelayInSecs();

	/**
	 * Notify the strategy that prefetching has just been performed.
	 */
	public abstract void notifyPrefetchPerformed();

}