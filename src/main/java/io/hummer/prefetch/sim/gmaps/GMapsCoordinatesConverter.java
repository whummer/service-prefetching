package io.hummer.prefetch.sim.gmaps;

import io.hummer.osm.model.Point;
import io.hummer.osm.util.MapProjection;
import io.hummer.osm.util.MercatorProjection;
import io.hummer.osm.util.Util;
import io.hummer.prefetch.sim.swisscom.CellularCoverage;

public class GMapsCoordinatesConverter {

	public static Point convertLatLonToPixelPoint(int zoom, double lat, double lon) {
		return convertLatLonToPixelPoint(new MercatorProjection(zoom), lat, lon);
	}
	public static Point convertLatLonToPixelPoint(MapProjection p, double lat, double lon) {
		return p.fromLatLngToPoint(lat, lon);
	}

	public static Point convertPixelPointToLatLon(int zoom, double xPixel, double yPixel) {
		return convertPixelPointToLatLon(new MercatorProjection(zoom), xPixel, yPixel);
	}
	public static Point convertPixelPointToLatLon(MapProjection p, double xPixel, double yPixel) {
		return p.fromPointToLatLng(xPixel, yPixel);
	}

	public static void main(String[] args) {
		System.out.println(convertLatLonToPixelPoint(14, 47.57428078387954,8.684112685488282));
		System.out.println(convertLatLonToPixelPoint(17, 47.57428078387954,8.684112685488282));
		System.out.println(convertLatLonToPixelPoint(18, 47.57428078387954,8.684112685488282));

		System.out.println(Util.getVicinity(10));
		System.out.println(Util.getVicinity(1));
		System.out.println(Util.getVicinity(15));
		System.exit(0);

		System.out.println(CellularCoverage.getCoverage(47.57428078387954,8.684112685488282));
	}

}
