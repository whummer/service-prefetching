package io.hummer.prefetch.context;

import io.hummer.prefetch.sim.LinkSpeed;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Represents network quality in a client's context.
 * @author Waldemar Hummer
 */
@XmlRootElement(name = "cellCvg")
public class NetworkQuality {

	@XmlAttribute(name="g2")
	public boolean _2g_gsm = false;
	@XmlAttribute(name="g31")
	public boolean _3g_umts = false;
	@XmlAttribute(name="g32")
	public boolean _3g_hspa = false;
	@XmlAttribute(name="g4")
	public boolean _4g_lte = false;

	public NetworkQuality() { }
	public NetworkQuality(boolean allAvailable) {
		_2g_gsm = allAvailable;
		_3g_hspa = allAvailable;
		_3g_umts = allAvailable;
		_4g_lte = allAvailable;
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

	@Override
	public String toString() {
		return "NetworkQuality [_2g_gsm=" + _2g_gsm + ", _3g_umts="
				+ _3g_umts + ", _3g_hspa=" + _3g_hspa + ", _4g_lte=" + _4g_lte
				+ "]";
	}
	
}
