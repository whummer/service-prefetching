package io.hummer.prefetch.ws;

import io.hummer.prefetch.impl.PrefetchingServiceImpl;

import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.transform.dom.DOMResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Custom JAXB (un-)marshaller required to hold abstract  
 * parameter types in some Web service interfaces.
 * 
 * @author Waldemar Hummer (hummer@dsg.tuwien.ac.at)
 */
public class JaxbAdapter extends XmlAdapter<Object,Object> {

	//private static final XMLUtil xmlUtil = new XMLUtil();
	private static final Map<Class<?>,JAXBContext> contexts = new HashMap<>();

	public Object marshal(Object o) throws Exception {
		if(o == null) {
			return null;
		}
		if(o instanceof SOAPEnvelope) {
			return WSClient.toElement(
					WSClient.toString((SOAPEnvelope)o));
		}
		Class<?> clazz = o.getClass();
		if(!contexts.containsKey(clazz)) {
			JAXBContext c = JAXBContext.newInstance(clazz);
			contexts.put(clazz, c);
		}
		JAXBContext c = contexts.get(clazz);
		DOMResult res = new DOMResult();
		c.createMarshaller().marshal(o, res);
		Element e = ((Document)res.getNode()).getDocumentElement();
		e.setAttribute("class", clazz.getCanonicalName());
		return e;
	}

	public Object unmarshal(Object o) throws Exception {
		Element e = (Element)o;
		String name = e.getLocalName();
		if(name.equalsIgnoreCase("Envelope")) {
			return WSClient.toEnvelope((Element)o);
		} else {
			Class<?> clazz = PrefetchingServiceImpl.class;
			String className = e.getAttribute("class");
			if(!className.isEmpty()) {
				clazz = Class.forName(className);
			}
			if(!contexts.containsKey(clazz)) {
				JAXBContext c = JAXBContext.newInstance(clazz);
				contexts.put(clazz, c);
			}
			JAXBContext c = contexts.get(clazz);
			Object res = c.createUnmarshaller().unmarshal(e);
			return res;
		}
	}

}
