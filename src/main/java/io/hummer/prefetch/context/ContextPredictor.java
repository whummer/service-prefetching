package io.hummer.prefetch.context;

import io.hummer.prefetch.context.Path.PathPoint;

/**
 * "Predicts" and generates future instances of the context information.
 * @author Waldemar Hummer
 */
public interface ContextPredictor<T> {

	Context<T> predict(Context<T> currentContext, double time);

	public static class DefaultPredictor implements ContextPredictor<Object>{

		public Context<Object> predict(Context<Object> currentContext, double time) {
			Context<Object> copy = currentContext.copy();
			copy.setContextAttribute(Context.ATTR_TIME, time);
			Path path = (Path)copy.getAttribute(Context.ATTR_FUTURE_PATH);
			copy.setContextAttribute(Context.ATTR_FUTURE_PATH, path.getFuturePathAt(time));
			PathPoint current = path.getLocationAtTime(time);
			if(current != null) {
				copy.setContextAttribute(Context.ATTR_LOCATION, current);
				copy.setContextAttribute(Context.ATTR_LOCATION_LAT, current.coordinates.lat);
				copy.setContextAttribute(Context.ATTR_LOCATION_LON, current.coordinates.lon);
				copy.setContextAttribute(Context.ATTR_NETWORK_AVAILABLE, 
						current.cellNetworkCoverage.hasAnyCoverage());
			} else {
				copy.setContextAttribute(Context.ATTR_LOCATION, null);
				copy.setContextAttribute(Context.ATTR_LOCATION_LAT, null);
				copy.setContextAttribute(Context.ATTR_LOCATION_LON, null);
				copy.setContextAttribute(Context.ATTR_NETWORK_AVAILABLE, false);
			}
			return copy;
		}
		
	}

}
