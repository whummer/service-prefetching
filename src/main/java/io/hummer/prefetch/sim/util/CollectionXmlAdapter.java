package io.hummer.prefetch.sim.util;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.xml.bind.annotation.adapters.XmlAdapter;

import org.w3c.dom.Element;

public class CollectionXmlAdapter extends XmlAdapter<Object, Object> {
	private static final String NAMESPACE = "http://jaxbAdapter";
    
	@Override
	@SuppressWarnings("all")
	public Object marshal(Object o) throws Exception {
		StringBuilder b = new StringBuilder();
		if(o instanceof Map) {
			Map map = (Map)o;
			String name = "map";
			if(o instanceof SortedMap) {
				name = "sortedMap";
			}
			b.append("<j:" + name + " xmlns:j=\"" + NAMESPACE + "\" j:type=\"" + name + "\">");
			for(Object e : map.entrySet()) {
				Entry entry = (Entry)e;
				b.append("<j:e>");
				b.append("<k>");
				b.append(Util.toString(marshal(entry.getKey())));
				b.append("</k>");
				b.append("<v>");
				b.append(Util.toString(marshal(entry.getValue())));
				b.append("</v>");
				b.append("</j:e>");
			}
			b.append("</j:" + name + ">");
		} else if(o instanceof List) {
			List list = (List)o;
			b.append("<j:list xmlns:j=\"" + NAMESPACE + "\">");
			for(Object i : list) {
				b.append("<j:i>");
				b.append(Util.toString(marshal(i)));
				b.append("</j:i>");
			}
			b.append("</j:list>");
		} else if(o instanceof Element) {
			return (Element)o;
		} else {
			return Util.toElement(o);
		}
		return Util.toElement(b.toString());
	}
	@Override
	@SuppressWarnings("all")
	public Object unmarshal(Object v) throws Exception {
		Element e = (Element)v;
		List<Element> children = Util.getChildElements(e);
		if(NAMESPACE.equals(e.getNamespaceURI()) || (children.size() > 0 && 
				NAMESPACE.equals(children.get(0).getNamespaceURI()))) {
			if(e.getLocalName().equals("map") || e.getLocalName().equals("sortedMap") 
					|| (children.size() > 0 && children.get(0).getLocalName().equals("e"))) { // e = map entry
				Map map = new HashMap();
				if(e.getLocalName().equals("sortedMap") || 
						e.getAttributeNS(NAMESPACE, "type").equals("sortedMap")) {
					map = new TreeMap();
				}
				for(Element entry : children) {
					List<Element> keyAndValue = Util.getChildElements(entry);
					Object key = unmarshal(Util.getChildElements(keyAndValue.get(0)).get(0));
					Object value = unmarshal(Util.getChildElements(keyAndValue.get(1)).get(0));
					map.put(key, value);
				}
				return map;
			} else if(e.getLocalName().equals("list") || (children.size() > 0 &&
					children.get(0).getLocalName().equals("i"))) { // i = list item
				List list = new LinkedList();
				for(Element item : children) {
					Object itemObj = unmarshal(Util.getChildElements(item).get(0));
					list.add(itemObj);
				}
				return list;
			} 
		}
		Object returnObj = Util.toJaxbObject(e);
		return returnObj;
	}

}