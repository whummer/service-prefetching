package io.hummer.prefetch.sim.swisscom;

import io.hummer.osm.model.Tile;
import io.hummer.osm.model.TiledMapAbstract;
import io.hummer.prefetch.context.NetworkQuality;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSeeAlso;

/**
 * TiledMap implementation for Swisscom cellular coverage maps.
 * @author Waldemar Hummer
 */
@XmlRootElement
@XmlSeeAlso(NetworkQuality.class)
public class TiledMapSwisscom extends TiledMapAbstract<NetworkQuality> {
	@SuppressWarnings("all")
	private static final long serialVersionUID = 1L;

	public NetworkQuality loadLazily(Tile t1) {
		/* lazy loading should never happen here */
		throw new RuntimeException("not supported");
	}

}
