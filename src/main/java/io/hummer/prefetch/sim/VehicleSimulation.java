package io.hummer.prefetch.sim;

import io.hummer.osm.OpenStreetMap;
import io.hummer.osm.model.Point;
import io.hummer.prefetch.PrefetchingService.ServiceInvocation;
import io.hummer.prefetch.impl.UsagePattern;
import io.hummer.prefetch.sim.swisscom.CellularCoverage;
import io.hummer.prefetch.sim.swisscom.NetworkOutagePath;
import io.hummer.prefetch.sim.util.Util;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;


/**
 * Evaluation scenario for service prefetching (vehicle simulation).
 * @author Waldemar Hummer (hummer@dsg.tuwien.ac.at)
 */
public class VehicleSimulation {

	@XmlRootElement
	public static class Time implements Comparable<Time>, Serializable {
		private static final long serialVersionUID = 1L;
		@XmlElement(name="t")
		public double time;
		public int compareTo(Time o) {
			return Double.valueOf(time).compareTo(o.time);
		}
		@Override
		public int hashCode() {
			long temp = Double.doubleToLongBits(time);
			return (int) (temp ^ (temp >>> 32));
		}
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Time other = (Time) obj;
			if (Double.doubleToLongBits(time) != Double
					.doubleToLongBits(other.time))
				return false;
			return true;
		}
		@Override
		public String toString() {
			return "T(" + time + ")";
		}
	}
	@XmlRootElement(name="coords")
	public static class Coordinates implements Serializable {
		private static final long serialVersionUID = 1L;
		public double x, y, z;
		public double lat, lon;
		public Point toPoint() {
			return new Point(lon, lat);
		}
		@Override
		public String toString() {
			return "[" + lat + "," + lon + "]";
		}
	}
	@XmlRootElement
	public static class MovingEntities implements Serializable {
		private static final long serialVersionUID = 1L;
		@XmlElement(name="e")
		public List<MovingEntity> entities = new LinkedList<>();
		public boolean containsID(String id) {
			return getEntity(id) != null;
		}
		public MovingEntity getEntity(String id) {
			for(MovingEntity e : entities) {
				if(id.equals(e.id))
					return e;
			}
			return null;
		}
	}
	@XmlRootElement(name = "entry")
	public static class PathPoint implements Serializable {
		private static final long serialVersionUID = 1L;
		@XmlElement(name="t")
		public Time time;
		@XmlElement(name="c")
		public Coordinates coordinates;
		@XmlElement(name="u")
		public Boolean isUndergroundTunnel;
		@XmlElement(name="n")
		public CellularCoverage cellNetworkCoverage;

		@Override
		public String toString() {
			return "P(" + coordinates + ":" + time + ")";
		}
	}

	@XmlRootElement
	public static class ServiceUsagePattern {
		@XmlElement(name="inv")
		public ServiceInvocation invocation;
		@XmlElement(name="pat")
		public UsagePattern pattern;
		
		public ServiceUsagePattern() { }
		public ServiceUsagePattern(ServiceInvocation inv, UsagePattern pat) {
			this.invocation = inv;
			this.pattern = pat;
		}
	}

	@XmlRootElement
	public static class MovingEntity implements Serializable {
		private static final long serialVersionUID = 1L;
		@XmlElement(name="p")
		public final List<PathPoint> path = new LinkedList<>();
		@XmlAttribute
		public String id;
		@XmlElement
		public List<ServiceUsagePattern> usagePatterns = new LinkedList<ServiceUsagePattern>();

		@XmlTransient
		private double startTime = -1;
		@XmlTransient
		private double endTime = -1;

		public double predictNetworkUsage(double time) {
			List<UsagePattern> patterns = new LinkedList<UsagePattern>();
			for(ServiceUsagePattern p : usagePatterns) {
				patterns.add(p.pattern);
			}
			return UsagePattern.combine(
					patterns.toArray(new UsagePattern[0])
				).predictUsage(time);
		}

		public void addPathPoint(double time, double east, double north, 
				double height, boolean loadDetails) {
			Time t = new Time();
			t.time = time;
			Coordinates c = new Coordinates();
			c.x = east; c.y = north; c.z = height;
			double[] coords = Util.convertSwissToGPS(east, north, height);
			c.lat = coords[0];
			c.lon = coords[1];
			PathPoint p = new PathPoint();
			p.time = t;
			p.coordinates = c;
			p.cellNetworkCoverage = CellularCoverage.getCoverage(c.lat, c.lon);
			if(loadDetails) {
				p.isUndergroundTunnel = OpenStreetMap.isInTunnel(c.lat, c.lon);
				if(p.isUndergroundTunnel) {
					System.out.println("! Is in tunnel (length " + 
							OpenStreetMap.getTunnelLength(c.lat, c.lon) + 
							"): " + c.lat + "," + c.lon);
				}
			}
			path.add(p);
		}
		
		public List<NetworkOutagePath> getNetworkOutages() {
			List<NetworkOutagePath> result = new LinkedList<>();
			NetworkOutagePath tmp = null;
			for(PathPoint p : path) {
				if(!p.cellNetworkCoverage.hasAnyCoverage()) {
					if(tmp == null)
						tmp = new NetworkOutagePath();
					tmp.path.add(p);
				} else {
					if(tmp != null) {
						result.add(tmp);
						System.out.println("No cell coverage here: " + 
								tmp.path.get(0).coordinates + ", " + 
								tmp.path.size() + " nodes, " +
								tmp.getLengthKM() + "km");
					}
					tmp = null;
				}
			}
			return result;
		}

		public PathPoint getLocationAtTime(double t) {
			if((startTime < 0 || endTime < 0) && !path.isEmpty()) {
				startTime = path.get(0).time.time;
				endTime = path.get(path.size() - 1).time.time;
			}
			if(t < startTime || t > endTime) {
				return null;
			}
			int count = 0;
			for(PathPoint p : path) {
				count ++;
				if(count >= path.size()) {
					return null; // when reaching the last position, this vehichle's route is over.
				} else if(p.time.time > t) {
					return p;
				}
			}
			return null;
		}

		public List<PathPoint> getFuturePathAt(double t) {
			for(int i = 0; i < path.size(); i ++) {
				PathPoint p = path.get(i);
				if(p.time.time > t) {
					return path.subList(i, path.size());
				}
			}
			return new LinkedList<>();
		}

		public double getMissingDataSizeAtTime(double t) {
			PathPoint pt = getLocationAtTime(t);
			if(pt == null)
				return 0;
			double usage = predictNetworkUsage(t);
			double capacity = 
					pt.cellNetworkCoverage.getMaxSpeed().getCapacityKbitPerSec();
			if(usage > capacity)
				return usage - capacity;
			return 0;
		}
	}

}