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
	private final long SIZE;
	private final NSDictionary ENTRY;
	private final String URL;
	private Matcher match;

	public final static String REGEX_BETA = "(\\d)?\\d[A-Z][4-6]\\d{3}[a-z]?";

	public OTAPackage(NSDictionary otaEntry) {
		ENTRY = otaEntry;
		otaEntry = null;

		// Obtaining the size and URL.
		// First, we need to make sure we don't get info for a dummy update.
		if (ENTRY.containsKey("RealUpdateAttributes")) {
			NSDictionary REAL_UPDATE_ATTRS = (NSDictionary)ENTRY.get("RealUpdateAttributes");

			SIZE = ((NSNumber)REAL_UPDATE_ATTRS.get("RealUpdateDownloadSize")).longValue();
			URL = REAL_UPDATE_ATTRS.get("RealUpdateURL").toString();
			REAL_UPDATE_ATTRS = null;
		}

		else {
			SIZE = ((NSNumber)ENTRY.get("_DownloadSize")).longValue();
			URL = ((NSString)ENTRY.get("__BaseURL")).getContent() + ((NSString)ENTRY.get("__RelativePath")).getContent();
		}
	}

	/**
	 * Returns the actual build number of the OTA entry.
	 * Sometimes, Apple likes to add a large number to the end.
	 * 
	 * @return The build number that iOS will report in Settings.
     **/
	public String actualBuild() {
		// If it the build number looks like a beta...
		// And it's labeled as a beta...
		// But it's not a beta... We need the actual build number.
		if (this.declaredBuild().matches(REGEX_BETA) &&
			this.releaseType().equals("Public") == false &&
			this.actualReleaseType() == 0) {
				int letterPos, numPos;

				for (letterPos = 1; letterPos < this.declaredBuild().length(); letterPos++) {
					if (Character.isUpperCase(this.declaredBuild().charAt(letterPos))) {
						letterPos++;
						break;
					}
				}

			    numPos = letterPos + 1;
				if (this.declaredBuild().charAt(numPos) == '0')
					numPos++;

				return this.declaredBuild().substring(0, letterPos) + this.declaredBuild().substring(numPos);
		}
		
		else
			return this.declaredBuild();
	}

	/**
	 * Returns the package's actual release type in the form of an integer.
	 * 
	 * @return An integer value of 0 (not a beta), 1 (public beta), 2 (developer beta), 3 (carrier beta), or 4 (internal build). -1 may be returned if the type is unknown.
     **/
	public int actualReleaseType() {
		// Just check ReleaseType and return values based on it.
		// We do need to dig deeper if it's "Beta" though.
		if (this.releaseType().equals("Public"))
			return 0;

		else {
			switch (ENTRY.get("ReleaseType").toString()) {
				case "Beta":
					if (this.documentationID().equals("N/A") || this.documentationID().equals("PreRelease"))
						return 2;

					else if (this.documentationID().contains("Public"))
						return 1;

					else if (this.documentationID().contains("Beta") || this.documentationID().contains("Seed"))
						return 2;

					else
						return 0;

				case "Carrier":
					return 3;

				case "Internal":
					return 4;

				default:
					System.err.println("Unknown ReleaseType: " + ENTRY.get("ReleaseType").toString());
					return -1;
			}
		}
	}

	/**
	 * Returns the beta number of this entry.
	 * If a beta number cannot be determined, returns 0.
	 * 
	 * @return Whatever number beta this is, as an int.
     **/
	public int betaNumber() {
		final char digit = this.documentationID().charAt(this.documentationID().length() - 1);


		if (this.isHonestBuild() && (this.documentationID().contains("Public") || this.documentationID().contains("Beta") || this.documentationID().contains("Seed")))
			return (Character.isDigit(digit)) ? Character.getNumericValue(digit) : 1;

		else
			return 0;
	}

	/**
	 * Returns the value in the entry's CompatibilityVersion key.
	 * If the compatibility version cannot be determined, this returns 0.
	 * 
	 * @return Whatever is in the key CompatibilityVersion, as an int.
     **/
	public int compatibilityVersion() {
		return (ENTRY.containsKey("CompatibilityVersion")) ? (int)(ENTRY.get("CompatibilityVersion").toJavaObject()) : 0;
	}

	/**
	 * Returns the timestamp found in the URL.
	 * Note that this is not always accurate;
	 * it may be off by a day, or even a week.
	 * 
	 * @return The timestamp found in the URL, which may not be accurate.
     **/
	public String date() {
		match = Pattern.compile("\\d{4}(\\-|\\.)20\\d{8}\\-").matcher(URL);

		if (match.find())
			return match.group().substring(5, 9) + match.group().substring(10, 12) + match.group().substring(13, 15);

		else {
			match = Pattern.compile("\\d{4}(\\-|\\.)20\\d{4}(\\d|\\.)(\\w|\\-)").matcher(URL);

			if (match.find()) {
				switch (match.group().substring(5)) {
					case "201218.D22":
						return "20120307";

					case "2015106-DC":
						return "20151006";

					case "20160009/1":
						return "20160913";

					default:
						return match.group().substring(5);
				}
			}
	
			else
				return "00000000";
		}
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
				return this.date().substring(6);

			// Month
			case 'm':
				return this.date().substring(4, 6);

			// Year
			case 'y':
				return this.date().substring(0, 4);

			default:
				return this.date();
		}
	}

	/**
	 * @return The build as listed in the OTA update catalog.
     **/
	public String declaredBuild() {
		return ENTRY.get("Build").toString();
	}

	/**
	 * Reports the documentation ID that Apple assigned.
	 * This tells iOS which documentation file to load, since multiple ones may be listed.
	 * 
	 * @return The documentation ID that corresponds to the OTA update. If one is not specified, returns "N/A."
     **/
	public String documentationID() {
		return ENTRY.containsKey("SUDocumentationID") ? ENTRY.get("SUDocumentationID").toString() : "N/A";
	}

	/**
	 * Checks if the release has an inflated build number.
	 * Apple does this to push devices on beta builds to stable builds.
	 * 
	 * @return A boolean value of whether this release has a false build number (true) or not (false).
     **/
	public boolean isHonestBuild() {
		return this.actualBuild().equals(this.declaredBuild());
	}

	/**
	 * Checks if the release is a large, "one size fits all" package.
	 * 
	 * @return A boolean value of whether this release is used to cover all scenarios (true) or not (false).
     **/
	public boolean isUniversal() {
		return (this.prerequisiteBuild().equals("N/A"));
	}

	/**
	 * Returns the value of "MarketingVersion" if present.
	 * "MarketingVersion" is used in some entries to display
	 * a false version number.
	 * 
	 * @return A String value of the "MarketingVersion" key (if it exists), otherwise returns the "OSVersion" key.
     **/
	public String marketingVersion() {
		if (ENTRY.containsKey("MarketingVersion")) {
			if (ENTRY.get("MarketingVersion").toString().contains(".") == false)
				return ENTRY.get("MarketingVersion").toString() + ".0";
			else
				return ENTRY.get("MarketingVersion").toString();
		}

		else
			return this.osVersion();
	}

	/**
	 * @return The "OSVersion" key, as a String.
     **/
	public String osVersion() {
		String version = ENTRY.get("OSVersion").toString();

		return (version.substring(0, 3).equals("9.9")) ? version.substring(4) : version;
	}

	/**
	 * "PrerequisiteBuild" states the specific build that
	 * the OTA package is intended for.
	 *
	 * @return The "PrerequisiteBuild" key, as a String.
     **/
	public String prerequisiteBuild() {
		return (ENTRY.containsKey("PrerequisiteBuild")) ? ENTRY.get("PrerequisiteBuild").toString() : "N/A";
	}

	/**
	 * "PrerequisiteVersion" states the specific version that
	 * the OTA package is intended for.
	 *
	 * @return The "PrerequisiteVersion" key, as a String.
     **/
	public String prerequisiteVer() {
		// Using a switch/case since exceptions must be made.
		// (I do not intend to address all exceptions.)
		switch (this.prerequisiteBuild()) {
			// iOS exceptions
			case "10A405":
				return "6.0";

			case "10B141":
				return "6.1";

			case "13A340":
			case "13A341":
				return "9.0 GM";

			// watchOS exceptions
			case "13V5098e":
				return "2.2 beta";

			case "13V5108c":
				return "2.2 beta 2";

			case "13V5117c":
				return "2.2 beta 3";

			case "13V5129c":
				return "2.2 beta 4";

			case "13V5141a":
				return "2.2 beta 5";

			case "13V5143a":
				return "2.2 beta 6";

			case "13V413":
				return "2.2.1 beta";

			case "13V601":
				return "2.2.2 beta";
		
			case "14S5247t":
				return "3.0 beta";

			case "14S5278d":
				return "3.0 beta 2";

			case "14S5290d":
				return "3.0 beta 3";

			case "14S5302d":
				return "3.0 beta 4";

			case "14S5315a":
				return "3.0 beta 5";

			case "14S5321a":
				return "3.0 beta 6";

			case "14S443":
				return "3.1 beta Pre-release";
				
			case "14S452":
				return "3.1 beta";

			case "14S464":
				return "3.1 beta 2";

			case "14S466":
				return "3.1 beta 3";

			case "14S468":
				return "3.1 beta 4 Pre-release";

			case "14S471":
				return "3.1 beta 4";

			case "14S5862d":
				return "3.1.1 beta";

			case "14S5869b":
				return "3.1.1 beta 2";

			case "14S5875b":
				return "3.1.1 beta 3";

			case "14S6879":
				return "3.1.1 beta 4";

			default:
				return (ENTRY.containsKey("PrerequisiteOSVersion")) ? ENTRY.get("PrerequisiteOSVersion").toString() : "N/A";
		}
	}

	/**
	 * Checks if Apple marked the OTA package with a release type.
	 * This function only checks for the existence of such a key,
	 * and not its value. For its value, use the betaType() method.
	 * 
	 * @return A boolean value of whether Apple provided a release type (true) or not (false).
	 **/
	public String releaseType() {
		return (ENTRY.containsKey("ReleaseType")) ? ENTRY.get("ReleaseType").toString() : "Public";
	}

	/**
	 * @return The package's file size, as a String. It is formatted with commas.
     **/
	public String size() {
		return NumberFormat.getNumberInstance(Locale.US).format(SIZE);
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
		int letterPos;
		String sortBuild = this.declaredBuild();

		// Make 9A### appear before 10A###.
		if (Character.isLetter(sortBuild.charAt(1)))
			sortBuild = '0' + sortBuild;

		// If the build number is false, replace everything after the letter with "0000."
		// This will cause betas to appear first.
		if (this.isHonestBuild() == false) {
			for (letterPos = 1; letterPos < sortBuild.length(); letterPos++) {
				if (Character.isUpperCase(sortBuild.charAt(letterPos))) {
					letterPos++;
					break;
				}
			}

			sortBuild = sortBuild.substring(0, letterPos) + "0000";
		}

		// Apple Watch betas go on the bottom in wiki markup.
		// As dumb as this is, it's a pain because of the OS version.
		// Hopefully this gets changed for consistency in the future...
		else if (this.declaredBuild().matches(REGEX_BETA)) {
			for (NSObject supportedDevice:this.supportedDevices()) {
				if (supportedDevice.toString().contains("Watch")) {
					sortBuild = sortBuild.substring(0, 3) + '9' + sortBuild.substring(4);
					break;
				}
			}
		}

		return sortBuild;
	}

	/**
	 * "sortingPrerequisiteBuild()" is the prerequisite build,
	 * with additional zeroes in the beginning to make sure
	 * it's arranged on top.
	 * 
	 * This also appends with an integer that represents the release type.
	 *
	 * @return A String with the same value as OTAPackage.prerequisiteBuild(),
	 * but with additional zeroes so the program will order it correctly,
	 * and an integer at the end specifying the release type.
     **/
	public String sortingPrerequisiteBuild() {
		// Sort by release type.
		int relType = 0;
		String build = this.prerequisiteBuild();

		switch (this.releaseType()) {
			case "Beta":
				relType = 1;
				break;

			case "Carrier":
				relType = 2;
				break;

			case "Internal":
				relType = 3;
				break;
		}

		if (this.isUniversal())
			return "000000000" + relType;

		else {
			if (Character.isLetter(build.charAt(1)))
				build = '0' + build;
			
			match = Pattern.compile("\\d?\\d[A-Z]").matcher(build);
			
			if (build.split("[A-z]")[1].length() < 3 && match.find())
				build = match.group() + '0' + build.replaceFirst("\\d?\\d[A-Z]", "");
			
			return build;
		}
	}

	/**
	 * This method provides the models that this OTA update supports.
	 *
	 * @return An array of NSObjects (which should be NSStrings).
     **/
	public NSObject[] supportedDeviceModels() {
		// Retrieve the list of supported models... if it exists.
		return (ENTRY.containsKey("SupportedDeviceModels")) ? ((NSArray)ENTRY.objectForKey("SupportedDeviceModels")).getArray() : null;
	}

	public NSObject[] supportedDevices() {
		return ((NSArray)ENTRY.objectForKey("SupportedDevices")).getArray();
	}

	/**
	 * @return The package's URL, as a String.
     **/
	public String url() {
		return URL;
	}
}
