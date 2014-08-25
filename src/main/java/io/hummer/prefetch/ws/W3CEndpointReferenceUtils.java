package io.hummer.prefetch.ws;

/**
 * Version: MPL 1.1/EUPL 1.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at:
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is Copyright The PEPPOL project (http://www.peppol.eu)
 *
 * Alternatively, the contents of this file may be used under the
 * terms of the EUPL, Version 1.1 or - as soon they will be approved
 * by the European Commission - subsequent versions of the EUPL
 * (the "Licence"); You may not use this work except in compliance
 * with the Licence.
 * You may obtain a copy of the Licence at:
 * http://www.osor.eu/eupl/european-union-public-licence-eupl-v.1.1
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 * If you wish to allow use of your version of this file only
 * under the terms of the EUPL License and not to allow others to use
 * your version of this file under the MPL, indicate your decision by
 * deleting the provisions above and replace them with the notice and
 * other provisions required by the EUPL License. If you do not delete
 * the provisions above, a recipient may use your version of this file
 * under either the MPL or the EUPL License.
 */

import java.lang.reflect.Field;
import java.util.List;

import javax.xml.ws.wsaddressing.W3CEndpointReference;
import javax.xml.ws.wsaddressing.W3CEndpointReferenceBuilder;

import org.w3c.dom.Element;

/**
 * As the default WS-Addressing binding since JAXB 2.1 uses the
 * {@link W3CEndpointReference} class, we must also use this class, otherwise
 * JAXB would complain, that there are 2 contexts for the same namespace+element
 * combination.<br>
 * The issue with {@link W3CEndpointReference} is that it can easily be created
 * using the {@link W3CEndpointReferenceBuilder} class, but it's not possible to
 * extract information from it (get....). This class offers a <b>*HACK*</b>
 * workaround by using reflection (incl. setAccessible) to access private fields
 * of {@link W3CEndpointReference}. This was only tested on Sun JDKs, so use at
 * your own risk!!!
 * 
 * @author PEPPOL.AT, BRZ, Philip Helger
 */
public final class W3CEndpointReferenceUtils {
	private static Field s_aFieldAddress;
	private static Field s_aFieldReferenceParameters;
	private static Field s_aFieldAddressURI;
	private static Field s_aFieldElementsElements;

	static {
		// Resolve inner classes
		final String sAddress = W3CEndpointReference.class.getName()
				+ "$Address";
		final String sElements = W3CEndpointReference.class.getName()
				+ "$Elements";
		Class<?> aClassAddress = null;
		Class<?> aClassElements = null;

		// for all inner classes
		for (final Class<?> aClass : W3CEndpointReference.class
				.getDeclaredClasses()) {
			final String sClassName = aClass.getName();
			if (sClassName.equals(sAddress))
				aClassAddress = aClass;
			else if (sClassName.equals(sElements))
				aClassElements = aClass;
		}
		if (aClassAddress == null)
			throw new RuntimeException("Failed to resolve class " + sAddress);
		if (aClassElements == null)
			throw new RuntimeException("Failed to resolve class " + sElements);

		try {
			// Resolve required fields and make them accessible
			s_aFieldAddress = W3CEndpointReference.class
					.getDeclaredField("address");
			// TODO use PrivilegedAction in phloc-commons >= 3.0.1
			s_aFieldAddress.setAccessible(true);

			s_aFieldReferenceParameters = W3CEndpointReference.class
					.getDeclaredField("referenceParameters");
			s_aFieldReferenceParameters.setAccessible(true);

			s_aFieldAddressURI = aClassAddress.getDeclaredField("uri");
			s_aFieldAddressURI.setAccessible(true);

			s_aFieldElementsElements = aClassElements
					.getDeclaredField("elements");
			s_aFieldElementsElements.setAccessible(true);
		} catch (final Throwable t) {
			throw new RuntimeException(
					"Failed to init W3CEndpointReference Fields for reflection");
		}
	}

	private W3CEndpointReferenceUtils() {
	}

	/**
	 * Create a new endpoint reference for the given address without reference
	 * parameters.
	 * 
	 * @param sAddress
	 *            The address to use. May not be <code>null</code>.
	 * @return The non-<code>null</code> endpoint reference for the given
	 *         address
	 */
	public static W3CEndpointReference createEndpointReference(
			final String sAddress) {
		return new W3CEndpointReferenceBuilder().address(sAddress).build();
	}

	/**
	 * Create a new endpoint reference for the given address, using the
	 * specified reference parameters.
	 * 
	 * @param sAddress
	 *            The address to use. May not be <code>null</code>.
	 * @param aReferenceParameters
	 *            The non-<code>null</code> list of reference parameters. May
	 *            not be <code>null</code>.
	 * @return The non-<code>null</code> endpoint reference for the given
	 *         address
	 */
	public static W3CEndpointReference createEndpointReference(
			final String sAddress, final List<Element> aReferenceParameters) {
		W3CEndpointReferenceBuilder aBuilder = new W3CEndpointReferenceBuilder()
				.address(sAddress);
		for (final Element aReferenceParameter : aReferenceParameters) {
			aBuilder = aBuilder.referenceParameter(aReferenceParameter);
		}
		return aBuilder.build();
	}

	/**
	 * Get the address contained in the passed endpoint reference.
	 * 
	 * @param aEndpointReference
	 *            The endpoint reference to retrieve the address from. May not
	 *            be <code>null</code>.
	 * @return The contained address.
	 */
	public static String getAddress(
			final W3CEndpointReference aEndpointReference) {
		try {
			// Get the "address" value of the endpoint reference
			final Object aAddress = s_aFieldAddress.get(aEndpointReference);
			if (aAddress == null)
				return null;

			// Get the "uri" out of the "address" field
			return (String) s_aFieldAddressURI.get(aAddress);
		} catch (final Throwable t) {
			throw new IllegalStateException(t);
		}
	}

	/**
	 * Get a list of all reference parameters contained in the passed endpoint
	 * reference.
	 * 
	 * @param aEndpointReference
	 *            The endpoint reference to retrieve the reference parameters.
	 *            May not be <code>null</code>.
	 * @return A mutable element list
	 */
	@SuppressWarnings("unchecked")
	public static List<Element> getReferenceParameters(
			final W3CEndpointReference aEndpointReference) {
		try {
			// Get the "referenceParameters" value of the endpoint reference
			final Object aReferenceParameters = s_aFieldReferenceParameters
					.get(aEndpointReference);
			if (aReferenceParameters == null)
				return null;

			// Get the "elements" out of the "referenceParameters" field
			return (List<Element>) s_aFieldElementsElements
					.get(aReferenceParameters);
		} catch (final Throwable t) {
			throw new IllegalStateException(t);
		}
	}

	/**
	 * Get the reference parameter at the given index
	 * 
	 * @param aEndpointReference
	 *            The object to retrieve the reference parameter from. May not
	 *            be <code>null</code>.
	 * @param nIndex
	 *            The index to retrieve. Should not be negative.
	 * @return <code>null</code> if the index is invalid
	 */
	public static Element getReferenceParameter(
			final W3CEndpointReference aEndpointReference, final int nIndex) {
		// Get all reference parameters
		final List<Element> aReferenceParameters = getReferenceParameters(aEndpointReference);

		// And extract the one at the desired index.
		return aReferenceParameters.get(nIndex);
	}
}
