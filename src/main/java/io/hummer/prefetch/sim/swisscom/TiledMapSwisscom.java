package io.hummer.prefetch.sim.swisscom;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSeeAlso;

import io.hummer.osm.model.Tile;
import io.hummer.osm.model.TiledMapAbstract;

/**
 * TiledMap implementation for Swisscom cellular coverage maps.
 * @author Waldemar Hummer
 */
@XmlRootElement
@XmlSeeAlso(CellularCoverage.class)
public class TiledMapSwisscom extends TiledMapAbstract<CellularCoverage> {
	@SuppressWarnings("all")
	private static final long serialVersionUID = 1L;

	public CellularCoverage loadLazily(Tile t1) {
		/* lazy loading should never happen here */
		throw new RuntimeException("not supported");
	}

}
