package com.emergya.wifieapconfigurator;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.hotspot2.PasspointConfiguration;
import android.net.wifi.hotspot2.pps.HomeSp;
import android.net.wifi.hotspot2.pps.Credential;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import androidx.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;


import com.getcapacitor.JSObject;
import com.getcapacitor.NativePlugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;

import org.json.JSONException;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

import static androidx.core.content.PermissionChecker.checkSelfPermission;

@NativePlugin(
		permissions = {
				Manifest.permission.ACCESS_WIFI_STATE,
				Manifest.permission.CHANGE_WIFI_STATE,
				Manifest.permission.ACCESS_FINE_LOCATION
		})
public abstract class Android {

	private WifiManager wifiManager;

	public List configureAP(PluginCall call, Context context) throws JSONException {
		String[] ssids = new String[call.getArray("ssid").length()];
		boolean res = true;

		String[] oids = new String[call.getArray("oid").length()];
		if (call.getArray("oid").length() != 0 && call.getArray("oid").get(0) != "") {
			List aux = call.getArray("oid").toList();
			for (int i = 0 ; i < aux.size() ; i++) {
				oids[i] = aux.get(i).toString();
			}
		}

		if((call.getArray("ssid").length() == 0 || call.getArray("ssid").get(0) == "") && oids.length == 0) {
			JSObject object = new JSObject();
			object.put("success", false);
			object.put("message", "plugin.wifieapconfigurator.error.ssid.missing");
			call.success(object);
			res = false;
		}

		if (call.getArray("ssid").length() != 0 && call.getArray("ssid").get(0) != "") {
			List aux = call.getArray("ssid").toList();
			for (int i = 0 ; i < aux.size() ; i++) {
				ssids[i] = aux.get(i).toString();
			}
		}

		String clientCertificate = null;
		if (call.getString("clientCertificate") != null && !call.getString("clientCertificate").equals("")) {
			clientCertificate = call.getString("clientCertificate");
		}

		String passPhrase = null;
		if (call.getString("passPhrase") != null && !call.getString("passPhrase").equals("")) {
			passPhrase = call.getString("passPhrase");
		}

		String anonymousIdentity = null;
		if (call.getString("anonymous") != null && !call.getString("anonymous").equals("")) {
			anonymousIdentity = call.getString("anonymous");
		}

		String[] caCertificate = new String[call.getArray("caCertificate").length()];
		if (call.getArray("caCertificate").length() != 0 && call.getArray("caCertificate").get(0) != "") {
			List aux = call.getArray("caCertificate").toList();
			for (int i = 0 ; i < aux.size() ; i++) {
				caCertificate[i] = aux.get(i).toString();
			}
		}

		Integer eap = getEapMethod(call.getInt("eap"));
		if (eap == null) {
			JSObject object = new JSObject();
			object.put("success", false);
			object.put("message", "plugin.wifieapconfigurator.error.eap.invalid");
			call.success(object);
			res = false;
		}

		String[] servername = new String[call.getArray("servername").length()];
		if (call.getArray("servername").length() != 0 && call.getArray("servername").get(0) != "") {
			List aux = call.getArray("servername").toList();
			for (int i = 0 ; i < aux.size() ; i++) {
				servername[i] = aux.get(i).toString();
			}
		}

		String username = null;
		String password = null;
		Integer auth = null;

		String id = null;
		if (call.getString("id") != null && !call.getString("id").equals("")) {
			id = call.getString("id");
		}
		String displayName = null;
		if (call.getString("displayName") != null && !call.getString("displayName").equals("")) {
			displayName = call.getString("displayName");
		}

		if (clientCertificate == null && passPhrase == null) {
			if (call.getString("username") != null && !call.getString("username").equals("")) {
				username = call.getString("username");
			} else {
				JSObject object = new JSObject();
				object.put("success", false);
				object.put("message", "plugin.wifieapconfigurator.error.username.missing");
				call.success(object);
				res = false;
			}

			if (call.getString("password") != null && !call.getString("password").equals("")) {
				password = call.getString("password");
			} else {
				JSObject object = new JSObject();
				object.put("success", false);
				object.put("message", "plugin.wifieapconfigurator.error.password.missing");
				call.success(object);
				res = false;
			}


			auth = getAuthMethod(call.getInt("auth"));
			if (auth == null) {
				JSObject object = new JSObject();
				object.put("success", false);
				object.put("message", "plugin.wifieapconfigurator.error.auth.invalid");
				call.success(object);
				res = false;
			}
		}

		for(String ssid : ssids) {
			try {
				removeNetwork(ssid, context);
			} catch (Throwable _) {
				/* ignore exceptions when removing the network,
				 * since many Android versions don't let us remove them,
				 * but allow us to override them
				 */
			}
		}

		if (res) {
			for (int i = 0 ; i < ssids.length ; i++) {
				res = getNetworkAssociated(context, call, ssids[i]);
			}
		}

		List parameters = new ArrayList();

		if (res) {
			parameters = connectAP(ssids, username, password, servername, caCertificate, clientCertificate, passPhrase, eap, auth, anonymousIdentity, displayName, id, oids, call);
			parameters.add(ssids);
			parameters.add(oids);
			parameters.add(displayName);
			parameters.add(id);
			return parameters;
		}
		return null;
	}

	public List connectAP(String[] ssids, String username, String password, String[] servernames, String[] caCertificates, String clientCertificate, String passPhrase,
				   Integer eap, Integer auth, String anonymousIdentity, String displayName, String id, String[] oids, PluginCall call) {

		WifiEnterpriseConfig enterpriseConfig = new WifiEnterpriseConfig();

		enterpriseConfig.setAnonymousIdentity(anonymousIdentity);

		if (servernames.length != 0) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				enterpriseConfig.setDomainSuffixMatch(getLongestSuffix(servernames));
				enterpriseConfig.setAltSubjectMatch("DNS:" + String.join(";DNS:", servernames));
			}
		} else {
			JSObject object = new JSObject();
			object.put("success", false);
			object.put("message", "plugin.wifieapconfigurator.error.ca.missing");
			call.success(object);
		}

		enterpriseConfig.setEapMethod(eap);

		CertificateFactory certFactory = null;
		X509Certificate[] caCerts = null;
		List<X509Certificate> certificates = new ArrayList<X509Certificate>();
		// building the certificates
		for (String certString : caCertificates) {
			byte[] bytes = Base64.decode(certString, Base64.NO_WRAP);
			ByteArrayInputStream b = new ByteArrayInputStream(bytes);

			try {
				certFactory = CertificateFactory.getInstance("X.509");
				X509Certificate certificate = (X509Certificate) certFactory.generateCertificate(b);
				boolean[] usage = certificate.getKeyUsage();
				// https://docs.oracle.com/javase/7/docs/api/java/security/cert/X509Certificate.html#getKeyUsage()
				// 5 is KeyUsage keyCertSign, which indicates the certificate is a CA
				if (usage[5]) certificates.add(certificate);
				// We really shouldn't expect any certificate here to NOT be a CA,
				// CAT shows a nice red warning when you try to configure this,
				// but experience shows that sometimes this is not enough of a deterrent.
				// We may very well block profiles like this, but then it should be done BEFORE
				// the user enters their username/password, not after.
			} catch (CertificateException e) {
				JSObject object = new JSObject();
				object.put("success", false);
				object.put("message", "plugin.wifieapconfigurator.error.ca.invalid");
				call.success(object);
			} catch (IllegalArgumentException e) {
				JSObject object = new JSObject();
				object.put("success", false);
				object.put("message", "plugin.wifieapconfigurator.error.ca.invalid");
				call.success(object);
			}
		}
		try {
			enterpriseConfig.setCaCertificates(certificates.toArray(new X509Certificate[certificates.size()]));
		} catch (IllegalArgumentException e) {
			JSObject object = new JSObject();
			object.put("success", false);
			object.put("message", "plugin.wifieapconfigurator.error.ca.invalid");
			call.success(object);
		}

		X509Certificate cert = null;
		PrivateKey key = null;

		// Explicitly reset client certificate, will set later if needed
		enterpriseConfig.setClientKeyEntry(null, null);

		if (eap != WifiEnterpriseConfig.Eap.TLS) {
			enterpriseConfig.setIdentity(username);
			enterpriseConfig.setPassword(password);

			enterpriseConfig.setPhase2Method(auth);

		} else {
			// Explicitly unset unused fields
			enterpriseConfig.setPassword("");
			enterpriseConfig.setPhase2Method(WifiEnterpriseConfig.Phase2.NONE);

			// For TLS, "identity" is used for outer identity,
			// while for PEAP/TTLS, "identity" is the inner identity,
			// and anonymousIdentity is the outer identity
			// - so we have to do some weird shuffling here.
			enterpriseConfig.setIdentity(anonymousIdentity);

			KeyStore pkcs12ks = null;
			try {
				pkcs12ks = KeyStore.getInstance("pkcs12");

				byte[] bytes = Base64.decode(clientCertificate, Base64.NO_WRAP);
				ByteArrayInputStream b = new ByteArrayInputStream(bytes);
				InputStream in = new BufferedInputStream(b);
				try {
					pkcs12ks.load(in, passPhrase.toCharArray());
				} catch(Exception e) {
					JSObject object = new JSObject();
					object.put("success", false);
					object.put("message", "plugin.wifieapconfigurator.error.passphrase.null");
					call.success(object);
				}

				Enumeration<String> aliases = pkcs12ks.aliases();

				while (aliases.hasMoreElements()) {
					String alias = aliases.nextElement();
					cert = (X509Certificate) pkcs12ks.getCertificate(alias);
					key = (PrivateKey) pkcs12ks.getKey(alias, passPhrase.toCharArray());
					enterpriseConfig.setClientKeyEntry(key, cert);
				}

			} catch (KeyStoreException e) {
				sendClientCertificateError(e, call);
				e.printStackTrace();
			} catch (NoSuchAlgorithmException e) {
				sendClientCertificateError(e, call);
				e.printStackTrace();
			} catch (UnrecoverableKeyException e) {
				sendClientCertificateError(e, call);
				e.printStackTrace();
			}
		}

		PasspointConfiguration config = this.createPasspointConfig(id, displayName, oids, enterpriseConfig, key);;

		List configs = new ArrayList();
		configs.add(enterpriseConfig);
		configs.add(config);
		return configs;
	}

	public void validatePassPhrase(PluginCall call) throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {

		String clientCertificate = call.getString("certificate");
		String passPhrase = call.getString("passPhrase");

		if (clientCertificate == null || passPhrase == null) {
			JSObject object = new JSObject();
			object.put("success", false);
			object.put("message", "plugin.wifieapconfigurator.error.passphrase.validation");
			call.success(object);
			return;
		}

		KeyStore pkcs12ks = KeyStore.getInstance("pkcs12");

		byte[] bytes = Base64.decode(clientCertificate, Base64.NO_WRAP);
		ByteArrayInputStream b = new ByteArrayInputStream(bytes);
		InputStream in = new BufferedInputStream(b);

		try {
			pkcs12ks.load(in, passPhrase.toCharArray());
			JSObject object = new JSObject();
			object.put("success", true);
			object.put("message", "plugin.wifieapconfigurator.success.passphrase.validation");
			call.success(object);
		} catch(Exception e) {
			JSObject object = new JSObject();
			object.put("success", false);
			object.put("message", "plugin.wifieapconfigurator.error.passphrase.validation");
			call.success(object);
		}

	}

	private PasspointConfiguration createPasspointConfig(String id, String displayName, String[] oid, WifiEnterpriseConfig enterpriseConfig, PrivateKey key) {
		PasspointConfiguration config = new PasspointConfiguration();

		HomeSp homeSp = new HomeSp();
		homeSp.setFqdn(enterpriseConfig.getDomainSuffixMatch());

		if (displayName != null) {
			homeSp.setFriendlyName(displayName);
		} else {
			homeSp.setFriendlyName(id + " via Passpoint");
		}

		long[] roamingConsortiumOIDs = new long[oid.length];
		int index = 0;
		for (String roamingConsortiumOIDString : oid) {
			if (!roamingConsortiumOIDString.startsWith("0x")) {
				roamingConsortiumOIDString = "0x" + roamingConsortiumOIDString;
			}
			roamingConsortiumOIDs[index] = Long.decode(roamingConsortiumOIDString);
			index++;
		}
		homeSp.setRoamingConsortiumOis(roamingConsortiumOIDs);

		config.setHomeSp(homeSp);
		Credential cred = new Credential();
		cred.setRealm(id);
		cred.setCaCertificate(enterpriseConfig.getCaCertificate());

		switch(enterpriseConfig.getEapMethod()) {
			case WifiEnterpriseConfig.Eap.TLS:
				Credential.CertificateCredential certCred = new Credential.CertificateCredential();
				certCred.setCertType("x509v3");
				cred.setClientPrivateKey(key);
				cred.setClientCertificateChain(enterpriseConfig.getClientCertificateChain());
				certCred.setCertSha256Fingerprint(getFingerprint(enterpriseConfig.getClientCertificateChain()[0]));
				cred.setCertCredential(certCred);
				break;
			case WifiEnterpriseConfig.Eap.PEAP:
			case WifiEnterpriseConfig.Eap.TTLS:
			case WifiEnterpriseConfig.Eap.PWD:
				byte[] data = new byte[0];
				try {
					data = enterpriseConfig.getPassword().getBytes("UTF-8");
				} catch (UnsupportedEncodingException e) {
					e.printStackTrace();
				}
				String base64 = Base64.encodeToString(data, Base64.DEFAULT);

				Credential.UserCredential us = new Credential.UserCredential();
				us.setUsername(enterpriseConfig.getIdentity());
				us.setPassword(base64);
				us.setEapType(21);
				switch(enterpriseConfig.getPhase2Method()) {
					// Strings from android.net.wifi.hotspot2.pps.Credential.UserCredential.AUTH_METHOD_*
					case WifiEnterpriseConfig.Phase2.MSCHAPV2: us.setNonEapInnerMethod("MS-CHAP-V2"); break;
					case WifiEnterpriseConfig.Phase2.PAP: us.setNonEapInnerMethod("PAP"); break;
					case WifiEnterpriseConfig.Phase2.MSCHAP: us.setNonEapInnerMethod("MS-CHAP"); break;
					// Do we need a default case here?
				}
				cred.setUserCredential(us);
				break;
			default:
		}

		config.setCredential(cred);

		return config;
	}


	@RequiresApi(api = Build.VERSION_CODES.Q)
	public abstract List connectNetwork(Context context, String ssid, WifiEnterpriseConfig enterpriseConfig, PluginCall call, PasspointConfiguration config, String displayName, String id, Activity activity);

	private void sendClientCertificateError(Exception e, PluginCall call) {
		JSObject object = new JSObject();
		object.put("success", false);
		object.put("message", "plugin.wifieapconfigurator.error.clientCertificate.invalid - " + e.getMessage());
		call.success(object);
		Log.e("error", e.getMessage());
	}

	public void removeNetwork(Context context, PluginCall call) {
		String ssid = call.getString("ssid");
		JSObject object = new JSObject();

		if (null == ssid || "".equals(ssid)) {
			object.put("success", false);
			object.put("message", "plugin.wifieapconfigurator.error.ssid.missing");
			call.success(object);
		} else if (removeNetwork(ssid, context)) {
			object.put("success", true);
			object.put("message", "plugin.wifieapconfigurator.success.network.removed");
			call.success(object);
		} else {
			object.put("success", false);
			object.put("message", "plugin.wifieapconfigurator.success.network.missing");
			call.success(object);
		}
	}
	public boolean removeNetwork(String ssid, Context context) {
		boolean res = false;
		WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);

		/*if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) { */
		List<WifiConfiguration> configuredNetworks = wifi.getConfiguredNetworks();
		for (WifiConfiguration conf : configuredNetworks) {
			if (conf.SSID.equals(ssid) || conf.SSID.equals("\"" + ssid + "\"")) { // TODO document why ssid can be surrounded by quotes
				wifi.removeNetwork(conf.networkId);
				wifi.saveConfiguration();
				res = true;
			}
		}
		/*} else {
			wifi.removeNetworkSuggestions(new ArrayList<WifiNetworkSuggestion>());
			res = true;
		}*/

		return res;
	}

	WifiManager getWifiManager(Context context) {
		if (wifiManager == null) {
			wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
		}
		return wifiManager;
	}

	public void enableWifi(Context context, PluginCall call) {
		//if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
		WifiManager wifiManager = getWifiManager(context);
		if (wifiManager.setWifiEnabled(true)) {
			JSObject object = new JSObject();
			object.put("success", true);
			object.put("message", "plugin.wifieapconfigurator.success.wifi.enabled");
			call.success(object);
		} else {
			JSObject object = new JSObject();
			object.put("success", false);
			object.put("message", "plugin.wifieapconfigurator.error.wifi.disabled");
			call.success(object);
		}
		/*} else{
			JSObject object = new JSObject();
			object.put("success", false);
			object.put("message", "plugin.wifieapconfigurator.error.wifi.disabled");
			call.success(object);
		}*/
	}

	public boolean isNetworkAssociated(Context context, PluginCall call) {
		String ssid = null;
		boolean res = false, isOverridable = false;

		//if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
		if (call.getString("ssid") != null && !call.getString("ssid").equals("")) {
			ssid = call.getString("ssid");
		} else {
			JSObject object = new JSObject();
			object.put("success", false);
			object.put("message", "plugin.wifieapconfigurator.error.ssid.missing");
			call.success(object);
			return res;
		}

		WifiManager wifi = getWifiManager(context);

		List<WifiConfiguration> configuredNetworks = wifi.getConfiguredNetworks();
		for (WifiConfiguration conf : configuredNetworks) {
			if (conf.SSID.equals(ssid) || conf.SSID.equals("\"" + ssid + "\"")) { // TODO document why ssid can be surrounded by quotes

				String packageName = context.getPackageName();
				if (conf.toString().toLowerCase().contains(packageName.toLowerCase())) { // TODO document why case insensitive
					isOverridable = true;
				}

				JSObject object = new JSObject();
				object.put("success", false);
				object.put("message", "plugin.wifieapconfigurator.error.network.alreadyAssociated");
				object.put("overridable", isOverridable);
				call.success(object);
				res = true;
				break;
			}
		}

		if (!res) {
			JSObject object = new JSObject();
			object.put("success", true);
			object.put("message", "plugin.wifieapconfigurator.success.network.missing");
			call.success(object);
		}
		/*} else{
			JSObject object = new JSObject();
			object.put("success", false);
			object.put("message", "plugin.wifieapconfigurator.error.ssid.missing");
			call.success(object);
		}*/

		return res;
	}

	public void reachableSSID(Context context, Activity activity, PluginCall call) {
		String ssid = null;
		boolean isReachable = false;
		if (call.getString("ssid") != null && !call.getString("ssid").equals("")) {
			ssid = call.getString("ssid");
		} else {
			JSObject object = new JSObject();
			object.put("success", false);
			object.put("message", "plugin.wifieapconfigurator.error.ssid.missing");
			call.success(object);
		}

		boolean granted = getPermission(Manifest.permission.ACCESS_FINE_LOCATION, context, activity);

		LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
		boolean location = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);

		if (!location) {
			JSObject object = new JSObject();
			object.put("success", false);
			object.put("message", "plugin.wifieapconfigurator.error.location.disabled");
			call.success(object);
		} else if (granted) {

			WifiManager wifiManager = getWifiManager(context);
			Iterator<ScanResult> results = wifiManager.getScanResults().iterator();

			while (isReachable == false && results.hasNext()) {
				ScanResult s = results.next();
				if (s.SSID.equals(ssid) || s.SSID.equals("\"" + ssid + "\"")) { // TODO document why ssid can be surrounded by quotes
					isReachable = true;
				}
			}

			String message = isReachable ? "plugin.wifieapconfigurator.success.network.reachable" : "plugin.wifieapconfigurator.error.network.notReachable";

			JSObject object = new JSObject();
			object.put("success", true);
			object.put("message", message);
			object.put("isReachable", isReachable);
			call.success(object);
		} else {
			JSObject object = new JSObject();
			object.put("success", false);
			object.put("message", "plugin.wifieapconfigurator.error.permission.notGranted");
			call.success(object);
		}
	}

	public void isConnectedSSID(Context context, Activity activity, PluginCall call) {
		String ssid = null;
		boolean isConnected = false;
		if (call.getString("ssid") != null && !call.getString("ssid").equals("")) {
			ssid = call.getString("ssid");
		} else {
			JSObject object = new JSObject();
			object.put("success", false);
			object.put("message", "plugin.wifieapconfigurator.error.ssid.missing");
			call.success(object);
		}

		boolean granted = getPermission(Manifest.permission.ACCESS_FINE_LOCATION, context, activity);

		LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
		boolean location = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);

		if (!location) {
			JSObject object = new JSObject();
			object.put("success", false);
			object.put("message", "plugin.wifieapconfigurator.error.location.disabled");
			call.success(object);
		} else if (granted) {
			WifiManager wifiManager = getWifiManager(context);
			WifiInfo info = wifiManager.getConnectionInfo();
			String currentlySsid = info.getSSID();
			if (currentlySsid != null && (currentlySsid.equals("\"" + ssid + "\"") || currentlySsid.equals(ssid))) { // TODO document why ssid can be surrounded by quotes
				isConnected = true;
			}

			String message = isConnected ? "plugin.wifieapconfigurator.success.network.connected" : "plugin.wifieapconfigurator.error.network.notConnected";

			JSObject object = new JSObject();
			object.put("success", true);
			object.put("message", message);
			object.put("isConnected", isConnected);
			call.success(object);
		} else {
			JSObject object = new JSObject();
			object.put("success", false);
			object.put("message", "plugin.wifieapconfigurator.error.permission.notGranted");
			call.success(object);
		}

	}

	private boolean getNetworkAssociated(Context context, PluginCall call, String ssid) {
		boolean res = true, isOverridable = false;

		//if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
		WifiManager wifi = getWifiManager(context);
		List<WifiConfiguration> configuredNetworks = wifi.getConfiguredNetworks();

		for (WifiConfiguration conf : configuredNetworks) {
			if (conf.SSID.equals(ssid) || conf.SSID.equals("\"" + ssid + "\"")) { // TODO document why ssid can be surrounded by quotes
				String packageName = context.getPackageName();
				if (conf.toString().toLowerCase().contains(packageName.toLowerCase())) { // TODO document why case insensitive
					isOverridable = true;
				}

				JSObject object = new JSObject();
				object.put("success", false);
				object.put("message", "plugin.wifieapconfigurator.error.network.alreadyAssociated");
				object.put("overridable", isOverridable);
				call.success(object);
				res = false;
				break;
			}
		}
		//}
		return res;
	}

	public boolean checkEnabledWifi(Context context, PluginCall call) {
		boolean res = true;
		WifiManager wifi = getWifiManager(context);

		if (!wifi.isWifiEnabled()) {
			JSObject object = new JSObject();
			object.put("success", false);
			object.put("message", "plugin.wifieapconfigurator.error.wifi.disabled");
			call.success(object);
			res = false;
		}
		return res;
	}

	private Integer getEapMethod(Integer eap) {
		switch (eap) {
			case 13: return WifiEnterpriseConfig.Eap.TLS;
			case 21: return WifiEnterpriseConfig.Eap.TTLS;
			case 25: return WifiEnterpriseConfig.Eap.PEAP;
			default: return null;
		}
	}

	private Integer getAuthMethod(Integer auth) {
		if (auth == null) {
			return WifiEnterpriseConfig.Phase2.MSCHAPV2;
		}
		switch (auth) {
			case -1: return WifiEnterpriseConfig.Phase2.PAP;
			case -2: return WifiEnterpriseConfig.Phase2.MSCHAP;
			case -3:
			case 26: /* Android cannot do TTLS-EAP-MSCHAPv2, we expect the ionic code to not let it happen, but if it does, try TTLS-MSCHAPv2 instead */
				// This currently DOES happen because CAT has a bug where it reports TTLS-MSCHAPv2 as TTLS-EAP-MSCHAPv2,
				// so denying this would prevent profiles from being sideloaded
				return WifiEnterpriseConfig.Phase2.MSCHAPV2;
			/*
			case _:
				return WifiEnterpriseConfig.Phase2.GTC;
			*/
			default: return null;
		}
	}

	boolean getPermission(String permission, Context context, Activity activity) {
		boolean res = true;
		if (!(checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)) {
			res = false;
			ActivityCompat.requestPermissions(activity, new String[]{permission}, 123);
		}

		return res;
	}

	private void verifyCaCert(X509Certificate caCert)
			throws GeneralSecurityException, IOException {
		CertificateFactory factory = CertificateFactory.getInstance("X.509");
		CertPathValidator validator =
				CertPathValidator.getInstance(CertPathValidator.getDefaultType());
		CertPath path = factory.generateCertPath(Arrays.asList(caCert));
		KeyStore ks = KeyStore.getInstance("AndroidCAStore");
		ks.load(null, null);
		PKIXParameters params = new PKIXParameters(ks);
		params.setRevocationEnabled(false);
		validator.validate(path, params);
	}

	private byte[] getFingerprint(X509Certificate certChain) {

		MessageDigest digester = null;
		byte[] fingerprint = null;
		try {
			digester = MessageDigest.getInstance("SHA-256");
			digester.reset();
			fingerprint = digester.digest(certChain.getEncoded());
		} catch (NoSuchAlgorithmException | CertificateEncodingException e) {
			e.printStackTrace();
		}
		return fingerprint;
	}

	private static String getLongestSuffix(String[] strings) {
		if (strings.length == 0) return "";
		if (strings.length == 1) return strings[0];
		String longest = strings[0];
		for(String candidate : strings) {
			int pos = candidate.length();
			do {
				pos = candidate.lastIndexOf('.', pos - 2) + 1;
			} while (pos > 0 && longest.endsWith(candidate.substring(pos)));
			if (!longest.endsWith(candidate.substring(pos))) {
				pos = candidate.indexOf('.', pos);
			}
			if (pos == -1) {
				longest = "";
			} else if (longest.endsWith(candidate.substring(pos))) {
				longest = candidate.substring(pos == 0 ? 0 : pos + 1);
			}
		}
		return longest;
	}

	public void sendNotification(Context context, PluginCall call) throws JSONException {
		String stringDate = call.getString("date");
		String title = call.getString("title");
		String message = call.getString("message");
		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putString("date", stringDate);
		editor.putString("title", title);
		editor.putString("message", message);
		editor.apply();
		StartNotifications.enqueueWorkStart(context, new Intent());
	}

	public void writeToSharedPref(Context context, PluginCall call) {
		String data = call.getString("id");

		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
		SharedPreferences.Editor editor = sharedPref.edit();
		editor.putString("institutionId", data);
		editor.apply();
		JSObject object = new JSObject();
		object.put("success", true);
		object.put("message", "plugin.wifieapconfigurator.success.writing");
		call.success(object);
	}

	public void readFromSharedPref(Context context, PluginCall call) {
		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
		String ret = sharedPref.getString("institutionId", "");
		if (ret != "") {
			JSObject object = new JSObject();
			object.put("success", true);
			object.put("message", "plugin.wifieapconfigurator.success.reading");
			object.put("id", ret);
			call.success(object);
		} else {
			JSObject object = new JSObject();
			object.put("success", false);
			object.put("message", "plugin.wifieapconfigurator.error.reading");
			call.success(object);
		}
	}

	public void checkIfOpenThroughNotifications(Activity activity, PluginCall call) {
		Boolean openFromNot;
		if (activity.getComponentName().getClassName().contains("MainActivity")) {
			openFromNot = false;
		} else {
			openFromNot = true;
		}
		JSObject object = new JSObject();
		object.put("fromNotification", openFromNot);
		call.success(object);
	}
}