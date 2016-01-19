/*
 * OTA Catalog Parser 0.4.2
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
	private final static String PROG_VER = "0.4.2";

	private final static ArrayList<OTAPackage> ENTRY_LIST = new ArrayList<OTAPackage>();
	private final static HashMap<String, Integer> buildRowspanCount = new HashMap<String, Integer>(),
		dateRowspanCount = new HashMap<String, Integer>(),
		marketingVersionRowspanCount = new HashMap<String, Integer>(),
		osVersionRowspanCount = new HashMap<String, Integer>();
	private final static HashMap<String, HashMap<String, Integer>> fileRowspanCount = new HashMap<String, HashMap<String, Integer>>(),// URL, <PrereqOS, count> 
		prereqRowspanCount = new HashMap<String, HashMap<String, Integer>>(); // Build, <PrereqOS, count>

	private static boolean showBeta = false;
	private static NSDictionary root = null;
	private static String device, maxOSVer = "", minOSVer = "", model;

	private static void addEntries(final NSDictionary PLIST_ROOT, final boolean CHECK_MODEL, final boolean WIKI) {
		// Looking for the array with key "Assets."
		NSObject[] assets = ((NSArray)PLIST_ROOT.objectForKey("Assets")).getArray();

		boolean matched;
		OTAPackage entry;

		// Look at every item in the array with the key "Assets."
		for (NSObject item:assets) {
			entry = new OTAPackage((NSDictionary)item); // Feed the info into a custom object so we can easily pull info and sort.
			matched = false;

			// Beta check.
			if (!showBeta && entry.betaType() > 0)
				continue;

			// Only count "Public Beta 1" entries once.
			if (WIKI && !entry.isDeclaredBeta() && entry.betaNumber() == 1)
				continue;

			// Device check.
			for (NSObject supportedDevice:entry.supportedDevices()) {
				if (device.equals(supportedDevice.toString())) {
					matched = true;
					break;
				}
			}

			// Model check, if needed.
			if (matched && CHECK_MODEL) {
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

			// If it's still a match, check the OS version.
			// If the OS version doesn't fit what we're
			// searching for, continue to the next entry.
			if (matched) {
				if (!maxOSVer.isEmpty() && (maxOSVer.compareTo(entry.marketingVersion()) < 0))
					continue;
				if (!minOSVer.isEmpty() && (minOSVer.compareTo(entry.marketingVersion()) > 0))
					continue;

				// It survived the checks!
				ENTRY_LIST.add(entry);
			}
		}

		assets = null;
		entry = null;
	}

	private static void countRowspan(final ArrayList<OTAPackage> ENTRYLIST) {
		HashMap<String, Integer> fileNestedCount, prereqNestedCount;
		String pseudoPrerequisite;

		// Count the rowspans for wiki markup.
		for (OTAPackage entry:ENTRYLIST) {
			fileNestedCount = new HashMap<String, Integer>();
			prereqNestedCount = new HashMap<String, Integer>();

			// Build
			// Increment the count if it exists.
			if (buildRowspanCount.containsKey(entry.declaredBuild()))
				buildRowspanCount.put(entry.declaredBuild(), buildRowspanCount.get(entry.declaredBuild())+1);
			// If it hasn't been counted, add the first tally.
			else
				buildRowspanCount.put(entry.declaredBuild(), 1);

			// Date (Count actualBuild() and not date() because x.0 GM and x.1 beta can technically be pushed at the same time.)
			// Increment the count if it exists.
			if (dateRowspanCount.containsKey(entry.actualBuild()))
				dateRowspanCount.put(entry.actualBuild(), dateRowspanCount.get(entry.actualBuild())+1);
			// If it hasn't been counted, add the first tally.
			else
				dateRowspanCount.put(entry.actualBuild(), 1);

			// File URL
			// If there is no prerequisite, use a fake prerequisite (with the OS version).
			// This allows us to merge beta entries' URL and universal entries' URL.

			// Load nested HashMap into a temporary variable, if it exists.
			if (fileRowspanCount.containsKey(entry.url()))
				fileNestedCount = fileRowspanCount.get(entry.url());

			// Increment the count if it exists.
			if (fileNestedCount.containsKey(entry.prerequisiteVer()))
				fileNestedCount.put(entry.prerequisiteVer(), fileNestedCount.get(entry.prerequisiteVer())+1);
			// If it hasn't been counted, add the first tally.
			else
				fileNestedCount.put(entry.prerequisiteVer(), 1);

			fileRowspanCount.put(entry.url(), fileNestedCount);

			// Marketing version
			// Increment the count if it exists.
			if (marketingVersionRowspanCount.containsKey(entry.marketingVersion()))
				marketingVersionRowspanCount.put(entry.marketingVersion(), marketingVersionRowspanCount.get(entry.marketingVersion())+1);
			// If it hasn't been counted, add the first tally.
			else
				marketingVersionRowspanCount.put(entry.marketingVersion(), 1);

			// OS version
			// Increment the count if it exists.
			if (osVersionRowspanCount.containsKey(entry.osVersion()))
				osVersionRowspanCount.put(entry.osVersion(), osVersionRowspanCount.get(entry.osVersion())+1);
			// If it hasn't been counted, add the first tally.
			else
				osVersionRowspanCount.put(entry.osVersion(), 1);

			// Prerequisite version
			if (prereqRowspanCount.containsKey(entry.declaredBuild())) // Load nested HashMap into variable temporarily, if it exists.
				prereqNestedCount = prereqRowspanCount.get(entry.declaredBuild());

			// Increment the count if it exists.
			if (prereqNestedCount.containsKey(entry.prerequisiteVer()))
				prereqNestedCount.put(entry.prerequisiteVer(), prereqNestedCount.get(entry.prerequisiteVer())+1);
			// If it hasn't been counted, add the first tally.
			else
				prereqNestedCount.put(entry.prerequisiteVer(), 1);

			prereqRowspanCount.put(entry.declaredBuild(), prereqNestedCount);
		}

		fileNestedCount = null;
		prereqNestedCount = null;
		pseudoPrerequisite = null;
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
		boolean mwMarkup = false;
		int i = 0;
		String arg = "", plistName = "";

		// No arguments? Don't do anything.
		if (args.length == 0) {
			System.out.println("OTA Catalog Parser v" + PROG_VER);
			System.out.println("https://github.com/Dialexio/OTA-Catalog-Parser-Java");

			System.out.println("\nRequired Arguments:");
			System.out.println("-d <device>      Choose the device you are searching for. (e.g. iPhone8,1)");
			System.out.println("-f <file>        Specify the path to the XML file you are searching in.");
			System.out.println("-m <model>       Choose the model you are searching for. (e.g. N71mAP)\n                 This is only used and required for iPhone 6S or 6S Plus.");

			System.out.println("\nOptional Arguments:");
			System.out.println("-b               Displays beta firmwares. By default, this is disabled.");
			System.out.println("-max <version>   Choose the highest firmware version you are searching for. (e.g. 9.0.2)");
			System.out.println("-min <version>   Choose the lowest firmware version you are searching for. (e.g. 8.4.1)");
			System.out.println("-w               Formats the output for The iPhone Wiki.");
			System.exit(0);
		}

		// Reading arguments (and performing some basic checks).
		while (i < args.length && args[i].startsWith("-")) {
			arg = args[i++];

			switch (arg) {
				case "-b":
					showBeta = true;
				break;

				case "-d":
					device = "";

					if (i < args.length)
						device = args[i++];

					if (!device.matches("((AppleTV|iP(ad|hone|od))|Watch)(\\d)?\\d,\\d")) {
						System.err.println("ERROR: You need to set a device with the \"-d\" argument, e.g. iPhone3,1 or iPad2,7");
						System.exit(1);
					}
				break;

				case "-f":
					plistName = "";

					if (i < args.length)
						plistName = args[i++];

					if (plistName.isEmpty()) {
						System.err.println("ERROR: You need to supply a file name.");
						System.exit(2);
					}
				break;

				case "-m":
					model = "";

					if (i < args.length)
						model = args[i++];

					if (!model.matches("[JKMNP]\\d(\\d)?(\\d)?[A-Za-z]?AP")) {
						System.err.println("ERROR: You need to specify a model with the \"-m\" argument, e.g. N71AP");
						System.exit(3);
					}
				break;

				case "-max":
					maxOSVer = "";

					if (i < args.length)
						maxOSVer = args[i++];

					if (!maxOSVer.matches("(\\d)?\\d\\.\\d(\\.\\d)?(\\d)?")) {
						System.err.println("ERROR: You need to specify a version of iOS if you are using the \"-max\" argument, e.g. 4.3 or 8.0.1");
						System.exit(4);
					}
				break;

				case "-min":
					minOSVer = "";

					if (i < args.length)
						minOSVer = args[i++];

					if (!minOSVer.matches("(\\d)?\\d\\.\\d(\\.\\d)?(\\d)?")) {
						System.err.println("ERROR: You need to specify a version of iOS if you are using the \"-min\" argument, e.g. 4.3 or 8.0.1");
						System.exit(5);
					}
				break;

				case "-w":
					mwMarkup = true;
				break;
			}

		}

		// Flag whether or not we need to check the model.
		// Right now, it's just a lazy check for iPhone8,1 or iPhone8,2.

		loadFile(new File(plistName));

		addEntries(root, device.matches("iPhone8,(1|2)"), mwMarkup);

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
				osName = "watchOS ";
			else if (device.matches("AppleTV(2,1|3,1|3,2)"))
				osName = "iOS ";
			else if (device.startsWith("AppleTV"))
				osName = "tvOS ";
			else
				osName = "iOS ";

			// Output OS version and build.
			System.out.print(osName + entry.marketingVersion());

				// Is this a beta?
				if (entry.betaType() > 0) {
					if (entry.betaType() == 2)
						System.out.print(" Public");
	
					System.out.print(" Beta " + entry.betaNumber());
				}

			System.out.println(" (Build " + entry.actualBuild() + ')');
			System.out.println("Listed as: "+ entry.osVersion() + " (Build " + entry.declaredBuild() + ')');
			System.out.println("Marked as beta: " + entry.isDeclaredBeta());

			// Print prerequisites if there are any.
			if (entry.isUniversal())
				System.out.println("Requires: Not specified");

			else
				System.out.println("Requires: " + entry.prerequisiteVer() + " (Build " + entry.prerequisiteBuild() + ')');

			// Date as extracted from the URL.
			System.out.println("Timestamp: " + entry.date('y') + '/' + entry.date('m') + '/' + entry.date('d'));

			// Print out the URL and file size.
			System.out.println("URL: " + entry.url());
			System.out.println("File size: " + entry.size());

			System.out.println();
		}
	}

	private static void printWikiMarkup(final ArrayList<OTAPackage> ENTRYLIST) {
		final Pattern NAME_REGEX = Pattern.compile("[0-9a-f]{40}\\.zip");
		Matcher name;
		String fileName;

		for (OTAPackage entry:ENTRYLIST) {
			fileName = "";

			name = NAME_REGEX.matcher(entry.url());
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
				if (entry.betaType() > 0) {
					if (entry.betaType() == 2)
						System.out.print(" Public Beta");
					else
						System.out.print(" beta");

					// Don't print a 1 if this is the first beta.
					if (entry.betaNumber() > 1)
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
				if (entry.betaType() > 0) {
					if (entry.betaType() == 2)
						System.out.print(" Public Beta");
					else
						System.out.print(" beta");

					// Don't print a 1 if this is the first beta.
					if (entry.betaNumber() > 1)
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

				// Do we have a false build number?
				if (!entry.actualBuild().equals(entry.declaredBuild()))
					System.out.print("<ref name=\"fakefive\" />");

				System.out.println();
			}

			// Print prerequisites if there are any.
			if (entry.isUniversal()) {
				System.out.print("| colspan=\"2\" {{n/a");

				// Is this "universal" OTA update intended for betas?
				if (entry.isDeclaredBeta() && entry.betaType() == 0)
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

				System.out.println("{{date|" + entry.date('y') + '|' + entry.date('m') + '|' + entry.date('d') + "}}");
			}

			if (fileRowspanCount.containsKey(entry.url()) && fileRowspanCount.get(entry.url()).containsKey(entry.prerequisiteVer())) {
				System.out.print("| ");

				// Is there more than one of this prerequisite version tallied?
				// Also do not use rowspan if the prerequisite build is a beta.
				if (fileRowspanCount.get(entry.url()).get(entry.prerequisiteVer()).intValue() > 1)
					System.out.print("rowspan=\"" + fileRowspanCount.get(entry.url()).get(entry.prerequisiteVer()) + "\" | ");

				System.out.print('[' + entry.url() + ' ' + fileName + "]\n| ");

				//Print file size.

				// Only give rowspan if there is more than one row with the OS version.
				if (fileRowspanCount.get(entry.url()).get(entry.prerequisiteVer()).intValue() > 1)
					System.out.print("rowspan=\"" + fileRowspanCount.get(entry.url()).get(entry.prerequisiteVer()) + "\" | ");

				System.out.println(entry.size());

				//Remove the count since we're done with it.
				fileRowspanCount.get(entry.url()).remove(entry.prerequisiteVer());
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
				return ((OTAPackage)package1).sortingMarketingVersion().compareTo(((OTAPackage)package2).sortingMarketingVersion());
			}
		});
	}
}
