package io.hummer.prefetch.sim;

/**
 * @author Waldemar Hummer (hummer@dsg.tuwien.ac.at)
 */
public class LinkSpeed {
	public double upSpeedMbitPerSec, downSpeedMbitPerSec;

	public double getCapacityKbitPerSec() {
		return Math.min(upSpeedMbitPerSec, downSpeedMbitPerSec) * 1000.0;
	}
}
