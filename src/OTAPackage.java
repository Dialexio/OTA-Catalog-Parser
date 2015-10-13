/*
 * OTA Catalog Parser 0.3.2
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
	private final NSDictionary ENTRY;
	private final String BUILD, PREREQ_BUILD, PREREQ_VER, URL; 
	private final String REGEX_STRING_BUILD_UP_TO_LETTER = "(\\d)?\\d[A-M]";// All "betas" have a 5 after the letter.

	private NSObject[] supportedDeviceModels = null, supportedDevices;
	private String date, size;

	public OTAPackage(NSDictionary entry) {
		BUILD = entry.get("Build").toString();
		Matcher timestamp;
		this.ENTRY = entry;
		final Pattern timestampRegex = Pattern.compile("\\d{4}(\\-|\\.)\\d{7}(\\d)?");
		supportedDevices = ((NSArray)entry.objectForKey("SupportedDevices")).getArray();

		// Obtain the prerequisite build and prerequisite version.
		if (entry.containsKey("PrerequisiteBuild")) {
			PREREQ_BUILD = entry.get("PrerequisiteBuild").toString();
			PREREQ_VER = (entry.containsKey("PrerequisiteOSVersion")) ? entry.get("PrerequisiteOSVersion").toString() : "N/A";
		}
		else {
			PREREQ_BUILD = "N/A";
			PREREQ_VER = "N/A";
		}

		// Retrieve the list of supported models... if it exists.
		if (entry.containsKey("SupportedDeviceModels"))
			supportedDeviceModels = ((NSArray)entry.objectForKey("SupportedDeviceModels")).getArray();

		// Obtaining the size and URL.
		// First, we need to make sure we don't get info for a dummy update.
		if (entry.containsKey("RealUpdateAttributes")) {
			final NSDictionary realUpdateAttrs = (NSDictionary)entry.get("RealUpdateAttributes");

			size = realUpdateAttrs.get("RealUpdateDownloadSize").toString();
			URL = realUpdateAttrs.get("RealUpdateURL").toString();
		}
		else {
			size = entry.get("_DownloadSize").toString();
			URL = ((NSString)entry.get("__BaseURL")).getContent() + ((NSString)entry.get("__RelativePath")).getContent();
		}

		// Extract the date from the URL.
		// This is not 100% accurate information, especially with releases like 8.0, 8.1, 8.2, etc., but better than nothing.
		timestamp = timestampRegex.matcher(URL);
		while (timestamp.find()) {
			date = timestamp.group().substring(5);
			break;
		}

		if (date.length() == 7)
			date = date.substring(0, 6) + "0" + date.substring(6);
	}

	public String build() {
		return ENTRY.get("Build").toString();
	}

	public String date() {
		return date;
	}

	public boolean isBeta() {
		final Pattern REGEX_BETA_CHECKER = Pattern.compile(REGEX_STRING_BUILD_UP_TO_LETTER+"5");
		final Matcher BETA_MATCH = REGEX_BETA_CHECKER.matcher(BUILD);

		return BETA_MATCH.find();
	}

	public boolean isUniversal() {
		return (PREREQ_VER.equals("N/A") && PREREQ_BUILD.equals("N/A"));
	}

	public String osVersion() {
		return ENTRY.get("OSVersion").toString();
	}

	public String prerequisiteBuild() {
		return PREREQ_BUILD;
	}

	public String prerequisiteVer() {
		return PREREQ_VER;
	}

	public String size() {
		return size = NumberFormat.getNumberInstance(Locale.US).format(Integer.parseInt(size));
	}

	// sortingBuild and sortingPrerequisiteBuild are used for sorting.
	// There are extra zeros in the front for both, and an
	// extra 9 after the first build letter for non-betas.
	public String sortingBuild() {
		String sortBuild = BUILD;

		//Make 9A### appear before 10A###.
		if (Character.isLetter(sortBuild.charAt(1)))
			sortBuild = "0" + sortBuild;

		if (!isBeta()) {
			final Pattern betaRegex = Pattern.compile(REGEX_STRING_BUILD_UP_TO_LETTER);
			final Matcher buildUpToLetter = betaRegex.matcher(sortBuild);
			String afterLetter, upToLetter = "";

			while (buildUpToLetter.find()) {
				upToLetter = buildUpToLetter.group();
				break;
			}
			afterLetter = sortBuild.replaceFirst(REGEX_STRING_BUILD_UP_TO_LETTER, "");

			sortBuild = upToLetter + "9" + afterLetter;
		}
		return sortBuild;
	}

	public String sortingPrerequisiteBuild() {
		if (PREREQ_BUILD.equals("N/A"))
			return "0000000000"; // Bump this to the top.
		else
			return (Character.isLetter(PREREQ_BUILD.charAt(1))) ? "0" + PREREQ_BUILD : PREREQ_BUILD;
	}

	public NSObject[] supportedDeviceModels() {
		return supportedDeviceModels;
	}

	public NSObject[] supportedDevices() {
		return supportedDevices;
	}

	public String url() {
		return URL;
	}
}
