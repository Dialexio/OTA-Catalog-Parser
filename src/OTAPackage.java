/*
 * OTA Catalog Parser 0.2
 * Copyright (c) 2015 Dialexio
 * 
 * The MIT License (MIT)
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
import com.dd.plist.*;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.regex.*;

class OTAPackage {
	private boolean isUniversal = true;
	private NSDictionary entry;
	private NSObject[] supportedDeviceModels = null, supportedDevices;
	private String build, date, prereqBuild = "", prereqVer, size, url;

	public OTAPackage(NSDictionary entry) {
		build = entry.get("Build").toString();
		this.entry = entry;
		Matcher timestamp;
		Pattern timestampRegex = Pattern.compile("\\d{4}(\\-|\\.)\\d{8}");
		supportedDevices = ((NSArray)entry.objectForKey("SupportedDevices")).getArray();

		// Obtain the prerequisite build and prerequisite version.
		if (entry.containsKey("PrerequisiteBuild")) {
			prereqBuild = entry.get("PrerequisiteBuild").toString();
			prereqVer = (entry.containsKey("PrerequisiteOSVersion")) ? entry.get("PrerequisiteOSVersion").toString() : "{{n/a}}";
			isUniversal = false;
		}

		// Retrieve the list of supported models... if it exists.
		if (entry.containsKey("SupportedDeviceModels"))
			supportedDeviceModels = ((NSArray)entry.objectForKey("SupportedDeviceModels")).getArray();

		// Obtaining the size and URL.
		// First, we need to make sure we don't get info for a dummy update.
		if (entry.containsKey("RealUpdateAttributes")) {
			NSDictionary realUpdateAttrs = (NSDictionary)entry.get("RealUpdateAttributes");

			size = realUpdateAttrs.get("RealUpdateDownloadSize").toString();
			url = realUpdateAttrs.get("RealUpdateURL").toString();
		}
		else {
			size = entry.get("_DownloadSize").toString();
			url = ((NSString)entry.get("__BaseURL")).getContent() + ((NSString)entry.get("__RelativePath")).getContent();
		}

		// Extract the date from the URL.
		// This is not 100% accurate information, especially with releases like 8.0, 8.1, 8.2, etc., but better than nothing.
		timestamp = timestampRegex.matcher(url);
		while (timestamp.find()) {
			date = timestamp.group().substring(5);
			break;
		}
	}

	public String build() {
		return entry.get("Build").toString();
	}

	public String date() {
		return date;
	}

	public boolean isBeta() {
		return (entry.containsKey("ReleaseType") && entry.get("ReleaseType").toString().equals("Beta"));
	}

	public boolean isUniversal() {
		return isUniversal;
	}

	public String osVersion() {
		return entry.get("OSVersion").toString();
	}

	public String prerequisiteBuild() {
		return prereqBuild;
	}

	public String prerequisiteVer() {
		return prereqVer;
	}

	public String size() {
		return size = NumberFormat.getNumberInstance(Locale.US).format(Integer.parseInt(size));
	}

	// sortingBuild and sortingPrerequisiteBuild are used for sorting.
	// (They're what they sound like, but with more zeroes.)
	public String sortingBuild() {
		return (Character.isLetter(build.charAt(1))) ? "0" + build : build;
	}

	public String sortingPrerequisiteBuild() {
		if (prereqBuild.isEmpty())
			return "0000000000"; // Bump this to the top.
		else
			return (Character.isLetter(prereqBuild.charAt(1))) ? "0" + prereqBuild : prereqBuild;
	}

	public NSObject[] supportedDeviceModels() {
		return supportedDeviceModels;
	}

	public NSObject[] supportedDevices() {
		return supportedDevices;
	}

	public String url() {
		return url;
	}
}
