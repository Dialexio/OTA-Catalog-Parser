/*
 * OTA Catalog Parser 0.3.3
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
	private final String REGEX_BETA = "(\\d)?\\d[A-M]5\\d{3}[a-z]";
	private final String REGEX_BUILD_UP_TO_LETTER = "(\\d)?\\d[A-M]";
	private final String REGEX_FIVEK = "5\\d{3}";

	private Matcher matchFinder;
	private NSObject[] supportedDeviceModels = null, supportedDevices;
	private String buildLeftSide, date, size;

	public OTAPackage(NSDictionary entry) {
		BUILD = entry.get("Build").toString();
		buildLeftSide = "";
		this.ENTRY = entry;
		supportedDevices = ((NSArray)entry.objectForKey("SupportedDevices")).getArray();

		final Pattern timestampRegex = Pattern.compile("\\d{4}(\\-|\\.)\\d{7}(\\d)?");

		// Get the build number up to (and including) the first letter.
		final Pattern buildToLetterRegex = Pattern.compile(REGEX_BUILD_UP_TO_LETTER);
		matchFinder = buildToLetterRegex.matcher(BUILD);
		while (matchFinder.find()) {
			buildLeftSide = matchFinder.group();
			break;
		}

		// Obtain the prerequisite build and prerequisite version.
		if (entry.containsKey("PrerequisiteBuild")) {
			PREREQ_BUILD = entry.get("PrerequisiteBuild").toString();

			//We need a conditional here because 6.0 on the iPhone 5 excludes PrerequisiteOSVersion.
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
			final NSDictionary REAL_UPDATE_ATTRS = (NSDictionary)entry.get("RealUpdateAttributes");

			size = REAL_UPDATE_ATTRS.get("RealUpdateDownloadSize").toString();
			URL = REAL_UPDATE_ATTRS.get("RealUpdateURL").toString();
		}
		else {
			size = entry.get("_DownloadSize").toString();
			URL = ((NSString)entry.get("__BaseURL")).getContent() + ((NSString)entry.get("__RelativePath")).getContent();
		}

		// Extract the date from the URL.
		// This is not 100% accurate information, especially with releases like 8.0, 8.1, 8.2, etc., but better than nothing.
		matchFinder = timestampRegex.matcher(URL);
		while (matchFinder.find()) {
			date = matchFinder.group().substring(5);
			break;
		}

		if (date.length() == 7)
			date = date.substring(0, 6) + "0" + date.substring(6);
	}

	// When a firmware reaches a final release, Apple creates an entry
	// that is the build number with 5000 added to the end number
	// in order to push devices with the beta firmware to the final
	// release. This subtracts the 5000.
	public String actualBuild() {
		if (this.declaredBeta() && !BUILD.matches(REGEX_BETA)) {
			final Pattern FIVE_THOUSAND_BUILDNUM = Pattern.compile(REGEX_FIVEK);
			matchFinder = FIVE_THOUSAND_BUILDNUM.matcher(BUILD);
			String minusFiveThousand = "";

			while (matchFinder.find()) {
				minusFiveThousand = matchFinder.group();
				break;
			}

			return buildLeftSide + (Integer.parseInt(minusFiveThousand) - 5000);
		}

		else
			return BUILD;
	}

	public String date() {
		return date;
	}

	public boolean declaredBeta() {
		final Pattern REGEX_BETA_CHECKER = Pattern.compile(REGEX_BUILD_UP_TO_LETTER + REGEX_FIVEK);
		matchFinder = REGEX_BETA_CHECKER.matcher(BUILD);

		return matchFinder.find();
	}

	public String declaredBuild() {
		return BUILD;
	}

	public boolean isBeta() {
		final Pattern REGEX_BETA_CHECKER = Pattern.compile(REGEX_BETA);
		matchFinder = REGEX_BETA_CHECKER.matcher(BUILD);

		return matchFinder.find();
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

		if (!declaredBeta()) {
			final Pattern betaRegex = Pattern.compile(REGEX_BUILD_UP_TO_LETTER);
			matchFinder = betaRegex.matcher(sortBuild);
			String afterLetter, upToLetter = "";

			while (matchFinder.find()) {
				upToLetter = matchFinder.group();
				break;
			}
			afterLetter = sortBuild.replaceFirst(REGEX_BUILD_UP_TO_LETTER, "");

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
