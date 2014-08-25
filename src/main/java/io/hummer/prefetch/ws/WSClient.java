package io.hummer.prefetch.ws;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Iterator;

import javax.wsdl.Definition;
import javax.wsdl.WSDLException;
import javax.wsdl.factory.WSDLFactory;
import javax.wsdl.xml.WSDLReader;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPMessage;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.ws.Service;
import javax.xml.ws.wsaddressing.W3CEndpointReference;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.ibm.wsdl.Constants;

public class WSClient {

	public static SOAPEnvelope invokePOST(W3CEndpointReference epr, SOAPEnvelope request) throws IOException {
		String endpointURL = W3CEndpointReferenceUtils.getAddress(epr);
		URL url = new URL(endpointURL);
		URLConnection conn = url.openConnection();
		conn.setDoOutput(true);
		String theRequest = toString(request);
		//System.out.println("sending request: " + theRequest);
		conn.setRequestProperty("Content-Type", "text/xml");
		BufferedWriter w = new BufferedWriter(new OutputStreamWriter(conn.getOutputStream()));
		theRequest = theRequest.trim();
		w.write(theRequest);
		w.close();
		BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
		StringBuilder b = new StringBuilder();
		String temp;
		while((temp = r.readLine()) != null) {
			b.append(temp);
			b.append("\n");
		}
		String originalResult = b.toString();
		String result = originalResult.trim();
		try {
			Element resultElement = toElement(result);
			SOAPEnvelope env = toEnvelope(resultElement);
			return env;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static SOAPEnvelope createEnvelope() {
		try {
			MessageFactory fct = MessageFactory.newInstance();
			SOAPMessage msg = fct.createMessage();
			SOAPEnvelope env = msg.getSOAPPart().getEnvelope();
			return env;
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	public static SOAPEnvelope toEnvelope(Element e) {
		try {
			SOAPEnvelope env = createEnvelope();
			NodeList list = e.getChildNodes();
			Document doc = env.getBody().getOwnerDocument();
			env.removeContents();
			for(int i = 0; i < list.getLength(); i ++) {
				Node n = doc.importNode(list.item(i), true);
				env.appendChild(n);
			}
			return env;
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	public static SOAPEnvelope createEnvelopeFromBody(Element e) {
		try {
			SOAPEnvelope env = createEnvelope();
			e = (Element)env.getBody().getOwnerDocument().importNode(e, true);
			env.getBody().appendChild(e);
			return env;
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	public static String toString(SOAPEnvelope env) {
	    try {
	    	Transformer transformer = TransformerFactory.newInstance().newTransformer();
		    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		    ByteArrayOutputStream bos = new ByteArrayOutputStream();
		    transformer.transform(new DOMSource(env), new StreamResult(bos));
		    return bos.toString();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	public static Element toElement(String string) throws Exception {
		if(string == null || string.trim().isEmpty())
			return null;
		Document d = null;
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		DocumentBuilder builder = factory.newDocumentBuilder();
		d = builder.parse(new InputSource(new StringReader(string)));
		return d.getDocumentElement();
	}

	@SuppressWarnings("all")
	public static <T> T createClientJaxws(Class<T> serviceToWrap, URL wsdlLocation) {
		try {
			QName serviceName = getSingleServiceName(wsdlLocation.toString());
			Service s = Service.create(wsdlLocation, serviceName);
			T serv = s.getPort(serviceToWrap);
			return serv;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
    }

    public static QName getSingleServiceName(String serviceWSDL) throws Exception {
    	return getSingleServiceName(readWsdl(serviceWSDL, true));
	}
    public static QName getSingleServiceName(Definition serviceWSDL) throws Exception {
    	Iterator<?> iter = serviceWSDL.getServices().keySet().iterator();
    	QName result = null;
    	while(iter.hasNext()) {
    		if(result != null)
    			throw new RuntimeException("Ambiguity: WSDL contains more than one service elements: " + serviceWSDL);
    		result = (QName)iter.next();
    	}
		return result;
	}

    public static Definition readWsdl(String wsdlUrl, boolean resolveImports) {
        return readWsdl(wsdlUrl, false, resolveImports);
    }
    private static Definition readWsdl(String wsdlUrl, boolean verbose, boolean resolveImports) {
        try {
            WSDLFactory factory = WSDLFactory.newInstance();
            WSDLReader reader = factory.newWSDLReader();
            reader.setFeature(Constants.FEATURE_VERBOSE, verbose);
            reader.setFeature(Constants.FEATURE_IMPORT_DOCUMENTS, resolveImports);
            return reader.readWSDL(wsdlUrl);
        } catch (WSDLException ex) {
        	throw new RuntimeException(ex);
        }
    }

}
