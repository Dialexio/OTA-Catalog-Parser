/*
 * OTA Catalog Parser 0.4
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
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.regex.*;
import org.xml.sax.SAXException;

public class Parser {
	private final static String PROG_VER = "0.4";

	private final static ArrayList<OTAPackage> ENTRY_LIST = new ArrayList<OTAPackage>();
	private final static HashMap<String, Integer> buildRowspanCount = new HashMap<String, Integer>(),
		dateRowspanCount = new HashMap<String, Integer>(),
		fileRowspanCount = new HashMap<String, Integer>(),
		marketingVersionRowspanCount = new HashMap<String, Integer>(),
		osVersionRowspanCount = new HashMap<String, Integer>();
	private final static HashMap<String, HashMap<String, Integer>> prereqRowspanCount = new HashMap<String, HashMap<String, Integer>>(); // Build, <PrereqOS, count>

	private static boolean checkModel, showBeta = false;
	private static NSDictionary root = null;
	private static String device, maxOSVer = "", minOSVer = "", model;


	private static void addEntries(final NSDictionary PLIST_ROOT) {
		// Looking for the array with key "Assets."
		final NSObject[] ASSETS = ((NSArray)PLIST_ROOT.objectForKey("Assets")).getArray();

		boolean matched;
		OTAPackage entry;

		// Look at every item in the array with the key "Assets."
		for (NSObject item:ASSETS) {
			entry = new OTAPackage((NSDictionary)item); // Feed it into our own object. This will be used for sorting in the future.
			matched = false;

			// Beta check.
			if (!showBeta && entry.isBeta())
				continue;

			// Device check.
			for (NSObject supportedDevice:entry.supportedDevices()) {
				if (device.equals(supportedDevice.toString())) {
					matched = true;
					break;
				}
			}

			// Model check, if needed.
			if (matched && checkModel) {
				matched = false; // Skipping unless we can verify we want it.

				// Make sure "SupportedDeviceModels" exists.
				if (entry.supportedDeviceModels() != null) {
					// Since it's an array, check each entry if the model matches.
					for (NSObject supportedDeviceModel:entry.supportedDeviceModels())
						if (supportedDeviceModel.toString().equals(model)) {
							matched = true;
							break;
						}
				}
			}

			// OS version check. Move to the next item if it doesn't match.
			if (matched && !maxOSVer.isEmpty() && (maxOSVer.compareTo(entry.marketingVersion()) < 0))
					continue;
			if (matched && !minOSVer.isEmpty() && (minOSVer.compareTo(entry.marketingVersion()) > 0))
					continue;

			// Add it after it survives the checks.
			if (matched)
				ENTRY_LIST.add(entry);
		}
	}

	private static void countRowspan(final ArrayList<OTAPackage> ENTRYLIST) {
		HashMap<String, Integer> prereqNestedCount;

		// Count the rowspans for wiki markup.
		for (OTAPackage entry:ENTRYLIST) {
			prereqNestedCount = new HashMap<String, Integer>();

			// Build
			// Increment the count if it exists.
			if (buildRowspanCount.containsKey(entry.declaredBuild()))
				buildRowspanCount.replace(entry.declaredBuild(), buildRowspanCount.get(entry.declaredBuild())+1);
			// If it hasn't been counted, add the first tally.
			else
				buildRowspanCount.put(entry.declaredBuild(), 1);

			// Date (Count actualBuild() and not date() because x.0 GM and x.1 beta can technically be pushed at the same time.)
			// Increment the count if it exists.
			if (dateRowspanCount.containsKey(entry.actualBuild()))
				dateRowspanCount.replace(entry.actualBuild(), dateRowspanCount.get(entry.actualBuild())+1);
			// If it hasn't been counted, add the first tally.
			else
				dateRowspanCount.put(entry.actualBuild(), 1);

			// File URL
			// Increment the count if it exists.
			if (fileRowspanCount.containsKey(entry.url()))
				fileRowspanCount.replace(entry.url(), fileRowspanCount.get(entry.url())+1);
			// If it hasn't been counted, add the first tally.
			else
				fileRowspanCount.put(entry.url(), 1);

			// Marketing version
			// Increment the count if it exists.
			if (marketingVersionRowspanCount.containsKey(entry.marketingVersion()))
				marketingVersionRowspanCount.replace(entry.marketingVersion(), marketingVersionRowspanCount.get(entry.marketingVersion())+1);
			// If it hasn't been counted, add the first tally.
			else
				marketingVersionRowspanCount.put(entry.marketingVersion(), 1);

			// OS version
			// Increment the count if it exists.
			if (osVersionRowspanCount.containsKey(entry.osVersion()))
				osVersionRowspanCount.replace(entry.osVersion(), osVersionRowspanCount.get(entry.osVersion())+1);
			// If it hasn't been counted, add the first tally.
			else
				osVersionRowspanCount.put(entry.osVersion(), 1);

			// Prerequisite version
			if (prereqRowspanCount.containsKey(entry.declaredBuild())) // Load nested HashMap into variable temporarily, if it exists.
				prereqNestedCount = prereqRowspanCount.get(entry.declaredBuild());

			// Increment the count if it exists.
			if (prereqNestedCount.containsKey(entry.prerequisiteVer()))
				prereqNestedCount.replace(entry.prerequisiteVer(), prereqNestedCount.get(entry.prerequisiteVer())+1);
			// If it hasn't been counted, add the first tally.
			else
				prereqNestedCount.put(entry.prerequisiteVer(), 1);

			prereqRowspanCount.put(entry.declaredBuild(), prereqNestedCount);
		}
	}

	private static void loadFile(final File PLIST_NAME) {
		try {
			root = (NSDictionary)PropertyListParser.parse(PLIST_NAME);
		}
		catch (FileNotFoundException e) {
			System.err.println("ERROR: The file \"" + PLIST_NAME + "\" can't be found.");
			System.exit(2);
		}
		catch (PropertyListFormatException e) {
			System.err.println("ERROR: This isn't an Apple property list.");
			System.exit(6);
		}
		catch (SAXException e) {
			System.err.println("ERROR: This file doesn't have proper XML syntax.");
			System.exit(7);
		}
		catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	public static void main(final String[] args) {
		boolean mwMarkup = false, version = false;
		int i = 0;
		String arg = "", plistName = "";

		// Reading arguments (and performing some basic checks).
		while (i < args.length && args[i].startsWith("-")) {
			arg = args[i++];

			if (arg.equals("-b"))
				showBeta = true;

			else if (arg.equals("-d")) {
				device = "";

				if (i < args.length)
					device = args[i++];

				if (!device.matches("((AppleTV|iP(ad|hone|od))|Watch)(\\d)?\\d,\\d")) {
					System.err.println("ERROR: You need to set a device with the \"-d\" argument, e.g. iPhone3,1 or iPad2,7");
					System.exit(1);
				}
			}

			else if (arg.equals("-f")) {
				plistName = "";

				if (i < args.length)
					plistName = args[i++];

				if (plistName.isEmpty()) {
					System.err.println("ERROR: You need to supply a file name.");
					System.exit(2);
				}
			}

			else if (arg.equals("-m")) {
				model = "";

				if (i < args.length)
					model = args[i++];

				if (!model.matches("[JKMNP]\\d(\\d)?(\\d)?[A-Za-z]?AP")) {
					System.err.println("ERROR: You need to specify a model with the \"-m\" argument, e.g. N71AP");
					System.exit(3);
				}
			}

			else if (arg.equals("-max")) {
				maxOSVer = "";

				if (i < args.length)
					maxOSVer = args[i++];

				if (!maxOSVer.matches("\\d\\.\\d(\\.\\d)?(\\d)?")) {
					System.err.println("ERROR: You need to specify a version of iOS if you are using the \"-max\" argument, e.g. 4.3 or 8.0.1");
					System.exit(4);
				}
			}

			else if (arg.equals("-min")) {
				minOSVer = "";

				if (i < args.length)
					minOSVer = args[i++];

				if (!minOSVer.matches("\\d\\.\\d(\\.\\d)?(\\d)?")) {
					System.err.println("ERROR: You need to specify a version of iOS if you are using the \"-min\" argument, e.g. 4.3 or 8.0.1");
					System.exit(5);
				}
			}

			else if (arg.equals("-v"))
				version = true;

			else if (arg.equals("-w"))
				mwMarkup = true;
		}

		// Flag whether or not we need to check the model.
		// Right now, it's just a lazy check for iPhone8,1 or iPhone8,2.
		checkModel = device.matches("iPhone8,(1|2)");

		if (version) {
			System.out.println("OTA Catalog Parser v" + PROG_VER);
			System.out.println("https://github.com/Dialexio/OTA-Catalog-Parser-Java\n");
		}

		loadFile(new File(plistName));

		addEntries(root);

		sort();

		if (mwMarkup) {
			countRowspan(ENTRY_LIST);
			printWikiMarkup(ENTRY_LIST);
		}
		else
			printOutput(ENTRY_LIST);
	}

	private static void printOutput(final ArrayList<OTAPackage> ENTRYLIST) {
		String osName;

		for (OTAPackage entry:ENTRYLIST) {
			if (device.startsWith("Watch"))
				osName = "watchOS";
			else if (device.matches("AppleTV(2,1|3,1|3,2)"))
				osName = "iOS";
			else if (device.startsWith("AppleTV"))
				osName = "tvOS";
			else
				osName = "iOS";

			// Output OS version and build.
			System.out.println(osName + " " + entry.marketingVersion() + " (Build " + entry.actualBuild() + ")");
			System.out.println("Listed as: "+ entry.osVersion() + " (Build " + entry.declaredBuild() + ").");

			// Is this a beta?
			System.out.print("Beta release: ");
			if (entry.isBeta()) {
				if (!entry.isDevBeta())
					System.out.print("Public ");

				System.out.println("Beta " + entry.betaNumber());
			}

			else if (entry.declaredBeta())
				System.out.println("Labeled as one, but not a beta");

			else
				System.out.println("No");

			// Print prerequisites if there are any.
			if (entry.isUniversal())
				System.out.println("Requires: Not specified");

			else
				System.out.println("Requires: " + entry.prerequisiteVer() + " (Build " + entry.prerequisiteBuild() + ")");

			// Date as extracted from the URL.
			System.out.println("Timestamp: " + entry.date().substring(0, 4) + "/" + entry.date().substring(4, 6) + "/" + entry.date().substring(6));

			// Print out the URL and file size.
			System.out.println("URL: " + entry.url());
			System.out.println("File size: " + entry.size());

			System.out.println();
		}
	}

	private static void printWikiMarkup(final ArrayList<OTAPackage> ENTRYLIST) {
		final Pattern nameRegex = Pattern.compile("[0-9a-f]{40}\\.zip");
		Matcher name;
		String fileName;

		for (OTAPackage entry:ENTRYLIST) {
			fileName = "";

			name = nameRegex.matcher(entry.url());
			if (name.find())
				fileName = name.group();

			// Let us begin!
			System.out.println("|-");

			//Marketing Version for Apple Watch.
			if (device.matches("Watch(\\d)?\\d,\\d") && marketingVersionRowspanCount.containsKey(entry.marketingVersion())) {
				System.out.print("| ");

				// Only give rowspan if there is more than one row with the OS version.
				if (marketingVersionRowspanCount.get(entry.marketingVersion()).intValue() > 1)
					System.out.print("rowspan=\"" + marketingVersionRowspanCount.get(entry.marketingVersion()) + "\" | ");

				System.out.print(entry.marketingVersion());

				// Give it a beta label (if it is one).
				if (entry.isBeta()) {
					if (!entry.isDevBeta())
						System.out.print(" Public Beta");
					else
						System.out.print(" beta");

					// Don't print a 1 if this is the first beta.
					if (entry.betaNumber() != 1)
						System.out.print(" " + entry.betaNumber());
				}

				System.out.println();

				//Remove the count since we're done with it.
				marketingVersionRowspanCount.remove(entry.marketingVersion());
			}

			// Output OS version.
			if (osVersionRowspanCount.containsKey(entry.osVersion())) {
				System.out.print("| ");

				// Only give rowspan if there is more than one row with the OS version.
				if (osVersionRowspanCount.get(entry.osVersion()).intValue() > 1)
					System.out.print("rowspan=\"" + osVersionRowspanCount.get(entry.osVersion()) + "\" | ");

				System.out.print(entry.osVersion());

				// Give it a beta label (if it is one).
				if (entry.isBeta()) {
					if (!entry.isDevBeta())
						System.out.print(" Public Beta");
					else
						System.out.print(" beta");

					// Don't print a 1 if this is the first beta.
					if (entry.betaNumber() != 1)
						System.out.print(" " + entry.betaNumber());
				}

				System.out.println();

				// Output a filler for Marketing Version, if this is a 32-bit Apple TV.
				if (device.matches("AppleTV(2,1|3,1|3,2)")) {
					System.out.print("| ");

					// Only give rowspan if there is more than one row with the OS version.
					if (osVersionRowspanCount.get(entry.osVersion()).intValue() > 1)
						System.out.print("rowspan=\"" + osVersionRowspanCount.get(entry.osVersion()) + "\" | ");

					System.out.println("[MARKETING VERSION]");
				}

				//Remove the count since we're done with it.
				osVersionRowspanCount.remove(entry.osVersion());
			}

			// Output build number.
			if (buildRowspanCount.containsKey(entry.declaredBuild())) {
				System.out.print("| ");

				// Only give rowspan if there is more than one row with the OS version.
				// Count declaredBuild() instead of actualBuild() so the entry pointing betas to the final build is treated separately.
				if (buildRowspanCount.get(entry.declaredBuild()).intValue() > 1)
					System.out.print("rowspan=\"" + buildRowspanCount.get(entry.declaredBuild()) + "\" | ");

				//Remove the count since we're done with it.
				buildRowspanCount.remove(entry.declaredBuild());

				System.out.print(entry.actualBuild());

				// Do we have a fake beta?
				if (entry.declaredBeta() && !entry.isBeta())
					System.out.print("<ref name=\"fakefive\" />");

				System.out.println();
			}

			// Print prerequisites if there are any.
			if (entry.isUniversal()) {
				System.out.print("| colspan=\"2\" {{n/a");

				// Is this "universal" OTA update intended for betas?
				if (entry.declaredBeta() && !entry.isBeta())
					System.out.print("|Beta");

				System.out.println("}}");
			}
			else {
				// Prerequisite version
				if (prereqRowspanCount.containsKey(entry.declaredBuild()) && prereqRowspanCount.get(entry.declaredBuild()).containsKey(entry.prerequisiteVer())) {
					System.out.print("| ");

					// Is there more than one of this prerequisite version tallied?
					// Also do not use rowspan if the prerequisite build is a beta.
					if (!entry.prerequisiteBuild().matches("(\\d)?\\d[A-Z][45]\\d{3}[a-z]") && prereqRowspanCount.get(entry.declaredBuild()).get(entry.prerequisiteVer()).intValue() > 1) {
						System.out.print("rowspan=\"" + prereqRowspanCount.get(entry.declaredBuild()).get(entry.prerequisiteVer()) + "\" | ");
						prereqRowspanCount.get(entry.declaredBuild()).remove(entry.prerequisiteVer());
					}

					System.out.print(entry.prerequisiteVer());

					// Very quick check if prerequisite is a beta. Won't work if close to final release.
					if (entry.prerequisiteBuild().matches("(\\d)?\\d[A-Z][45]\\d{3}[a-z]"))
						System.out.print(" beta #");

					System.out.println();
				}

				// Prerequisite build
				System.out.println("| " + entry.prerequisiteBuild());
			}

			// Date as extracted from the URL. Using the same rowspan count as build.
			// (3.1.1 had two builds released on different dates for iPod touch 3G.)
			if (dateRowspanCount.containsKey(entry.actualBuild())) {
				System.out.print("| ");

				// Only give rowspan if there is more than one row with the OS version.
				if (dateRowspanCount.get(entry.actualBuild()).intValue() > 1) {
					System.out.print("rowspan=\"" + dateRowspanCount.get(entry.actualBuild()) + "\" | ");
					dateRowspanCount.remove(entry.actualBuild()); //Remove the count since we already used it.
				}

				System.out.println("{{date|" + entry.date().substring(0, 4) + "|" + entry.date().substring(4, 6) + "|" + entry.date().substring(6) + "}}");
			}

			if (fileRowspanCount.containsKey(entry.url())) {
				// Print file URL.
				System.out.print("| ");

				// Only give rowspan if there is more than one row with the OS version.
				if (fileRowspanCount.get(entry.url()).intValue() > 1)
					System.out.print("rowspan=\"" + fileRowspanCount.get(entry.url()) + "\" | ");

				System.out.println("[" + entry.url() + " " + fileName + "]");

				//Print file size.
				System.out.print("| ");

				// Only give rowspan if there is more than one row with the OS version.
				if (fileRowspanCount.get(entry.url()).intValue() > 1)
					System.out.print("rowspan=\"" + fileRowspanCount.get(entry.url()) + "\" | ");

				System.out.println(entry.size());

				//Remove the count since we're done with it.
				fileRowspanCount.remove(entry.url());
			}
		}
	}

	private static void sort() {
		Collections.sort(ENTRY_LIST, new Comparator<OTAPackage>() {
			@Override
			public int compare(OTAPackage package1, OTAPackage package2) {
				return ((OTAPackage)package1).sortingPrerequisiteBuild().compareTo(((OTAPackage)package2).sortingPrerequisiteBuild());
			}
		});
		Collections.sort(ENTRY_LIST, new Comparator<OTAPackage>() {
			@Override
			public int compare(OTAPackage package1, OTAPackage package2) {
				return ((OTAPackage)package1).sortingBuild().compareTo(((OTAPackage)package2).sortingBuild());
			}
		});
		Collections.sort(ENTRY_LIST, new Comparator<OTAPackage>() {
			@Override
			public int compare(OTAPackage package1, OTAPackage package2) {
				return ((OTAPackage)package1).marketingVersion().compareTo(((OTAPackage)package2).marketingVersion());
			}
		});
	}
}
