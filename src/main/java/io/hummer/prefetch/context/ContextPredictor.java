package io.hummer.prefetch.context;

import io.hummer.prefetch.context.Path.PathPoint;
import io.hummer.util.log.LogUtil;

import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;

/**
 * "Predicts" and generates future instances of the context information.
 * @author Waldemar Hummer
 */
public interface ContextPredictor<T> {

	List<Time> predictContextChanges(Context<T> currentContext, Time fromTime, Time toTime);

	Context<T> predict(Context<T> currentContext, Time time);

	/**
	 * @param currentContext
	 * @param fromTime including
	 * @param toTime excluding
	 * @return
	 */
	List<Context<T>> predictContexts(
			Context<Object> currentContext, Time fromTime, Time toTime);

	/**
	 * Default implementation based on time, path, location and network availability
	 */
	public static class DefaultPredictor implements ContextPredictor<Object> {

		protected static final Logger LOG = LogUtil.getLogger();

		public Context<Object> predict(Context<Object> currentContext, Time time) {
			double t = time.time;
			Context<Object> copy = currentContext.copy();
			copy.setContextAttribute(Context.ATTR_TIME, time);
			Path path = (Path)copy.getAttribute(Context.ATTR_PATH);
			//Path futurePath = (Path)copy.getAttribute(Context.ATTR_FUTURE_PATH);
			if(path != null) {
				copy.setContextAttribute(Context.ATTR_FUTURE_PATH, path.getFuturePathAt(t));
				PathPoint current = path.getLocationAtTime(t);
				if(current != null) {
					copy.setContextAttribute(Context.ATTR_LOCATION, current);
					copy.setContextAttribute(Context.ATTR_LOCATION_LAT, current.coordinates.lat);
					copy.setContextAttribute(Context.ATTR_LOCATION_LON, current.coordinates.lon);
					copy.setContextAttribute(Context.ATTR_NETWORK_AVAILABLE, 
							current.cellNetworkCoverage.hasSufficientCoverage());
				} else {
					LOG.warn("Cannot find location at time " + t + " in path " + path.points);
					copy.setContextAttribute(Context.ATTR_LOCATION, null);
					copy.setContextAttribute(Context.ATTR_LOCATION_LAT, null);
					copy.setContextAttribute(Context.ATTR_LOCATION_LON, null);
					copy.setContextAttribute(Context.ATTR_NETWORK_AVAILABLE, false);
				}
			}
			return copy;
		}

		public List<Context<Object>> predictContexts(
				Context<Object> currentContext, Time fromTime, Time toTime) {
			List<Context<Object>> result = new LinkedList<Context<Object>>();
			//System.out.println("changes: " + predictContextChanges(currentContext, fromTime, toTime));
			//System.out.println(currentContext);
			for(Time t : predictContextChanges(currentContext, fromTime, toTime)) {
				result.add(predict(currentContext, t));
			}
			LOG.trace("predicted contexts from " + fromTime + " to " + toTime + ": " + result.size());
			return result;
		}

		public List<Time> predictContextChanges(
				Context<Object> currentContext, Time fromTime, Time toTime) {
			List<Time> result = new LinkedList<Time>();
			Path path = (Path)currentContext.getAttribute(Context.ATTR_PATH);
			if(path != null) {
				for(PathPoint p : path.points) {
					if(p.time.isBetween(fromTime, toTime, false)) {
						result.add(p.time);
					}
				}
			}
			if(LOG.isDebugEnabled()) LOG.debug("times ContextChangeBased: " + fromTime + "-" + toTime + ": " + result);
			return result;
		}
	}

	public static class DefaultPredictorWithUpdateInterval extends DefaultPredictor {
		private double updateSeconds = 60;
//		private double timeInterval = 10; // TODO remove??
		public DefaultPredictorWithUpdateInterval() { }
		public DefaultPredictorWithUpdateInterval(double updateSeconds,
				double timeInterval) {
			this.updateSeconds = updateSeconds;
//			this.timeInterval = timeInterval;
		}
		public List<Time> predictContextChanges(Context<Object> currentContext,
				Time fromTime, Time toTime) {
			List<Time> result = new LinkedList<Time>();
			Path path = (Path)currentContext.getAttribute(Context.ATTR_PATH);
			double lastPathTime = -1;
			if(path != null) {
				if(path.points.isEmpty()) {
					return result;
				}
				lastPathTime = path.points.get(path.size() - 1).time.time;
			}
			double start = (double)((int)(fromTime.time / updateSeconds)) * updateSeconds;
			/* note: t < toTime.time is essential here, do NOT use t <= toTime.time */
			for(double t = start; t < toTime.time; t += updateSeconds) {
				if(lastPathTime >= 0 && t > lastPathTime) {
					break;
				}
				if(t >= fromTime.time) {
					result.add(new Time(t));
					//result.add(new Time(t + timeInterval)); // TODO revise!
				}
			}
			if(LOG.isDebugEnabled()) LOG.debug("times UpdateInterval: " + fromTime + "-" + toTime + ": " + result);
			return result;
		}
	}

}
