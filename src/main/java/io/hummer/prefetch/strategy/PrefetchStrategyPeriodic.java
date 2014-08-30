package io.hummer.prefetch.strategy;

import io.hummer.prefetch.PrefetchStrategy;
import io.hummer.prefetch.context.TimeClock;
import io.hummer.util.log.LogUtil;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.log4j.Logger;

/**
 * Periodic pre-fetching strategy.
 * @author Waldemar Hummer (hummer@dsg.tuwien.ac.at)
 */
@XmlRootElement(name="strategy")
@XmlAccessorType(XmlAccessType.PUBLIC_MEMBER)
public class PrefetchStrategyPeriodic extends PrefetchStrategy {

	static final Logger LOG = LogUtil.getLogger();

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
	public PrefetchStrategyPeriodic(double timeoutSecs, String id) {
		this(timeoutSecs);
		this.id = id;
	}

	public boolean doPrefetchNow(Object context) {
		boolean doPrefetch = TimeClock.now() >= lastTime + timeoutSecs;
		//Thread.dumpStack();
		LOG.debug("PrefStrPer doPrefetchNow (" + TimeClock.now() + " - " + 
				lastTime + " - " + timeoutSecs + "): " + doPrefetch);
		return doPrefetch;
	}

	public Double getNextAskTimeDelayInSecs() {
		return timeoutSecs;
	}

	public void notifyPrefetchPerformed() {
		lastTime = TimeClock.now();
	}

	public double getTimeoutSecs() {
		return timeoutSecs;
	}
}