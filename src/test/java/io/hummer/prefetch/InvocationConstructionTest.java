package io.hummer.prefetch;

import static org.junit.Assert.assertTrue;
import io.hummer.prefetch.PrefetchingService.ServiceInvocation;
import io.hummer.prefetch.context.Context;
import io.hummer.prefetch.sim.ws.VehicleInfoService;
import io.hummer.prefetch.ws.W3CEndpointReferenceUtils;
import io.hummer.prefetch.ws.WSClient;
import io.hummer.util.xml.XMLUtil;

import org.junit.Test;
import org.w3c.dom.Element;

public class InvocationConstructionTest {

	@Test
	public void construct() throws Exception {
		XMLUtil xmlUtil = new XMLUtil();
		ServiceInvocation tmp = new ServiceInvocation();
		Element body = WSClient.toElement(
				"<tns:getTrafficInfo " +
				"xmlns:tns=\"" + VehicleInfoService.NAMESPACE + "\">" + 
				"<lat>{{" + Context.ATTR_LOCATION_LAT + "}}</lat>" +
				"<lon>{{" + Context.ATTR_LOCATION_LON + "}}</lon>" +
				"</tns:getTrafficInfo>");
		tmp.serviceCall = WSClient.createEnvelopeFromBody(body);
		tmp.prefetchPossible = true;
		tmp.serviceEPR = W3CEndpointReferenceUtils.createEndpointReference("http://foo");
		final String tmpl = xmlUtil.toString(tmp);
		assertTrue(tmpl.contains("getTrafficInfo"));
	}

}
