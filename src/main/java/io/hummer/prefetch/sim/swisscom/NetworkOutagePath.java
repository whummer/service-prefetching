package io.hummer.prefetch.sim.swisscom;

import io.hummer.osm.OpenStreetMap;
import io.hummer.prefetch.context.Path.PathPoint;

import java.util.LinkedList;
import java.util.List;

/**
 * @author Waldemar Hummer
 */
public class NetworkOutagePath {

	public final List<PathPoint> path = new LinkedList<>();

	public double getLengthKM() {
		double length = 0;
		for(int i = 0; i < path.size() - 1; i ++) {
			length += OpenStreetMap.getDistance(
					path.get(i).coordinates.toPoint(),
					path.get(i+1).coordinates.toPoint());
		}
		return length;
	}
	
}
