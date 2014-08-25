package io.hummer.prefetch.impl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Used to handle different notions of time, 
 * e.g., real system time, simulation time, ...
 * 
 * All times are in seconds with type Double (e.g., 10.25 seconds).
 * 
 * @author Waldemar Hummer
 */
public abstract class TimeClock {

	public static final AtomicReference<TimeClock> instance = 
			new AtomicReference<>((TimeClock)new TimeClockSystem());

	public static double now() {
		return instance.get().getTime();
	}
	public static void setTime(double time) {
		instance.set(new TimeClockManual(time));
		runTasks();
	}

	abstract double getTime();

	public static class TimeClockSystem extends TimeClock {
		@Override
		public double getTime() {
			return ((double)System.currentTimeMillis()) / 1000.0;
		}
	}
	public static class TimeClockManual extends TimeClock {
		double time;
		public TimeClockManual(double time) {
			this.time = time;
		}
		@Override
		public double getTime() {
			return time;
		}
	}

	/**
	 * Filter by timestamp range.
	 * @param timestamps
	 * @param timeFrom from, inclusive
	 * @param timeTo to, exclusive
	 * @return
	 */
	public static <T> List<T> filterTimestampedValues(
			Map<Double,T> timestamps, double timeFrom, double timeTo) {
		List<T> result = new LinkedList<T>();
		for(double d : timestamps.keySet()) {
			if(d >= timeFrom && d < timeTo) {
				result.add(timestamps.get(d));
			}
		}
		return result;
	}

	private static Map<TimerTask,Double> tasks = new HashMap<TimerTask,Double>();
	public static void schedule(TimerTask task, double delaySecs) {
		tasks.put(task, now() + delaySecs);
	}

	private static void runTasks() {
		double now = now();
		for(Entry<TimerTask,Double> e : new HashSet<>(tasks.entrySet())) {
			if(e.getValue() >= now) {
				tasks.remove(e.getKey());
				//GlobalThreadPool.execute(e.getKey());
				e.getKey().run();
			}
		}
	}

}
