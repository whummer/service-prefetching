package io.hummer.prefetch.strategy;

import io.hummer.prefetch.PrefetchStrategy;
import io.hummer.prefetch.impl.TimeClock;

import java.util.LinkedList;
import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * Combination of multiple prefetch strategies.
 * @author Waldemar Hummer (hummer@dsg.tuwien.ac.at)
 */
@XmlRootElement(name="strategy")
public class PrefetchStrategyCombined extends PrefetchStrategy {

	private List<PrefetchStrategy> strategies = new LinkedList<>();

	public boolean doPrefetchNow(Object currentContext) {
		for(PrefetchStrategy s : strategies) {
			if(s.doPrefetchNow(currentContext))
				return true;
		}
		return false;
	}

	public Double getNextAskTimeDelayInSecs() {
		double min = PrefetchStrategyPeriodic.DEFAULT_TIMEOUT_SECS;
		for(PrefetchStrategy s : strategies) {
			Double t = s.getNextAskTimeDelayInSecs();
			if(t != null && t < min) {
				min = t;
			}
		}
		return min;
	}

	public void notifyPrefetchPerformed() {
		lastTime = TimeClock.now();
		for(PrefetchStrategy s : strategies) {
			s.notifyPrefetchPerformed();
		}
	}
}