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