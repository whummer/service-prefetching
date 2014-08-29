package io.hummer.prefetch;

import io.hummer.prefetch.impl.UsagePattern;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Waldemar Hummer (hummer@dsg.tuwien.ac.at)
 */
public class UsagePatternTest {

	@Test
	@SuppressWarnings("deprecation")
	public void testPattern() throws Exception {
		double errorDelta = 0.000000000001;
		
		UsagePattern p = UsagePattern.periodic(10, 1, 1);
		assertEquals(0, p.predictUsage(0), errorDelta);
		assertEquals(0, p.predictUsage(8), errorDelta);
		assertEquals(1, p.predictUsage(9), errorDelta);
		assertEquals(1, p.predictUsage(9.99999), errorDelta);

		assertEquals(0, p.predictUsage(10), errorDelta);
		assertEquals(0, p.predictUsage(15), errorDelta);
		assertEquals(1, p.predictUsage(19), errorDelta);
		assertEquals(1, p.predictUsage(19.9999999), errorDelta);

		assertEquals(0, p.predictUsage(20), errorDelta);
	}
}