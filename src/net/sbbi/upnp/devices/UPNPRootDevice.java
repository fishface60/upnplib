/*
 * This software copyright by various authors including the RPTools.net
 * development team, and licensed under the LGPL Version 3 or, at your option,
 * any later version.
 * 
 * Portions of this software were originally covered under the Apache Software
 * License, Version 1.1 or Version 2.0.
 * 
 * See the file LICENSE elsewhere in this distribution for license details.
 */

package net.sbbi.upnp.devices;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import net.sbbi.upnp.JXPathParser;
import net.sbbi.upnp.services.UPNPService;

import org.apache.commons.jxpath.Container;
import org.apache.commons.jxpath.JXPathContext;
import org.apache.commons.jxpath.JXPathException;
import org.apache.commons.jxpath.Pointer;
import org.apache.commons.jxpath.xml.DocumentContainer;
import org.apache.log4j.Logger;

/**
 * Root UPNP device that is contained in a device definition file. Slightly
 * differs from a simple UPNPDevice object. This object will contains all the
 * child devices, this is the top objet in the UPNP device devices hierarchy.
 * 
 * @author <a href="mailto:superbonbon@sbbi.net">SuperBonBon</a>
 * @version 1.0
 */

public class UPNPRootDevice extends UPNPDevice {
	private final static Logger log = Logger.getLogger(UPNPRootDevice.class);

	private final int specVersionMajor;
	private final int specVersionMinor;
	private URL URLBase;
	private long validityTime;
	private long creationTime;
	private final URL deviceDefLoc;
	private String deviceDefLocData;
	private String vendorFirmware;
	private String discoveryUSN;
	private String discoveryUDN;

	private final DocumentContainer UPNPDevice;

	/**
	 * Constructor for the root device, constructs itself from An xml device
	 * definition file provided by the UPNP device via http normally.
	 * 
	 * @param deviceDefLoc
	 *            the location of the XML device definition file using
	 *            "the urn:schemas-upnp-org:device-1-0" namespace
	 * @param maxAge
	 *            the maximum age of this UPNP device in secs before considered
	 *            to be outdated
	 * @param vendorFirmware
	 *            the vendor firmware
	 * @param discoveryUSN
	 *            the discovery USN used to find and create this device
	 * @param discoveryUDN
	 *            the discovery UDN used to find and create this device
	 * @throws MalformedURLException
	 *             if the location URL is invalid and cannot be used to populate
	 *             this root object and its child devices IllegalStateException
	 *             if the device has an unsupported version, currently only
	 *             version 1.0 is supported
	 */
	public UPNPRootDevice(URL deviceDefLoc, String maxAge, String vendorFirmware, String discoveryUSN, String discoveryUDN) throws MalformedURLException, IllegalStateException {
		this(deviceDefLoc, maxAge);
		this.vendorFirmware = vendorFirmware;
		this.discoveryUSN = discoveryUSN;
		this.discoveryUDN = discoveryUDN;
	}

	/**
	 * Constructor for the root device, constructs itself from An xml device
	 * definition file provided by the UPNP device via http normally.
	 * 
	 * @param deviceDefLoc
	 *            the location of the XML device definition file using
	 *            "the urn:schemas-upnp-org:device-1-0" namespace
	 * @param maxAge
	 *            the maximum age of this UPNP device in secs before considered
	 *            to be outdated
	 * @param vendorFirmware
	 *            the vendor firmware
	 * @throws MalformedURLException
	 *             if the location URL is invalid and cannot be used to populate
	 *             this root object and its child devices IllegalStateException
	 *             if the device has an unsupported version, currently only
	 *             version 1.0 is supported
	 */
	public UPNPRootDevice(URL deviceDefLoc, String maxAge, String vendorFirmware) throws MalformedURLException, IllegalStateException {
		this(deviceDefLoc, maxAge);
		this.vendorFirmware = vendorFirmware;
	}

	/**
	 * Constructor for the root device, constructs itself from An xml device
	 * definition file provided by the UPNP device via http normally.
	 * 
	 * @param deviceDefLoc
	 *            the location of the XML device definition file using
	 *            "the urn:schemas-upnp-org:device-1-0" namespace
	 * @param maxAge
	 *            the maximum age in secs of this UPNP device before considered
	 *            to be outdated
	 * @throws MalformedURLException
	 *             if the location URL is invalid and cannot be used to populate
	 *             this root object and its child devices IllegalStateException
	 *             if the device has an unsupported version, currently only
	 *             version 1.0 is supported
	 */
	public UPNPRootDevice(URL deviceDefLoc, String maxAge) throws MalformedURLException, IllegalStateException {
		this.deviceDefLoc = deviceDefLoc;
		DocumentContainer.registerXMLParser(DocumentContainer.MODEL_DOM, new JXPathParser());
		UPNPDevice = new DocumentContainer(deviceDefLoc, DocumentContainer.MODEL_DOM);
		validityTime = Integer.parseInt(maxAge) * 1000;
		creationTime = System.currentTimeMillis();

		JXPathContext context = JXPathContext.newContext(this);
		context.registerNamespace("upnp", "urn:schemas-upnp-org:device-1-0");
		Pointer rootPtr = null;
		try {
			rootPtr = context.getPointer("UPNPDevice/upnp:root");
		} catch (Exception ex) {
			// handled below as rootPtr will be null
		}
		if (rootPtr == null)
			throw new IllegalStateException("Unsupported device; no 'UPNPDevice/upnp:root' element");
		JXPathContext rootCtx = context.getRelativeContext(rootPtr);

		specVersionMajor = Integer.parseInt((String) rootCtx.getValue("upnp:specVersion/upnp:major"));
		specVersionMinor = Integer.parseInt((String) rootCtx.getValue("upnp:specVersion/upnp:minor"));

		if (!(specVersionMajor == 1)) {
			throw new IllegalStateException("Unsupported device version (" + specVersionMajor + "." + specVersionMinor + ")");
		}
		boolean buildURLBase = true;
		String base = null;
		try {
			base = (String) rootCtx.getValue("upnp:URLBase");
			if (base != null && base.trim().length() > 0) {
				URLBase = new URL(base);
				if (log.isDebugEnabled())
					log.debug("device specified URLBase as " + URLBase);
				buildURLBase = false;
			}
		} catch (JXPathException ex) {
			// URLBase is not mandatory
			// we assume we use the URL of the device
		} catch (MalformedURLException malformedEx) {
			// crappy urlbase provided
			log.warn("Error occured while parsing device baseURL '" + base + "'; building it from device default location instead", malformedEx);
		}
		if (buildURLBase) {
			String URL = deviceDefLoc.getProtocol() + "://" + deviceDefLoc.getHost() + ":" + deviceDefLoc.getPort();
			String path = deviceDefLoc.getPath();
			if (path != null) {
				int lastSlash = path.lastIndexOf('/');
				if (lastSlash != -1) {
					URL += path.substring(0, lastSlash);
				}
			}
			URLBase = new URL(URL);
		}
		Pointer devicePtr = rootCtx.getPointer("upnp:device");
		JXPathContext deviceCtx = rootCtx.getRelativeContext(devicePtr);

		fillUPNPDevice(this, null, deviceCtx, URLBase);
	}

	/**
	 * The validity time for this device in milliseconds,
	 * 
	 * @return the number of milliseconds remaining before the device object
	 *         that has been build is considered to be outdated, after this
	 *         delay the UPNP device should resend an advertisement message or a
	 *         negative value if the device is outdated
	 */
	public long getValidityTime() {
		long elapsed = System.currentTimeMillis() - creationTime;
		return validityTime - elapsed;
	}

	/**
	 * Resets the device validity time
	 * 
	 * @param newMaxAge
	 *            the maximum age in secs of this UPNP device before considered
	 *            to be outdated
	 */
	public void resetValidityTime(String newMaxAge) {
		validityTime = Integer.parseInt(newMaxAge) * 1000;
		creationTime = System.currentTimeMillis();
	}

	/**
	 * Retreives the device description file location
	 * 
	 * @return an URL
	 */
	public URL getDeviceDefLoc() {
		return deviceDefLoc;
	}

	public int getSpecVersionMajor() {
		return specVersionMajor;
	}

	public int getSpecVersionMinor() {
		return specVersionMinor;
	}

	public String getVendorFirmware() {
		return vendorFirmware;
	}

	public String getDiscoveryUSN() {
		return discoveryUSN;
	}

	public String getDiscoveryUDN() {
		return discoveryUDN;
	}

	/**
	 * URL base acces
	 * 
	 * @return URL the URL base, or null if the device does not provide such
	 *         information
	 */
	public URL getURLBase() {
		return URLBase;
	}

	/**
	 * Parsing an UPNPdevice description element (<device>) in the description
	 * XML file
	 * 
	 * @param device
	 *            the device object that will be populated
	 * @param parent
	 *            the device parent object
	 * @param deviceCtx
	 *            an XPath context for object population
	 * @param baseURL
	 *            the base URL of the UPNP device
	 * @throws MalformedURLException
	 *             if some URL provided in the description file is invalid
	 */
	private void fillUPNPDevice(UPNPDevice device, UPNPDevice parent, JXPathContext deviceCtx, URL baseURL) throws MalformedURLException {
		device.deviceType = getMandatoryData(deviceCtx, "upnp:deviceType");
		if (log.isDebugEnabled())
			log.debug("parsing device " + device.deviceType);
		device.friendlyName = getMandatoryData(deviceCtx, "upnp:friendlyName");
		device.manufacturer = getNonMandatoryData(deviceCtx, "upnp:manufacturer");
		String base = getNonMandatoryData(deviceCtx, "upnp:manufacturerURL");
		try {
			if (base != null)
				device.manufacturerURL = new URL(base);
		} catch (java.net.MalformedURLException ex) {
			// crappy data provided, keep the field null
		}
		try {
			device.presentationURL = getURL(getNonMandatoryData(deviceCtx, "upnp:presentationURL"), URLBase);
		} catch (java.net.MalformedURLException ex) {
			// crappy data provided, keep the field null
		}
		device.modelDescription = getNonMandatoryData(deviceCtx, "upnp:modelDescription");
		device.modelName = getMandatoryData(deviceCtx, "upnp:modelName");
		device.modelNumber = getNonMandatoryData(deviceCtx, "upnp:modelNumber");
		device.modelURL = getNonMandatoryData(deviceCtx, "upnp:modelURL");
		device.serialNumber = getNonMandatoryData(deviceCtx, "upnp:serialNumber");
		device.UDN = getMandatoryData(deviceCtx, "upnp:UDN");
		device.USN = UDN.concat("::").concat(deviceType);
		String tmp = getNonMandatoryData(deviceCtx, "upnp:UPC");
		if (tmp != null) {
			try {
				device.UPC = Long.parseLong(tmp);
			} catch (Exception ex) {
				// non all numeric field provided, non upnp compliant device
			}
		}
		device.parent = parent;

		// Fill icons list first, since it will be in the XML data
		// stream first (well, probably ;-))
		fillUPNPDeviceIconsList(device, deviceCtx, URLBase);
		fillUPNPServicesList(device, deviceCtx);

		Pointer deviceListPtr;
		try {
			deviceListPtr = deviceCtx.getPointer("upnp:deviceList");
		} catch (JXPathException ex) {
			// no pointers for this device list, this can happen
			// if the device has no child devices, simply return
			return;
		}
		JXPathContext deviceListCtx = deviceCtx.getRelativeContext(deviceListPtr);
		Double arraySize = (Double) deviceListCtx.getValue("count( upnp:device )");
		if (log.isDebugEnabled())
			log.debug("child devices count is " + arraySize);

		device.childDevices = new ArrayList<UPNPDevice>(arraySize.intValue());
		for (int idx = 1; idx <= arraySize.intValue(); idx++) {
			Pointer devicePtr = deviceListCtx.getPointer("upnp:device[" + idx + "]");
			JXPathContext childDeviceCtx = deviceListCtx.getRelativeContext(devicePtr);
			UPNPDevice childDevice = new UPNPDevice();
			fillUPNPDevice(childDevice, device, childDeviceCtx, baseURL);
			if (log.isDebugEnabled())
				log.debug("adding child device " + childDevice.getDeviceType());
			device.childDevices.add(childDevice);
		}
	}

	private String getMandatoryData(JXPathContext ctx, String ctxFieldName) {
		String value = (String) ctx.getValue(ctxFieldName);
		if (value != null && value.length() == 0) {
			throw new JXPathException("Mandatory field " + ctxFieldName + " not provided, uncompliant UPNP device !!");
		}
		return value;
	}

	private String getNonMandatoryData(JXPathContext ctx, String ctxFieldName) {
		String value = null;
		try {
			value = (String) ctx.getValue(ctxFieldName);
			if (value != null && value.length() == 0) {
				value = null;
			}
		} catch (JXPathException ex) {
			value = null;
		}
		return value;
	}

	/**
	 * Parsing an UPNPdevice services list element (<device/serviceList>) in the
	 * description XML file
	 * 
	 * @param device
	 *            the device object that will store the services list
	 *            (UPNPService) objects
	 * @param deviceCtx
	 *            an XPath context for object population
	 * @throws MalformedURLException
	 *             if some URL provided in the description file for a service
	 *             entry is invalid
	 */
	private void fillUPNPServicesList(UPNPDevice device, JXPathContext deviceCtx) throws MalformedURLException {
		Pointer serviceListPtr = deviceCtx.getPointer("upnp:serviceList");
		if (serviceListPtr == null) {
			return;
		}
		JXPathContext serviceListCtx = deviceCtx.getRelativeContext(serviceListPtr);
		Double arraySize = (Double) serviceListCtx.getValue("count( upnp:service )");
		if (log.isDebugEnabled())
			log.debug("device services count is " + arraySize);

		device.services = new ArrayList<UPNPService>(arraySize.intValue());
		for (int idx = 1; idx <= arraySize.intValue(); idx++) {
			Pointer servicePtr = serviceListCtx.getPointer("upnp:service[" + idx + "]");
			JXPathContext serviceCtx = serviceListCtx.getRelativeContext(servicePtr);
			// TODO possibility of bugs if deviceDefLoc contains a file name
			URL base = URLBase != null ? URLBase : deviceDefLoc;
			UPNPService service = new UPNPService(serviceCtx, base, this);
			device.services.add(service);
		}
	}

	/**
	 * Parsing an UPNPdevice icons list element (<device/iconList>) in the
	 * description XML file. This list can be null.
	 * 
	 * @param device
	 *            the device object that will store the icons list (DeviceIcon)
	 *            objects
	 * @param deviceCtx
	 *            an XPath context for object population
	 * @throws MalformedURLException
	 *             if some URL provided in the description file for an icon URL
	 */
	private void fillUPNPDeviceIconsList(UPNPDevice device, JXPathContext deviceCtx, URL baseURL) throws MalformedURLException {
		Pointer iconListPtr;
		try {
			iconListPtr = deviceCtx.getPointer("upnp:iconList");
		} catch (JXPathException ex) {
			// no pointers for icons list, this can happen; simply return
			return;
		}
		JXPathContext iconListCtx = deviceCtx.getRelativeContext(iconListPtr);
		Double arraySize = (Double) iconListCtx.getValue("count( upnp:icon )");
		if (log.isDebugEnabled())
			log.debug("device icons count is " + arraySize);
		device.deviceIcons = new ArrayList<DeviceIcon>();
		for (int idx = 1; idx <= arraySize.intValue(); idx++) {
			DeviceIcon ico = new DeviceIcon();
			if (true) {
				JXPathContext elemCtx = iconListCtx.getRelativeContext(iconListCtx.getPointer("upnp:icon[" + idx + "]"));
				ico.mimeType = (String) elemCtx.getValue("upnp:mimetype");
				ico.width = Integer.parseInt((String) elemCtx.getValue("upnp:width"));
				ico.height = Integer.parseInt((String) elemCtx.getValue("upnp:height"));
				ico.depth = Integer.parseInt((String) elemCtx.getValue("upnp:depth"));
				ico.url = getURL((String) elemCtx.getValue("upnp:url"), baseURL);
//			} else {
//				ico.mimeType = (String) iconListCtx.getValue("upnp:icon[" + i + "]/upnp:mimetype");
//				ico.width = Integer.parseInt((String) iconListCtx.getValue("upnp:icon[" + i + "]/upnp:width"));
//				ico.height = Integer.parseInt((String) iconListCtx.getValue("upnp:icon[" + i + "]/upnp:height"));
//				ico.depth = Integer.parseInt((String) iconListCtx.getValue("upnp:icon[" + i + "]/upnp:depth"));
//				ico.url = getURL((String) iconListCtx.getValue("upnp:icon[" + i + "]/upnp:url"), baseURL);
			}
			if (log.isDebugEnabled())
				log.debug("icon URL is " + ico.url);
			device.deviceIcons.add(ico);
		}
	}

	/**
	 * Parsing an URL from the descriptionXML file
	 * 
	 * @param url
	 *            the string representation fo the URL
	 * @param baseURL
	 *            the base device URL, needed if the url param is relative
	 * @return an URL object defining the url param
	 * @throws MalformedURLException
	 *             if the url param or baseURL.toExternalForm() + url cannot be
	 *             parsed to create an URL object
	 */
	public final static URL getURL(String url, URL baseURL) throws MalformedURLException {
		URL rtrVal;
		if (url == null || url.trim().length() == 0)
			return null;
		try {
			rtrVal = new URL(url);
		} catch (MalformedURLException malEx) {
			// maybe that the url is relative, we add the baseURL and reparse it
			// if relative then we take the device baser url root and add the url
			if (baseURL != null) {
				url = url.replace('\\', '/');
				if (url.charAt(0) != '/') {
					// the path is relative to the device baseURL
					String externalForm = baseURL.toExternalForm();
					if (!externalForm.endsWith("/")) {
						externalForm += "/";
					}
					rtrVal = new URL(externalForm + url);
				} else {
					// the path is not relative
					String URLRoot = baseURL.getProtocol() + "://" + baseURL.getHost() + ":" + baseURL.getPort();
					rtrVal = new URL(URLRoot + url);
				}
			} else {
				throw malEx;
			}
		}
		return rtrVal;
	}

	/**
	 * Retrieves the device definition XML data
	 * 
	 * @return the device definition XML data as a String
	 */
	public String getDeviceDefLocData() {
		if (deviceDefLocData == null) {
			try {
				java.io.InputStream in = deviceDefLoc.openConnection().getInputStream();
				int readen = 0;
				byte[] buff = new byte[512];
				StringBuffer strBuff = new StringBuffer();
				while ((readen = in.read(buff)) != -1) {
					strBuff.append(new String(buff, 0, readen));
				}
				in.close();
				deviceDefLocData = strBuff.toString();
			} catch (IOException ioEx) {
				return null;
			}
		}
		return deviceDefLocData;
	}

	/**
	 * Used for JXPath parsing, do not use this method
	 * 
	 * @return a Container object for Xpath parsing capabilities
	 */
	public Container getUPNPDevice() {
		return UPNPDevice;
	}
}
