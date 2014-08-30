package io.hummer.prefetch.context;

import io.hummer.osm.model.Point;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * Represents location in a client's context.
 * @author Waldemar Hummer
 */
@XmlRootElement(name="coords")
public class Location implements Serializable {
	private static final long serialVersionUID = 1L;

	public double x = -1, y = -1, z = -1;
	public double lat, lon;

	public Location() { }
	public Location(double lat, double lon) {
		this.lat = lat;
		this.lon = lon;
	}

	public Point toPoint() {
		return new Point(lon, lat);
	}
	@Override
	public String toString() {
		return "[" + lat + "," + lon + "]";
	}
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		long temp;
		temp = Double.doubleToLongBits(lat);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		temp = Double.doubleToLongBits(lon);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		return result;
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Location other = (Location) obj;
		if (Double.doubleToLongBits(lat) != Double.doubleToLongBits(other.lat))
			return false;
		if (Double.doubleToLongBits(lon) != Double.doubleToLongBits(other.lon))
			return false;
		if ((x >= 0 && other.x >= 0) && Double.doubleToLongBits(x) != Double.doubleToLongBits(other.x))
			return false;
		if ((y >= 0 && other.y >= 0) && Double.doubleToLongBits(y) != Double.doubleToLongBits(other.y))
			return false;
		if ((z >= 0 && other.z >= 0) && Double.doubleToLongBits(z) != Double.doubleToLongBits(other.z))
			return false;
		return true;
	}

}