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
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.regex.*;
import org.xml.sax.SAXException;

public class Parser {
	private final static ArrayList<OTAPackage> ENTRY_LIST = new ArrayList<OTAPackage>();

	private static boolean checkModel, showBeta = false;
	private static NSDictionary root = null;
	private static String device = "", maxOSVer = "", minOSVer = "", model = "";

	private static HashMap<String, Integer> buildEntryCount = new HashMap<String, Integer>(),
			 fileEntryCount = new HashMap<String, Integer>(),
			 osEntryCount = new HashMap<String, Integer>();
	private static HashMap<String, HashMap<String, Integer>>prereqEntryCount = new HashMap<String, HashMap<String, Integer>>(); // Build, <PrereqOS, count>


	private static void addEntries(final NSDictionary PLIST_ROOT) {
		boolean matched;
		NSObject[] assets = ((NSArray)PLIST_ROOT.objectForKey("Assets")).getArray(); // Looking for the array with key "Assets."
		OTAPackage entry;

		// Look at every item in the array with the key "Assets."
		for (NSObject item:assets) {
			entry = new OTAPackage((NSDictionary)item); // Feed it into our own object. This will be used for sorting in the future.
			matched = false;

			// Beta check.
			if (!showBeta && entry.declaredBeta())
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
			if (matched && !maxOSVer.isEmpty() && (maxOSVer.compareTo(entry.osVersion()) < 0))
					continue;
			if (matched && !minOSVer.isEmpty() && (minOSVer.compareTo(entry.osVersion()) > 0))
					continue;

			// Add it after it survives the checks.
			if (matched)
				ENTRY_LIST.add(entry);
		}
	}

	private static void countRowspan(ArrayList<OTAPackage> entryList) {
		// Count the rowspans for wiki markup.
		for (OTAPackage entry:entryList) {
			HashMap<String, Integer> prereqNestedCount = new HashMap<String, Integer>();

			// OS version
			if (osEntryCount.containsKey(entry.osVersion())) // Increment existing count.
				osEntryCount.replace(entry.osVersion(), osEntryCount.get(entry.osVersion())+1);
			// Since it hasn't been counted, add the first tally.
			else
				osEntryCount.put(entry.osVersion(), 1);

			// Build
			if (buildEntryCount.containsKey(entry.declaredBuild())) // Increment existing count.
				buildEntryCount.replace(entry.declaredBuild(), buildEntryCount.get(entry.declaredBuild())+1);
			// Since it hasn't been counted, add the first tally.
			else
				buildEntryCount.put(entry.declaredBuild(), 1);

			// Prerequisite version
			if (prereqEntryCount.containsKey(entry.declaredBuild())) // Load nested HashMap into variable temporarily, if it exists.
				prereqNestedCount = prereqEntryCount.get(entry.declaredBuild());

			if (prereqNestedCount.containsKey(entry.prerequisiteVer())) // Increment existing count.
				prereqNestedCount.replace(entry.prerequisiteVer(), prereqNestedCount.get(entry.prerequisiteVer())+1);
			// Since it hasn't been counted, add the first tally.
			else
				prereqNestedCount.put(entry.prerequisiteVer(), 1);

			prereqEntryCount.put(entry.declaredBuild(), prereqNestedCount);

			// File
			if (fileEntryCount.containsKey(entry.url())) // Increment existing count.
				fileEntryCount.replace(entry.url(), fileEntryCount.get(entry.url())+1);
			// Since it hasn't been counted, add the first tally.
			else 
				fileEntryCount.put(entry.url(), 1);
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
		boolean mwMarkup = false;
		int i = 0;
		String arg = "", plistName = "";

		System.out.println("OTA Catalog Parser v0.3.3");
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
			else if (arg.equals("-w"))
				mwMarkup = true;
		}

		// Flag whether or not we need to check the model.
		// Right now, it's just a lazy check for iPhone8,1 or iPhone8,2.
		checkModel = device.matches("iPhone8,(1|2)");

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

	private static void printOutput(ArrayList<OTAPackage> entryList) {
		for (OTAPackage entry:entryList) {
			// Output iOS version and build.
			System.out.print("iOS " + entry.osVersion() + " (Build " + entry.actualBuild() + ")");
			if (!entry.actualBuild().equals(entry.declaredBuild()))
				System.out.print(" (listed as " + entry.declaredBuild() + ")");
			System.out.println();

			// Is this a beta?
			if (entry.declaredBeta())
				System.out.println("This is marked as a beta release.");

			// Print prerequisites if there are any.
			if (entry.isUniversal())
				System.out.println("Requires: Not specified");
			else {
				System.out.print("Requires: ");

				// Version isn't always specified.
				if (entry.prerequisiteVer().equals("N/A"))
					System.out.print("Version not specified");
				else
					System.out.print("iOS " + entry.prerequisiteVer());

				System.out.println(" (Build " + entry.prerequisiteBuild() + ")");
			}

			// Date as extracted from the URL.
			System.out.println("Timestamp: " + entry.date().substring(0, 4) + "/" + entry.date().substring(4, 6) + "/" + entry.date().substring(6));

			// Print out the URL and file size.
			System.out.println("URL: " + entry.url());
			System.out.println("File size: " + entry.size());

			System.out.println();
		}
	}

	private static void printWikiMarkup(ArrayList<OTAPackage> entryList) {
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

				// If this is an Apple TV, we need to leave space for the marketing version.
				if (device.matches("AppleTV\\d(\\d)?,\\d")) {
					System.out.print("| ");

					// Only give rowspan if there is more than one row with the OS version.
					if (osEntryCount.get(entry.osVersion()).intValue() > 1)
						System.out.print("rowspan=\"" + osEntryCount.get(entry.osVersion()) + "\" | ");

					System.out.println("[MARKETING VERSION]");
				}

				//Remove the count since we're done with it.
				osEntryCount.remove(entry.osVersion());
			}

			//Output build number.
			if (buildEntryCount.containsKey(entry.declaredBuild())) {
				System.out.print("| ");

				// Only give rowspan if there is more than one row with the OS version.
				if (buildEntryCount.get(entry.declaredBuild()).intValue() > 1)
					System.out.print("rowspan=\"" + buildEntryCount.get(entry.declaredBuild()) + "\" | ");

				if (entry.declaredBeta() && !entry.isBeta())
					System.out.println(entry.actualBuild() + "<ref name=\"fakefive\" />");
				else
					System.out.println(entry.declaredBuild() + " ");
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
				if (prereqEntryCount.containsKey(entry.declaredBuild()) && prereqEntryCount.get(entry.declaredBuild()).containsKey(entry.prerequisiteVer())) {
					System.out.print("| ");

					// Is there more than one of this prerequisite version tallied?
					if (prereqEntryCount.get(entry.declaredBuild()).get(entry.prerequisiteVer()).intValue() > 1) {
						System.out.print("rowspan=\"" + prereqEntryCount.get(entry.declaredBuild()).get(entry.prerequisiteVer()) + "\" | ");
						prereqEntryCount.get(entry.declaredBuild()).remove(entry.prerequisiteVer());
					}

					System.out.println(entry.prerequisiteVer());
				}

				// Prerequisite build
				System.out.println("| " + entry.prerequisiteBuild());
			}

			// Date as extracted from the URL. Using the same rowspan count as build.
			// (3.1.1 had two builds released on different dates for iPod touch 3G.)
			if (buildEntryCount.containsKey(entry.declaredBuild())) {
				System.out.print("| ");

				// Only give rowspan if there is more than one row with the OS version.
				if (buildEntryCount.get(entry.declaredBuild()).intValue() > 1) {
					System.out.print("rowspan=\"" + buildEntryCount.get(entry.declaredBuild()) + "\" | ");
					buildEntryCount.remove(entry.declaredBuild()); //Remove the count since we already used it.
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
	}
}
