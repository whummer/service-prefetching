package io.hummer.prefetch.strategy;

import io.hummer.prefetch.PrefetchStrategy;
import io.hummer.prefetch.context.TimeClock;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * Never pre-fetch with this strategy.
 * @author Waldemar Hummer (hummer@dsg.tuwien.ac.at)
 */
@XmlRootElement(name="strategy")
public class PrefetchStrategyNone extends PrefetchStrategy {

	public boolean doPrefetchNow(Object context) {
		return false;
	}

	public Double getNextAskTimeDelayInSecs() {
		return null; /* null = never ask again. */
	}

	public void notifyPrefetchPerformed() {
		lastTime = TimeClock.now();
	}
}
