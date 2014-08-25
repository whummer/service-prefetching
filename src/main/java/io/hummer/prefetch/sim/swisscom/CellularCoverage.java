package io.hummer.prefetch.sim.swisscom;

import io.hummer.osm.model.Point;
import io.hummer.osm.model.Tile;
import io.hummer.prefetch.sim.Constants;
import io.hummer.prefetch.sim.LinkSpeed;
import io.hummer.prefetch.sim.gmaps.GMapsCoordinatesConverter;
import io.hummer.prefetch.sim.util.Util;

import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.File;
import java.io.Serializable;
import java.net.URL;

import javax.imageio.ImageIO;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Cellular network coverage for a given point, 
 * for 2nd/3rd/4th generation networks.
 *
 * @author Waldemar Hummer
 */
@XmlRootElement(name = "cellCvg")
public class CellularCoverage implements Serializable {
	protected static final long serialVersionUID = 1L;

	private static TiledMapSwisscom cache = new TiledMapSwisscom();
	private static final String URL_PATTERN = 
			"http://scmplc.begasoft.ch/plcapp/netzabdeckung/<type>?zoom=<zoom>&x=<x>&y=<y>";
	private static final String CACHE_FILE = Constants.TMP_DIR + "/swisscomCoverageCache.xml.gz";
	private static int requestCount = 0;
	public static final int DEFAULT_ZOOM = 18;
	public static final int RETURNED_TILE_PIXEL_SIZE = 256;
	public static final int FLUSH_INTERVAL = 20;

	@XmlAttribute(name="g2")
	public boolean _2g_gsm = false;
	@XmlAttribute(name="g31")
	public boolean _3g_umts = false;
	@XmlAttribute(name="g32")
	public boolean _3g_hspa = false;
	@XmlAttribute(name="g4")
	public boolean _4g_lte = false;

	static {
		loadCache();
	}

	public static CellularCoverage getCoverage(double lat, double lon) {
		int zoom = DEFAULT_ZOOM;
		double v = io.hummer.osm.util.Util.getVicinity(zoom);
		Tile t = new Tile(lon - v, lat + v, lon + v, lat - v);

		CellularCoverage c = cache.findInCache(t);
		if (c != null) {
			return c;
		}
		c = new CellularCoverage();
		c._2g_gsm = get2gGsmCoverage(lat, lon);
		c._3g_umts = get3gUmtsCoverage(lat, lon);
		c._3g_hspa = get3gHspaCoverage(lat, lon);
		c._4g_lte = get4gLteCoverage(lat, lon);
		cache.put(t, c);

		requestCount++;
		if(requestCount % FLUSH_INTERVAL == 0) {
			storeCache();
		}

		return c;
	}

	public LinkSpeed getMaxSpeed() {
		LinkSpeed result = new LinkSpeed();
		if(_4g_lte) {
			result.downSpeedMbitPerSec = 100;
			result.upSpeedMbitPerSec = 50;
		} else if(_3g_hspa) {
			result.downSpeedMbitPerSec = 14.4;
			result.upSpeedMbitPerSec = 5.76;
		} else if(_3g_umts) {
			result.downSpeedMbitPerSec = 0.384;
			result.upSpeedMbitPerSec = 0.384;
		} else if(_2g_gsm) {
			result.downSpeedMbitPerSec = 0.150;
			result.upSpeedMbitPerSec = 0.150;
		}
		return result;
	}

	public boolean hasAnyCoverage() {
		return _4g_lte || _3g_hspa || _3g_umts || _2g_gsm;
	}

	/* PRIVATE HELPER METHODS */

	private static boolean get2gGsmCoverage(double lat, double lon) {
		return getCoverage("gsm", DEFAULT_ZOOM, lat, lon);
	}

	private static boolean get3gUmtsCoverage(double lat, double lon) {
		return getCoverage("umts", DEFAULT_ZOOM, lat, lon);
	}

	private static boolean get3gHspaCoverage(double lat, double lon) {
		return getCoverage("hspa", DEFAULT_ZOOM, lat, lon);
	}

	private static boolean get4gLteCoverage(double lat, double lon) {
		return getCoverage("lte", DEFAULT_ZOOM, lat, lon);
	}

	private static boolean getCoverage(String type, int zoom, double lat, double lon) {
		Point p = GMapsCoordinatesConverter.convertLatLonToPixelPoint(zoom, lat, lon);
		try {
			String url = URL_PATTERN.replace("<type>", "" + type)
					.replace("<zoom>", "" + DEFAULT_ZOOM)
					.replace("<x>", "" + Math.round(p.x))
					.replace("<y>", "" + Math.round(p.y));
			BufferedImage img = null;
			img = ImageIO.read(new URL(url).openStream());
			Raster r = img.getData();
			double numTransparent = 0;
			double numNonTransparent = 0;
			for (int x = r.getMinX(); x - r.getMinX() < r.getWidth(); x++) {
				for (int y = r.getMinY(); y - r.getMinY() < r.getHeight(); y++) {
					int[] d = r.getPixel(x, y, (int[]) null);
					long opacity = d[3];
					if (opacity <= 0) {
						numTransparent++;
					} else {
						numNonTransparent++;
					}
				}
			}
			return numNonTransparent > numTransparent;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static void storeCache() {
		if(!new File(Constants.TMP_DIR).exists()) {
			new File(Constants.TMP_DIR).mkdir();
		}
		String str = Util.toString(cache);
		Util.storeStringGzipped(CACHE_FILE, str);
	}

	private static void loadCache() {
		if(!new File(CACHE_FILE).exists()) {
			return;
		}
		String str = Util.loadStringFromGzip(CACHE_FILE);
		try {
			cache = Util.toJaxbObject(TiledMapSwisscom.class, 
					Util.toElement(str));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		System.out.println("Loaded cache file: " + CACHE_FILE +
				" (" + cache.size() + " objects)");
	}

	@Override
	public String toString() {
		return "CellularCoverage [_2g_gsm=" + _2g_gsm + ", _3g_umts="
				+ _3g_umts + ", _3g_hspa=" + _3g_hspa + ", _4g_lte=" + _4g_lte
				+ "]";
	}
	
	public static void main(String[] args) {
		System.out.println(GMapsCoordinatesConverter.convertLatLonToPixelPoint(15, 46.4772, 8.7897));
		System.out.println(GMapsCoordinatesConverter.convertLatLonToPixelPoint(10, 46.101816214055304,8.69681517045248));
		
	}
}
