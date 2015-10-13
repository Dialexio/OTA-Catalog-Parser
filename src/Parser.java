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
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.regex.*;
import org.xml.sax.SAXException;

public class Parser {
	private static ArrayList<OTAPackage> entryList = new ArrayList<OTAPackage>();
	private static boolean checkModel, showBeta = false;
	private static NSDictionary root = null;
	private static String device = "", maxOSVer = "", minOSVer = "", model = "";

	private static void addEntries(NSDictionary root) {
		NSObject[] assets = ((NSArray)root.objectForKey("Assets")).getArray(); // Looking for the array with key "Assets."
		OTAPackage entry;

		// Look at every item in the array with the key "Assets."
		for (NSObject item:assets) {
			boolean entryMatch = false;
			entry = new OTAPackage((NSDictionary)item); // Feed it into our own object. This will be used for sorting in the future.

			// Beta check.
			if (!showBeta && entry.isBeta())
				continue;

			// Device check.
			for (NSObject supportedDevice:entry.supportedDevices()) {
				if (device.equals(supportedDevice.toString())) {
					entryMatch = true;
					break;
				}
			}

			// Model check, if needed.
			if (entryMatch && checkModel) {
				entryMatch = false; // Skipping unless we can verify we want it.

				// Make sure "SupportedDeviceModels" exists.
				if (entry.supportedDeviceModels() != null) {
					// Since it's an array, check each entry if the model matches.
					for (NSObject supportedDeviceModel:entry.supportedDeviceModels())
						if (supportedDeviceModel.toString().equals(model)) {
							entryMatch = true;
							break;
						}
				}
			}

			// OS version check. Move to the next item if it doesn't match.
			if (entryMatch && !maxOSVer.isEmpty() && (maxOSVer.compareTo(entry.osVersion()) < 0))
					continue;
			if (entryMatch && !minOSVer.isEmpty() && (minOSVer.compareTo(entry.osVersion()) > 0))
					continue;

			// Add it after it survives the checks.
			if (entryMatch)
				entryList.add(entry);
		}
	}

	private static void loadFile(final File xmlName) {
		try {
			root = (NSDictionary)PropertyListParser.parse(xmlName);
		}
		catch (FileNotFoundException e) {
			System.err.println("ERROR: The file \"" + xmlName + "\" can't be found.");
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
		String arg = "", xmlName = "";

		System.out.println("OTA Catalog Parser v0.3.1");
		System.out.println("https://github.com/Dialexio/OTA-Catalog-Parser\n");

		// Reading arguments (and performing some basic checks).
		while (i < args.length && args[i].startsWith("-")) {
			arg = args[i++];

			if (arg.equals("-b"))
				showBeta = true;

			else if (arg.equals("-d")) {
				device = "";

				if (i < args.length)
					device = args[i++];

				if (!device.matches("(AppleTV|iP(ad|hone|od))\\d(\\d)?,\\d")) {
					System.err.println("ERROR: You need to set a device with the \"-d\" argument, e.g. iPhone3,1 or iPad2,7");
					System.exit(1);
				}
			}

			else if (arg.equals("-f")) {
				xmlName = "";

				if (i < args.length)
					xmlName = args[i++];

				if (xmlName.isEmpty()) {
					System.err.println("ERROR: You need to supply a file name.");
					System.exit(2);
				}
			}

			else if (arg.equals("-m")) {
				model = "";

				if (i < args.length)
					model = args[i++];

				if (model.matches("[JKMNP]\\d(\\d)?(\\d)?[a-z]?AP")) {
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
			else if (arg.equals("-w"))
				mwMarkup = true;
		}

		// Flag whether or not we need to check the model.
		// Right now, it's just a lazy check for iPhone8,1 or iPhone8,2.
		checkModel = device.matches("iPhone8,(1|2)");

		loadFile(new File(xmlName));

		addEntries(root);

		sort();

		if (mwMarkup)
			printWikiMarkup(entryList);
		else
			printOutput(entryList);
	}

	private static void printOutput(ArrayList<OTAPackage> entryList) {
		for (OTAPackage entry:entryList) {
			// Output iOS version and build.
			System.out.println("iOS " + entry.osVersion() + " (Build " + entry.build() + ")");
			if (entry.isBeta()) // Is this a beta?
				System.out.println("This is marked as a beta release.");

			// Print prerequisites if there are any.
			if (entry.isUniversal())
				System.out.println("Requires: Not specified");
			else
				System.out.println("Requires: iOS " + entry.prerequisiteVer() + " (Build " + entry.prerequisiteBuild() + ")");

			// Date as extracted from the URL.
			System.out.println("Timestamp: " + entry.date().substring(0, 4) + "/" + entry.date().substring(4, 6) + "/" + entry.date().substring(6));

			// Print out the URL and file size.
			System.out.println("URL: " + entry.url());
			System.out.println("File size: " + entry.size());

			System.out.println();
		}
	}

	private static void printWikiMarkup(ArrayList<OTAPackage> entryList) {
		HashMap<String, Integer> buildEntryCount = new HashMap<String, Integer>(),
								 fileEntryCount = new HashMap<String, Integer>(),
				 				 osEntryCount = new HashMap<String, Integer>(),
								 prereqNestedCount = new HashMap<String, Integer>();
		HashMap<String, HashMap<String, Integer>>prereqEntryCount = new HashMap<String, HashMap<String, Integer>>(); // Build, <PrereqOS, count>

		// Count the rowspans for wiki markup.
		for (OTAPackage entry:entryList) {
			// OS version
			if (osEntryCount.containsKey(entry.osVersion())) // Increment existing count.
				osEntryCount.replace(entry.osVersion(), osEntryCount.get(entry.osVersion())+1);
			// Since it hasn't been counted, add the first tally.
			else
				osEntryCount.put(entry.osVersion(), 1);

			// Build
			if (buildEntryCount.containsKey(entry.build())) // Increment existing count.
				buildEntryCount.replace(entry.build(), buildEntryCount.get(entry.build())+1);
			// Since it hasn't been counted, add the first tally.
			else
				buildEntryCount.put(entry.build(), 1);

			// Prerequisite version
			if (prereqEntryCount.containsKey(entry.build())) // Load nested HashMap into variable temporarily, if it exists.
				prereqNestedCount = prereqEntryCount.get(entry.build());

			if (prereqNestedCount.containsKey(entry.prerequisiteVer())) // Increment existing count.
				prereqNestedCount.replace(entry.prerequisiteVer(), prereqNestedCount.get(entry.prerequisiteVer())+1);
			// Since it hasn't been counted, add the first tally.
			else
				prereqNestedCount.put(entry.prerequisiteVer(), 1);

			prereqEntryCount.put(entry.build(), prereqNestedCount);

			// File
			if (fileEntryCount.containsKey(entry.url())) // Increment existing count.
				fileEntryCount.replace(entry.url(), fileEntryCount.get(entry.url())+1);
			// Since it hasn't been counted, add the first tally.
			else 
				fileEntryCount.put(entry.url(), 1);
		}

		for (OTAPackage entry:entryList) {
			Matcher name;
			Pattern nameRegex = Pattern.compile("[0-9a-f]{40}\\.zip");
			String fileName = "";
			name = nameRegex.matcher(entry.url());
			while (name.find()) {
				fileName = name.group();
				break;
			}

			System.out.println("|-");

			// Output iOS version.
			if (osEntryCount.containsKey(entry.osVersion())) {
				System.out.print("| ");

				// Only give rowspan if there is more than one row with the OS version.
				if (osEntryCount.get(entry.osVersion()).intValue() > 1)
					System.out.print("rowspan=\"" + osEntryCount.get(entry.osVersion()) + "\" | ");

				System.out.print(entry.osVersion());

				// Give it a beta label (if it is one).
				if (entry.isBeta())
					System.out.print(" beta #"); // Number sign should be replaced by user; we can't keep track of which beta this is.

				System.out.println();
				osEntryCount.remove(entry.osVersion()); //Remove the count since we're done with it.
			}

			// If this is an Apple TV, we need to leave space for the marketing version.
			if (device.matches("AppleTV\\d(\\d)?,\\d")) {
				System.out.print("| ");

				// Only give rowspan if there is more than one row with the OS version.
				if (osEntryCount.containsKey(entry.osVersion()) && (osEntryCount.get(entry.osVersion()).intValue() > 1))
					System.out.println("rowspan=\"" + osEntryCount.get(entry.osVersion()) + "\" | ");

				System.out.println("[MARKETING VERSION]");
			}

			//Output build number.
			if (buildEntryCount.containsKey(entry.build())) {
				System.out.print("| ");

				// Only give rowspan if there is more than one row with the OS version.
				if (buildEntryCount.get(entry.build()).intValue() > 1)
					System.out.print("rowspan=\"" + buildEntryCount.get(entry.build()) + "\" | ");

				System.out.println(entry.build());
			}

			// Print prerequisites if there are any.
			if (entry.isUniversal())
				System.out.println("| colspan=\"2\" {{n/a}}");
			else {
				// Prerequisite version
				if (prereqEntryCount.containsKey(entry.build()) && prereqEntryCount.get(entry.build()).containsKey(entry.prerequisiteVer())) {
					System.out.print("| ");
					// Is there more than one of this prerequisite version tallied?
					if (prereqEntryCount.get(entry.build()).get(entry.prerequisiteVer()).intValue() > 1) {
						System.out.print("rowspan=\"" + prereqEntryCount.get(entry.build()).get(entry.prerequisiteVer()) + "\" | ");
						prereqEntryCount.get(entry.build()).remove(entry.prerequisiteVer());
					}

					System.out.println(entry.prerequisiteVer());
				}

				// Prerequisite build
				System.out.println("| " + entry.prerequisiteBuild());
			}

			// Date as extracted from the URL. Using the same rowspan count as build.
			// (3.1.1 had two builds released on different dates for iPod touch 3G.)
			if (buildEntryCount.containsKey(entry.build())) {
				System.out.print("| ");

				// Only give rowspan if there is more than one row with the OS version.
				if (buildEntryCount.get(entry.build()).intValue() > 1) {
					System.out.print("rowspan=\"" + buildEntryCount.get(entry.build()) + "\" | ");
					buildEntryCount.remove(entry.build()); //Remove the count since we already used it.
				}

				System.out.println("{{date|" + entry.date().substring(0, 4) + "|" + entry.date().substring(4, 6) + "|" + entry.date().substring(6) + "}}");
			}

			// Print file URL.
			if (fileEntryCount.containsKey(entry.url())) {
				System.out.print("| ");

				// Only give rowspan if there is more than one row with the OS version.
				if (fileEntryCount.get(entry.url()).intValue() > 1) {
					System.out.print("rowspan=\"" + fileEntryCount.get(entry.url()) + "\" | ");
				}

				System.out.println("[" + entry.url() + " " + fileName + "]");
			}

			//Print file size.
			if (fileEntryCount.containsKey(entry.url())) {
				System.out.print("| ");

				// Only give rowspan if there is more than one row with the OS version.
				if (fileEntryCount.get(entry.url()).intValue() > 1) {
					System.out.print("rowspan=\"" + fileEntryCount.get(entry.url()) + "\" | ");
					fileEntryCount.remove(entry.url());
				}

				System.out.println(entry.size());
			}
		}
	}

	private static void sort() {
		Collections.sort(entryList, new Comparator<OTAPackage>() {
			@Override
			public int compare(OTAPackage package1, OTAPackage package2) {
				return ((OTAPackage)package1).sortingPrerequisiteBuild().compareTo(((OTAPackage)package2).sortingPrerequisiteBuild());
			}
		});
		Collections.sort(entryList, new Comparator<OTAPackage>() {
			@Override
			public int compare(OTAPackage package1, OTAPackage package2) {
				return ((OTAPackage)package1).sortingBuild().compareTo(((OTAPackage)package2).sortingBuild());
			}
		});
	}
}
