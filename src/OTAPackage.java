/*
 * OTA Catalog Parser
 * Copyright (c) 2016 Dialexio
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
	private final String BUILD, BUILD_LEFT, DOC_ID, PREREQ_BUILD, PREREQ_VER, URL,
		REGEX_BUILD_AFTER_LETTER = "[4-6]\\d{3}",
		REGEX_BUILD_UP_TO_LETTER = "(\\d)?\\d[A-Z]",
		REGEX_BETA = REGEX_BUILD_UP_TO_LETTER + REGEX_BUILD_AFTER_LETTER + "[a-z]?";
	private Matcher match;
	private NSObject[] supportedDeviceModels = null, supportedDevices;
	private String date, size;

	public OTAPackage(NSDictionary otaEntry) {
		ENTRY = otaEntry;
		otaEntry = null;

		BUILD = ENTRY.get("Build").toString();
		DOC_ID = ENTRY.containsKey("SUDocumentationID") ? ENTRY.get("SUDocumentationID").toString() : "0Seed";
		supportedDevices = ((NSArray)ENTRY.objectForKey("SupportedDevices")).getArray();

		final Pattern timestampRegex = Pattern.compile("\\d{4}(\\-|\\.)\\d{7}(\\d)?");

		// Get the build number up to (and including) the first letter.
		final Pattern buildToLetterRegex = Pattern.compile(REGEX_BUILD_UP_TO_LETTER);
		match = buildToLetterRegex.matcher(BUILD);
		if (match.find())
			BUILD_LEFT = match.group();
		else
			BUILD_LEFT = "";

		// Obtain the prerequisite build and prerequisite version.
		if (ENTRY.containsKey("PrerequisiteBuild")) {
			PREREQ_BUILD = ENTRY.get("PrerequisiteBuild").toString();

			// Thanks for making these conditionals a thing, 6.0 build 10A444.
			if (ENTRY.containsKey("PrerequisiteOSVersion"))
				PREREQ_VER = ENTRY.get("PrerequisiteOSVersion").toString();

			else if (BUILD.equals("10A444"))
				PREREQ_VER = "6.0";

			else
				PREREQ_VER = "N/A";
		}
		else {
			PREREQ_BUILD = "N/A";
			PREREQ_VER = "N/A";
		}

		// Retrieve the list of supported models... if it exists.
		if (ENTRY.containsKey("SupportedDeviceModels"))
			supportedDeviceModels = ((NSArray)ENTRY.objectForKey("SupportedDeviceModels")).getArray();

		// Obtaining the size and URL.
		// First, we need to make sure we don't get info for a dummy update.
		if (ENTRY.containsKey("RealUpdateAttributes")) {
			final NSDictionary REAL_UPDATE_ATTRS = (NSDictionary)ENTRY.get("RealUpdateAttributes");

			size = REAL_UPDATE_ATTRS.get("RealUpdateDownloadSize").toString();
			URL = REAL_UPDATE_ATTRS.get("RealUpdateURL").toString();
		}
		else {
			size = ENTRY.get("_DownloadSize").toString();
			URL = ((NSString)ENTRY.get("__BaseURL")).getContent() + ((NSString)ENTRY.get("__RelativePath")).getContent();
		}

		// Extract the date from the URL.
		// This is not 100% accurate information, especially with releases like 8.0, 8.1, 8.2, etc., but better than nothing.
		match = timestampRegex.matcher(URL);
		date = (match.find()) ? match.group().substring(5) : "Not Available";

		if (date.length() == 7)
			date = date.substring(0, 6) + '0' + date.substring(6);
	}

	/**
	 * Returns the actual build number of the OTA entry.
	 * Sometimes, Apple likes to add a large number to the end.
	 * 
	 * @return The build number that iOS will report in Settings.
     **/
	public String actualBuild() {
		String actualBuild = "";

		// If it the build number looks like a beta...
		// And it's labeled as a beta...
		// But it's not a beta... We need to get the actual build number.
		if (BUILD.matches(REGEX_BETA) && this.isDeclaredBeta() && this.betaType() == 0) {
			final Pattern BUILDNUM_AFTER_LETTER = Pattern.compile(REGEX_BUILD_AFTER_LETTER);
			match = BUILDNUM_AFTER_LETTER.matcher(BUILD);

			if (match.find()) {
				// Subtract the value that Apple added to the actual build number.
				if (Integer.parseInt(match.group()) > 6000)
					actualBuild = BUILD_LEFT + (Integer.parseInt(match.group()) - 6000);

				else
					actualBuild = BUILD_LEFT + (Integer.parseInt(match.group()) - 5000);

				// If there was a letter on the end (usually for Apple TV), put it back.
				if (Character.isLowerCase(BUILD.charAt(BUILD.length()-1)))
					actualBuild = actualBuild + BUILD.charAt(BUILD.length()-1);
			}

			// Not sure how anyone would get to this, but...
			else
				actualBuild = BUILD;

			return actualBuild;
		}
		
		else
			return BUILD;
	}

	/**
	 * Returns the beta number of this entry.
	 * If a beta number cannot be determined, returns 0.
	 * 
	 * @return Whatever number beta this is, as an int.
     **/
	public int betaNumber() {
		if (this.betaType() > 0 && !DOC_ID.equals("PreRelease")) {
			final char digit = DOC_ID.charAt(DOC_ID.length()-1);

			return (Character.isDigit(digit)) ? Integer.parseInt(digit + "") : 1;
		}

		else
			return 0;
	}

	/**
	 * Checks if the release is a developer beta, a public beta, or not a beta.
	 * 
	 * @return An integer value of 0 (not a beta), 1 (public beta), 2 (developer beta), 3 (carrier beta), or 4 (internal build).
     **/
	public int betaType() {
		boolean beta = false;
		Pattern regex = Pattern.compile("\\d(DevBeta|PublicBeta|Seed)");
		match = regex.matcher(DOC_ID);

		if (ENTRY.containsKey("ReleaseType")) {
			regex = null;

			switch (ENTRY.get("ReleaseType").toString()) {
				// If the build is labeled "Beta," we still need to check if it's actually a beta. 
				case "Beta":
					break;

				case "Carrier":
					return 3;

				case "Internal":
					return 4;

				default:
					System.err.println("Unknown ReleaseType: "+ ENTRY.get("ReleaseType").toString());
					return -1;
			}
		}

		if (match.find())
			beta = true;

		else {
			regex = Pattern.compile(REGEX_BETA);
			match = regex.matcher(BUILD);
	
			beta = match.find();
		}

		if (beta) {
			if (DOC_ID.equals("PreRelease"))
				return 2;

			// Hack to force large OTA updates to return 0.
			// I have never seen a beta OTA update exceed this size.
			else if (Integer.parseInt(size) > 550000000)
				return 0;

			else {
				regex = Pattern.compile("\\d(DevBeta|Seed)");
				match = regex.matcher(DOC_ID);

				if (match.find()) {
					regex = null;
					return 2;
				}

				else {
					regex = Pattern.compile("Public");
					match = regex.matcher(DOC_ID);
					regex = null;

					return (match.find()) ? 1 : 0;
				}
			}
		}

		else {
			regex = null;
			return 0;
		}
	}

	/**
	 * Returns the timestamp found in the URL.
	 * Note that this is not always accurate;
	 * it may be off by a day, or even a week.
	 * 
	 * @return The timestamp found in the URL.
     **/
	public String date() {
		return date;
	}

	/**
	 * Returns part of the timestamp found in the URL.
	 * 
	 * @param dmy	Specifies if you are looking for the <b>d</b>ay, <b>m</b>onth, or <b>y</b>ear.
	 * If neither 'd', 'm', or 'y' is specified, it will return the entire timestamp.
	 * 
	 * @return The day, month, or year found in the URL.
     **/
	public String date(final char dmy) {
		switch (dmy) {
			// Day
			case 'd':
				return date.substring(6);

			// Month
			case 'm':
				return date.substring(4, 6);

			// Year
			case 'y':
				return date.substring(0, 4);

			default:
				return date;
		}
	}

	/**
	 * @return The build as listed in the OTA update catalog.
     **/
	public String declaredBuild() {
		return BUILD;
	}

	/**
	 * Checks if Apple marked the release as a beta or not.
	 * Just because Apple marked the release as a beta does
	 * not mean that it is a beta release.
	 * 
	 * @return A boolean value of whether Apple claims this is a beta release (true) or not (false).
     **/
	public boolean isDeclaredBeta() {
		return ENTRY.containsKey("ReleaseType") && ENTRY.get("ReleaseType").toString().equals("Beta");
	}

	/**
	 * Checks if the release is a large, "one size fits all" package.
	 * 
	 * @return A boolean value of whether this release is used to cover all scenarios (true) or not (false).
     **/
	public boolean isUniversal() {
		return (PREREQ_VER.equals("N/A") && PREREQ_BUILD.equals("N/A"));
	}

	/**
	 * Returns the value of "MarketingVersion" if present.
	 * "MarketingVersion" is used in some entries to display
	 * a false version number (e.g. watchOS 2).
	 * 
	 * @return A String value of the "MarketingVersion" key, or "OSVersion" key.
     **/
	public String marketingVersion() {
		if (ENTRY.containsKey("MarketingVersion")) {
			if (!ENTRY.get("MarketingVersion").toString().contains("."))
				return ENTRY.get("MarketingVersion").toString() + ".0";
			else
				return ENTRY.get("MarketingVersion").toString();
		}

		else
			return ENTRY.get("OSVersion").toString();
	}

	/**
	 * @return The "OSVersion" key, as a String.
     **/
	public String osVersion() {
		return ENTRY.get("OSVersion").toString();
	}

	/**
	 * "PrerequisiteBuild" states the specific build that
	 * the OTA package is intended for.
	 *
	 * @return The "PrerequisiteBuild" key, as a String.
     **/
	public String prerequisiteBuild() {
		return PREREQ_BUILD;
	}

	/**
	 * "PrerequisiteVersion" states the specific version that
	 * the OTA package is intended for.
	 *
	 * @return The "PrerequisiteVersion" key, as a String.
     **/
	public String prerequisiteVer() {
		return PREREQ_VER;
	}

	/**
	 * @return The package's file size, as a String. It is formatted with commas.
     **/
	public String size() {
		return NumberFormat.getNumberInstance(Locale.US).format(Integer.parseInt(size));
	}

	/**
	 * "sortingBuild()" is the regular build number, with additional zeroes
	 * in the beginning to make sure it's arranged on top.
	 *
	 * @return A String with the same value as OTAPackage.build(),
	 * but with a number of zeroes in front so the program arranges it above
	 * newer entries.
     **/
	public String sortingBuild() {
		String sortBuild = BUILD;

		// Make 9A### appear before 10A###.
		if (Character.isLetter(sortBuild.charAt(1)))
			sortBuild = '0' + sortBuild;

		// If the build number is false, replace everything after the letter with "0000."
		// This will cause betas to appear first.
		if (!this.actualBuild().equals(this.declaredBuild())) {
			final Pattern betaRegex = Pattern.compile(REGEX_BUILD_UP_TO_LETTER);
			match = betaRegex.matcher(sortBuild);
			String upToLetter = "";

			if (match.find())
				upToLetter = match.group();

			sortBuild = upToLetter + "0000";
		}

		return sortBuild;
	}

	/**
	 * "sortingMarketingVersion()" is the marketing version,
	 * with additional zeroes in the beginning to make sure
	 * it's arranged on top.
	 *
	 * @return A String with the same value as OTAPackage.marketingVersion(),
	 * but with a number of zeroes in front so the program arranges it above
	 * newer entries.
     **/
	public String sortingMarketingVersion() {
		return (this.marketingVersion().charAt(1) == '.') ? '0' + this.marketingVersion() : this.marketingVersion();
	}

	/**
	 * "sortingPrerequisiteBuild()" is the prerequisite build,
	 * with additional zeroes in the beginning to make sure
	 * it's arranged on top.
	 *
	 * @return A String with the same value as OTAPackage.prerequisiteBuild(),
	 * but with a number of zeroes in front so the program arranges it above
	 * newer entries.
     **/
	public String sortingPrerequisiteBuild() {
		if (this.isUniversal())
			return "0000000000";

		else {
			if (Character.isLetter(PREREQ_BUILD.charAt(1)))
				return '0' + PREREQ_BUILD;

			else
				return PREREQ_BUILD;
		}
	}

	public NSObject[] supportedDeviceModels() {
		return supportedDeviceModels;
	}

	public NSObject[] supportedDevices() {
		return supportedDevices;
	}

	/**
	 * @return The package's URL, as a String.
     **/
	public String url() {
		return URL;
	}
}
