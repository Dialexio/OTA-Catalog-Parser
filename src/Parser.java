/*
 * OTA Catalog Parser 0.3
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.regex.*;

public class Parser {
	public static void main(String[] args) {
		boolean checkModel, mwMarkup = false, showBeta = false;
		File file = null;
		HashMap<String, Integer> buildEntryCount = null, osEntryCount = null;
		int i = 0;
		NSDictionary root;
		String arg = "", device = "", model = "", osVer = "", xmlName = "";

		System.out.println("OTA Catalog Parser v0.3");
		System.out.println("https://github.com/Dialexio/OTA-Catalog-Parser\n");

		// Reading and (lazily) checking arguments.
		while (i < args.length && args[i].startsWith("-")) {
			arg = args[i++];

			// Should we display beta?
			if (arg.equals("-b"))
				showBeta = true;
			// We need to know the device.
			else if (arg.equals("-d")) {
				if (i < args.length)
					device = args[i++];
				else
					System.err.println("-d requires a device, e.g. iPad2,1 or iPhone6,2");
			}
			// We also need to know what file we're looking at.
			else if (arg.equals("-f")) {
				if (i < args.length)
					xmlName = args[i++];
				else
					System.err.println("-f requires a filename");
			}
			else if (arg.equals("-m")) {
				if (i < args.length)
					model = args[i++];
				else
					System.err.println("-m requires a device, e.g. N71AP or N66mAP");
			}
			else if (arg.equals("-o")) {
				if (i < args.length)
					osVer = args[i++];
				else
					System.err.println("-o requires an OS version, e.g. 4.3 or 8.0.1");
			}
			else if (arg.equals("-w"))
				mwMarkup = true;
		}

		// Flag whether or not we need to check the model.
		checkModel = (device.matches("iPhone8,(1|2)"));

		if (!device.matches("(AppleTV|iP(ad|hone|od))\\d(\\d)?,\\d")) {
			System.err.println("You need to set a device with the \"-d\" argument, e.g. iPhone3,1 or iPad2,7");
			System.exit(1);
		}
		if (xmlName.isEmpty()) {
			System.err.println("You need to set a file name with the \"-f\" argument.");
			System.exit(2);
		}
		if (checkModel && !model.matches("[JKMNP]\\d(\\d)?(\\d)?[a-z]?AP")) {
			System.err.println("You need to specify a model with the \"-m\" argument, e.g. N71AP");
			System.exit(3);
		}
		if (!osVer.isEmpty() && !osVer.matches("\\d\\.\\d(\\.\\d)?")) {
			System.err.println("You need to specify a version of iOS if you are using the \"-o\" argument, e.g. 4.3 or 8.0.1");
			System.exit(4);
		}

		file = new File(xmlName);

		try {
			ArrayList<OTAPackage> entryList = new ArrayList<OTAPackage>();
			root = (NSDictionary)PropertyListParser.parse(file); // The first <dict>.

			NSObject[] assets = ((NSArray)root.objectForKey("Assets")).getArray(); // Looking for the array with key "Assets."

			// Look at every item in the array with the key "Assets."
			for (NSObject item:assets) {
				boolean entryMatch = false;
				OTAPackage entry = new OTAPackage((NSDictionary)item); // Feed it into our own object. This will be used for sorting in the future.

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

				// OS version check.
				if (entryMatch && !osVer.isEmpty()) {
					if (osVer.compareTo(entry.osVersion()) <= 0)
						entryMatch = true;
					else
						continue;
				}

				// Add it after it survives the checks.
				if (entryMatch)
					entryList.add(entry);
			}

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

			// Count the colspans for wiki markup.
			if (mwMarkup) {
				buildEntryCount = new HashMap<String, Integer>();
				osEntryCount = new HashMap<String, Integer>();

				for (OTAPackage entry:entryList) {
					if (osEntryCount.containsKey(entry.osVersion()))
						osEntryCount.put(entry.osVersion(), osEntryCount.get(entry.osVersion())+1);
					else
						osEntryCount.put(entry.osVersion(), 1);

					if (buildEntryCount.containsKey(entry.build()))
						buildEntryCount.put(entry.build(), buildEntryCount.get(entry.build())+1);
					else
						buildEntryCount.put(entry.build(), 1);
				}
			}

			for (OTAPackage entry:entryList) {
				if (mwMarkup) {
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

						// Only give colspan if there is more than one row with the OS version.
						if (osEntryCount.get(entry.osVersion()).intValue() > 1)
							System.out.print("colspan=\"" + osEntryCount.get(entry.osVersion()) + "\" | ");

						System.out.print(entry.osVersion());

						// Give it a beta label (if it is one).
						if (entry.isBeta())
							System.out.print(" beta #"); // Number sign should be replaced by user. We can't keep track of which beta this is.

						System.out.println();
						osEntryCount.remove(entry.osVersion()); //Remove the count since we're done with it.
					}

					// If this is an Apple TV, we need to leave space for the marketing version.
					if (device.matches("AppleTV\\d,\\d")) {
						System.out.print("| ");

						// Only give colspan if there is more than one row with the OS version.
						if (osEntryCount.containsKey(entry.osVersion()) && (osEntryCount.get(entry.osVersion()).intValue() > 1))
								System.out.println("colspan=\"" + osEntryCount.get(entry.osVersion()) + "\" | ");

						System.out.println("[MARKETING VERSION]");
					}


					//Output build number.
					if (buildEntryCount.containsKey(entry.build())) {
						System.out.print("| ");

						// Only give colspan if there is more than one row with the OS version.
						if (buildEntryCount.get(entry.build()).intValue() > 1) {
							System.out.print("colspan=\"" + buildEntryCount.get(entry.build()) + "\" | ");
							buildEntryCount.remove(entry.build()); //Remove the count since we already used it.
						}

						System.out.println(entry.build());
					}

					// Print prerequisites if there are any.
					if (entry.isUniversal())
						System.out.println("| colspan=\"2\" {{n/a}}");
					else {
						System.out.println("| " + entry.prerequisiteVer());
						System.out.println("| " + entry.prerequisiteBuild());
					}

					// Date as extracted from the URL.
					System.out.println("| {{date|" + entry.date().substring(0, 4) + "|" + entry.date().substring(4, 6) + "|" + entry.date().substring(6) + "}}");

					// Prints out fileURL, reuses fileURL to store just the file name, and then prints fileURL again.
					System.out.print("| [" + entry.url() + " ");
					System.out.println(fileName + "]");

					System.out.println("| " + entry.size());
				}
				else {
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
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
}
