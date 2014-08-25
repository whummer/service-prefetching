package io.hummer.prefetch.sim.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class Util {

	private static final List<Class<?>> defaultJaxbContextClasses = new LinkedList<Class<?>>();
	private static JAXBContext defaultJaxbContext;

	public static List<String> readFile(String file) {
		try {
			return readStream(new FileInputStream(file));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	public static String concat(List<String> lines) {
		StringBuilder b = new StringBuilder();
		int count = 0;
		for(String line : lines) {
			b.append(line);
			if(count < lines.size() - 1) {
				b.append("\n");
			}
		}
		return b.toString();
 	}
	public static String readFile(InputStream stream) {
		return concat(readStream(stream));
 	}
	public static List<String> readStream(InputStream stream) {
		List<String> result = new LinkedList<>();
		try {
			BufferedReader r = new BufferedReader(new InputStreamReader(
					stream));
			String tmp = null;
			while((tmp = r.readLine()) != null) {
				result.add(tmp);
			}
			r.close();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return result;
	}

	public static void storeStringGzipped(String file, String str) {
		try {
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
					new GzipCompressorOutputStream(new FileOutputStream(file))));
			bw.write(str);
			bw.close();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	public static void storeObjectGzipped(String file, Object obj) {
		try {
			ObjectOutputStream oos = new ObjectOutputStream(
					new GzipCompressorOutputStream(new FileOutputStream(file)));
			oos.writeObject(obj);
			oos.close();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static <T> T loadGzippedObject(String file) {
		try {
			ObjectInputStream ois = new ObjectInputStream(
					new GzipCompressorInputStream(new FileInputStream(file)));
			@SuppressWarnings("unchecked")
			T t = (T)ois.readObject();
			ois.close();
			return t;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	public static String loadStringFromGzip(String file) {
		try {
			return Util.readFile(
					new GzipCompressorInputStream(
							new FileInputStream(file)));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	// --------------------------------------------------------------------------
	// Conversion functions.
	// --------------------------------------------------------------------------

	/* 
	 * Swiss Coordinates, conversion based on
	 * http://www.giangrandi.ch/soft/swissgrid/swissgrid.shtml
	 * */
	public static double[] convertSwissToGPS(double east, double north, double hgt) {
		east -= 600000.0;											 // Convert origin to "civil" system, where Bern has coordinates 0,0.
		north -= 200000.0;

		east /= (float)1E6;												// Express distances in 1000km units.
		north /= (float)1E6;

		double lon = 2.6779094;										// Calculate longitude in 10000" units.
		lon += 4.728982 * east;
		lon += 0.791484 * east * north;
		lon += 0.1306 * east * north * north;
		lon -= 0.0436 * east * east * east;

		double lat = 16.9023892;									   // Calculate latitude in 10000" units.
		lat += 3.238272 * north;
		lat -= 0.270978 * east * east;
		lat -= 0.002528 * north * north;
		lat -= 0.0447 * east * east * north;
		lat -= 0.0140 * north * north * north;

		hgt += 49.55;											   // Convert height [m].
		hgt -= 12.60 * east;
		hgt -= 22.64 * north;

		lon *= 100.0 / 36.0;											// Convert longitude and latitude back in degrees.
		lat *= 100.0 / 36.0;

		return new double[]{lat, lon, hgt};
	}

	public static double toRadian(double degrees) {
		return degrees * Math.PI / 180.0;
	}

	public static String toString(Object jaxbObject) {
		return toString(jaxbObject, false);
	}

	public static String toString(Object jaxbObject, boolean indent) {
		return toString(toElement(jaxbObject), indent);
	}

	public static String toString(Element element) {
		return toString(element, false);
	}
	public static String toString(Element element, boolean indent) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			Transformer tr = TransformerFactory.newInstance().newTransformer();
			tr.setOutputProperty(OutputKeys.METHOD, "xml");
			if(indent) {
				tr.setOutputProperty(
						"{http://xml.apache.org/xslt}indent-amount", "2");
				tr.setOutputProperty(OutputKeys.INDENT, "yes");
			} else {
				tr.setOutputProperty(OutputKeys.INDENT, "no");
			}
			tr.transform(new DOMSource(element), new StreamResult(baos));
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
		String string = new String(baos.toByteArray());
		try {
			string = string
					.replaceAll(
							"<\\?xml version=\"1\\.0\" (encoding=\".*\")?( )*\\?>(\n)*",
							"");
		} catch (OutOfMemoryError e) {
			/* swallow */
		}
		return string;
	}
	public static Element toElement(Object jaxbObject) {
		if(jaxbObject == null)
			return null;
		if(jaxbObject instanceof Element)
			return (Element) jaxbObject;
		try {
			if(jaxbObject instanceof String)
				return toElement((String) jaxbObject);
			return toElement(jaxbObject,
					getJaxbContext(jaxbObject.getClass(), true));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static JAXBContext getJaxbContext(Class<?> jaxbClass,
			boolean doCacheContext) {
		try {
			if(doCacheContext) {
				synchronized(defaultJaxbContextClasses) {
					if(!defaultJaxbContextClasses.contains(jaxbClass)) {
						defaultJaxbContextClasses.add(jaxbClass);
						defaultJaxbContext = JAXBContext
								.newInstance(defaultJaxbContextClasses
										.toArray(new Class[0]));
					}
				}
				return defaultJaxbContext;
			} else {
				return JAXBContext.newInstance(jaxbClass);
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static Element toElement(Object jaxbObject, JAXBContext ctx)
			throws Exception {
		if(jaxbObject == null)
			return null;
		Element result = newDocument().createElement("result");
		try {
			ctx.createMarshaller().marshal(jaxbObject, result);
		} catch(Exception e) {
			throw e;
		}
		return (Element) result.getFirstChild();
	}

	public static Element toElement(String string) throws Exception {
		if(string == null || string.trim().isEmpty())
			return null;
		Document d = null;
		DocumentBuilder builder = newDocumentBuilder();
		//System.out.println("parsing " + string);
		d = builder.parse(new InputSource(new StringReader(string)));
		return d.getDocumentElement();
	}

	public static Document newDocument() throws Exception {
		DocumentBuilder builder = newDocumentBuilder();
		return builder.newDocument();
	}

	public static DocumentBuilder newDocumentBuilder()
			throws ParserConfigurationException {
		DocumentBuilderFactory factory = getDocBuilderFactory();
		DocumentBuilder temp = factory.newDocumentBuilder();
		return temp;
	}

	private static DocumentBuilderFactory getDocBuilderFactory() {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		return factory;
	}

	public static List<Element> getChildElements(Element e) {
		return getChildElements(e, null);
	}

	public static List<Element> getChildElements(Element e, String name) {
		if(e == null)
			return null;
		List<Element> result = new LinkedList<Element>();
		NodeList list = e.getChildNodes();
		for(int i = 0; i < list.getLength(); i++) {
			Node n = list.item(i);
			if(n instanceof Element) {
				if(name == null || name.equals(((Element) n).getLocalName())
						|| ((Element) n).getLocalName().endsWith(":" + name)) {
					result.add((Element) n);
				}
			}
		}
		return result;
	}

	@SuppressWarnings("all")
	public static <T> T toJaxbObject(Class<T> jaxbClass, Element element)
			throws Exception {
		return toJaxbObject(jaxbClass, element, true);
	}

	@SuppressWarnings("all")
	public static <T> T toJaxbObject(Class<T> jaxbClass, Element element,
			boolean doCacheContext) throws Exception {
		JAXBContext ctx = getJaxbContext(jaxbClass, doCacheContext);
		return (T) ctx.createUnmarshaller().unmarshal(element);
	}

	@SuppressWarnings("all")
	public static <T> T toJaxbObject(Element element,
			Class<?>... jaxbClasses) throws Exception {
		JAXBContext ctx = JAXBContext.newInstance(jaxbClasses);
		return (T) ctx.createUnmarshaller().unmarshal(element);
	}

	public static void write(File file, String content) {
		try {
			FileOutputStream fos = new FileOutputStream(file);
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos));
			bw.write(content);
			bw.close();
			fos.close();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public static void write(String file, String content) {
		write(new File(file), content);
	}

	public static void dumpStackTrace() {
		System.out.println(Arrays.asList(
				Thread.getAllStackTraces().get(Thread.currentThread())).
				toString().replace(",", "\n"));
	}
}
