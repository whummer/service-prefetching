package io.hummer.prefetch.strategy;

import io.hummer.prefetch.PrefetchStrategy;
import io.hummer.prefetch.impl.TimeClock;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Periodic pre-fetching strategy.
 * @author Waldemar Hummer (hummer@dsg.tuwien.ac.at)
 */
@XmlRootElement(name="strategy")
@XmlAccessorType(XmlAccessType.PUBLIC_MEMBER)
public class PrefetchStrategyPeriodic extends PrefetchStrategy {

	/**
	 * Default timeout (5 minutes).
	 */
	public static final double DEFAULT_TIMEOUT_SECS = 60*5;

	/**
	 * Timeout for periodic re-fetching, in milliseconds.
	 */
	public double timeoutSecs;

	public PrefetchStrategyPeriodic() {
		this(DEFAULT_TIMEOUT_SECS);
	}
	public PrefetchStrategyPeriodic(double timeoutSecs) {
		this.timeoutSecs = timeoutSecs;
		this.lastTime = 0;
	}

	public boolean doPrefetchNow(Object context) {
		return TimeClock.now() >= lastTime + timeoutSecs;
	}

	public Double getNextAskTimeDelayInSecs() {
		return timeoutSecs;
	}

	public void notifyPrefetchPerformed() {
		lastTime = TimeClock.now();
	}
}