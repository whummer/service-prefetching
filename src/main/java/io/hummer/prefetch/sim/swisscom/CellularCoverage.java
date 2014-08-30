package io.hummer.prefetch.sim.swisscom;

import io.hummer.osm.model.Point;
import io.hummer.osm.model.Tile;
import io.hummer.prefetch.context.Location;
import io.hummer.prefetch.context.NetworkQuality;
import io.hummer.prefetch.context.Path.PathPoint;
import io.hummer.prefetch.sim.Constants;
import io.hummer.prefetch.sim.gmaps.GMapsCoordinatesConverter;
import io.hummer.prefetch.sim.util.Util;
import io.hummer.util.log.LogUtil;
import io.hummer.util.xml.XMLUtil;

import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.File;
import java.io.Serializable;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.log4j.Logger;

/**
 * Cellular network coverage util for Swisscom.
 *
 * @author Waldemar Hummer
 */
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
	private static Logger LOG = LogUtil.getLogger();

	/** user-defined overrides */
	public static final Map<Location,NetworkQuality> OVERRIDES = new HashMap<Location, NetworkQuality>();

	static {
		loadCache();
	}

	public static NetworkQuality getCoverage(double lat, double lon) {
		NetworkQuality override = getOverride(lat, lon);
		if(override != null) {
			return override;
		}

		int zoom = DEFAULT_ZOOM;
		double v = io.hummer.osm.util.Util.getVicinity(zoom);
		Tile t = new Tile(lon - v, lat + v, lon + v, lat - v);

		NetworkQuality c = cache.findInCache(t);
		if (c != null) {
			return c;
		}
		c = new NetworkQuality();
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

	/* PRIVATE HELPER METHODS */

	public static NetworkQuality getOverride(double lat, double lon) {
		NetworkQuality q = OVERRIDES.get(new Location(lat, lon));
		//LOG.info("Network quality override (" + OVERRIDES.size() + "): " + q);
		return q;
	}

	public static void setOverrideIfExists(PathPoint p1) {
		NetworkQuality q = getOverride(p1.coordinates.lat, p1.coordinates.lon);
		if(q != null) {
			LOG.debug("Network quality override: " + q);
			p1.cellNetworkCoverage = q;
		}
	}

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
		try {
			XMLUtil xmlUtil = new XMLUtil();
			String str = xmlUtil.toString(cache);
			Util.storeStringGzipped(CACHE_FILE, str);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static void loadCache() {
		if(!new File(CACHE_FILE).exists()) {
			return;
		}
		String str = Util.loadStringFromGzip(CACHE_FILE);
		XMLUtil xmlUtil = new XMLUtil();
		try {
			cache = xmlUtil.toJaxbObject(TiledMapSwisscom.class, 
					xmlUtil.toElement(str));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		System.out.println("Loaded cache file: " + CACHE_FILE +
				" (" + cache.size() + " objects)");
	}

	public static void main(String[] args) {
		System.out.println(GMapsCoordinatesConverter.convertLatLonToPixelPoint(15, 46.4772, 8.7897));
		System.out.println(GMapsCoordinatesConverter.convertLatLonToPixelPoint(10, 46.101816214055304,8.69681517045248));
		
	}

}
