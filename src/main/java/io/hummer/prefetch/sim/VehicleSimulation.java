package io.hummer.prefetch.sim;

import io.hummer.osm.OpenStreetMap;
import io.hummer.prefetch.PrefetchingService.ServiceInvocation;
import io.hummer.prefetch.context.Location;
import io.hummer.prefetch.context.Path;
import io.hummer.prefetch.context.Path.PathPoint;
import io.hummer.prefetch.context.Time;
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
		@XmlElement(name="path")
		public final Path path = new Path();
		@XmlAttribute
		public String id;
		@XmlTransient
		public List<ServiceUsagePattern> usagePatterns = new LinkedList<ServiceUsagePattern>();

		public void addPathPoint(double time, double east, double north, 
				double height, boolean loadDetails) {
			Time t = new Time();
			t.time = time;
			Location c = new Location();
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
			for(PathPoint p : path.points) {
				if(!p.cellNetworkCoverage.hasSufficientCoverage()) {
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
			return path.getLocationAtTime(t);
		}

		public Path getFuturePathAt(double t) {
			return path.getFuturePathAt(t);
		}

//		public double getMissingDataSizeAtTime(double t) {
//			PathPoint pt = getLocationAtTime(t);
//			if(pt == null)
//				return 0;
//			double usage = predictNetworkUsage(t);
//			double capacity = 
//					pt.cellNetworkCoverage.getMaxSpeed().getCapacityKbitPerSec();
//			if(usage > capacity)
//				return usage - capacity;
//			return 0;
//		}

//		public double predictNetworkUsage(double time) {
//			List<UsagePattern> patterns = new LinkedList<UsagePattern>();
//			for(ServiceUsagePattern p : usagePatterns) {
//				patterns.add(p.pattern);
//			}
//			return UsagePattern.combine(
//					patterns.toArray(new UsagePattern[0])
//				).predictUsage(time);
//		}

	}

}