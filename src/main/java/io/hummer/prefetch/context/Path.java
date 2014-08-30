package io.hummer.prefetch.context;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

/**
 * Represents a path of a moving vehicle.
 * @author Waldemar Hummer
 */
@XmlRootElement(name="path")
public class Path {

	@XmlElement(name="p")
	public List<PathPoint> points = new LinkedList<>();
	@XmlTransient
	private double startTime = -1;
	@XmlTransient
	private double endTime = -1;

	@XmlRootElement(name = "p")
	public static class PathPoint implements Serializable {
		private static final long serialVersionUID = 1L;
		@XmlElement(name="t")
		public Time time;
		@XmlElement(name="c")
		public Location coordinates;
		@XmlElement(name="u")
		public Boolean isUndergroundTunnel;
		@XmlElement(name="n")
		public NetworkQuality cellNetworkCoverage;

		public PathPoint() { }
		public PathPoint(Time time, Location location, NetworkQuality net) {
			this.time = time;
			this.coordinates = location;
			this.cellNetworkCoverage = net;
		}

		@Override
		public String toString() {
			boolean hasNet = cellNetworkCoverage != null ? cellNetworkCoverage.hasSufficientCoverage() : false;
			return "P(" + coordinates + ":N(" + hasNet + "):" + time + ")";
		}
	}

	public PathPoint getLocationAtTime(double t) {
		if(points.isEmpty()) {
			return null;
		}
		if(startTime < 0 || endTime < 0) {
			startTime = points.get(0).time.time;
			endTime = points.get(points.size() - 1).time.time;
			//System.out.println("start/end time: " + startTime + "-" + endTime);
		}
		if(t < startTime || t > endTime) {
			return null;
		}
		int count = 0;
		for(PathPoint p : points) {
//			count ++;
//			if(count >= points.size()) {
//				/* when reaching the last position, 
//				 * this vehichle's route is over. */
//				return null; 
//			} else 
			if(p.time.time >= t) {
				return p;
			}
		}
		return null;
	}

	public Path getFuturePathAt(double t) {
		Path result = new Path();
		for(int i = 0; i < points.size(); i ++) {
			PathPoint p = points.get(i);
			if(p.time.time > t) {
				result.points = points.subList(i, points.size());
				return result;
			}
		}
		return result;
	}

	public void add(PathPoint p) {
		points.add(p);
	}
	
	public int size() {
		return points.size();
	}
}
