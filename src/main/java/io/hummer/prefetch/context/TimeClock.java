package io.hummer.prefetch.context;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
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
		if(!(instance.get() instanceof TimeClockManual))
			instance.set(new TimeClockManual(time));
		TimeClockManual t = (TimeClockManual)instance.get();
		t.setTheTime(time);
		t.runTasks();
	}

	abstract double getTime();

	abstract void scheduleTask(TimerTask task, double delaySecs);

	public static class TimeClockSystem extends TimeClock {
		
		final Timer timer = new Timer();
		
		@Override
		public double getTime() {
			return ((double)System.currentTimeMillis()) / 1000.0;
		}
		@Override
		void scheduleTask(TimerTask task, double delaySecs) {
			timer.schedule(task, (long)(delaySecs * 1000.0));
		}
	}
	public static class TimeClockManual extends TimeClock {
		
		double time;
		final Map<TimerTask,Double> tasks = new HashMap<TimerTask,Double>();
		
		public TimeClockManual(double time) {
			this.time = time;
		}
		@Override
		public double getTime() {
			return time;
		}
		public void setTheTime(double time) {
			this.time = time;
		}
		@Override
		void scheduleTask(TimerTask task, double delaySecs) {
			tasks.put(task, now() + delaySecs);
		}
		private void runTasks() {
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

	public static void schedule(TimerTask task, double delaySecs) {
		instance.get().scheduleTask(task, delaySecs);
	}

}
