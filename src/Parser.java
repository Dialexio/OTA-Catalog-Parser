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
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.regex.*;
import org.eclipse.swt.widgets.Text;

public class Parser {
	private final static ArrayList<OTAPackage> entryList = new ArrayList<OTAPackage>();
	private static boolean modelCheckRequired;
	private final static HashMap<String, Integer> buildRowspanCount = new HashMap<String, Integer>(),
		dateRowspanCount = new HashMap<String, Integer>(),
		marketingVersionRowspanCount = new HashMap<String, Integer>(),
		osVersionRowspanCount = new HashMap<String, Integer>();
	private final static HashMap<String, HashMap<String, Integer>> fileRowspanCount = new HashMap<String, HashMap<String, Integer>>(),// URL, <PrereqOS, count> 
		prereqRowspanCount = new HashMap<String, HashMap<String, Integer>>(); // Build, <PrereqOS, count>

	private static boolean showBeta = false, wiki = false;
	private static NSDictionary root;
	private static String device, maxOSVer = "", minOSVer = "", model;
	private static Text paper;

	// Getter and setter methods.
	public void defineOutput(Text output) {
		paper = output;
	}

	public int loadFile(String value) {
		try {
			root = (NSDictionary)PropertyListParser.parse(new File(value));

			if (root != null && root.containsKey("Assets"))
				return 0;

			else {
				System.err.println("ERROR: This is an Apple property list, but it's not one of Apple's OTA update catalogs.");
				return 7;
			}
		}

		catch (FileNotFoundException e) {
			if (e.getMessage().contains("Permission denied")) {
				System.err.println("ERROR: You don't have permission to read \"" + value + "\".");
				return 8;
			}

			else {
				System.err.println("ERROR: The file \"" + value + "\" can't be found.");
				return 2;
			}
		}

		catch (PropertyListFormatException e) {
			System.err.println("ERROR: This isn't an Apple property list.");
			return 6;
		}

		catch (Exception e) {
			e.printStackTrace();
			return -1;
		}
	}

	public void setDevice(String value) {
		if (value.matches("((AppleTV|iP(ad|hone|od))|Watch)(\\d)?\\d,\\d")) {
			device = value;
			modelCheckRequired = device.matches("iPhone8,(1|2)");
		}

		else
			System.err.println("ERROR: You need to set a device with the \"-d\" argument, e.g. iPhone5,1 or iPad2,7");
	}

	public void setMax(String value) {
		if (value.matches("(\\d)?\\d\\.\\d(\\.\\d)?(\\d)?"))
			maxOSVer = value;

		else if (value.isEmpty())
			return;

		else {
			if (paper == null)
				System.err.println("WARNING: For the \"-max\" argument, you need to specify a version of iOS, e.g. 4.3 or 8.0.1. Ignoring...");

			else
				paper.append("WARNING: The value entered for the maximum version is not valid. Ignoring...");
		}
	}

	public void setMin(String value) {
		if (value.matches("(\\d)?\\d\\.\\d(\\.\\d)?(\\d)?"))
			minOSVer = value;

		else if (value.isEmpty())
			return;

		else {
			if (paper == null)
				System.err.println("WARNING: For the \"-min\" argument, you need to specify a version of iOS, e.g. 4.3 or 8.0.1. Ignoring...");

			else
				paper.append("WARNING: The value entered for the minimum version is not valid. Ignoring...");
		}
	}

	public boolean setModel(String value) {
		if (modelCheckRequired) {
			if (value.matches("[JKMNP]\\d(\\d)?(\\d)?[A-Za-z]?AP")) {
				model = value;
				return true;
			}

			else {
				System.err.println("ERROR: You need to specify a model with the \"-m\" argument, e.g. N71AP");
				return false;
			}
		}

		else {
			if (value.isEmpty() == false)
				System.err.println("NOTE: A model was specified for " + device + ", despite not requiring a check. The model will be ignored.");

			return true;
		}
	}

	public void showBeta(boolean value) {
		showBeta = value;
	}

	public void wikiMarkup(boolean value) {
		wiki = value;
	}

	// Where the magic happens.
	private static void addEntries(final NSDictionary PLIST_ROOT) {
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

			// For wiki markup: If a beta has two entries
			// (one for betas, one for non-betas), don't count it twice.
			if (wiki && !entry.isDeclaredBeta() && entry.betaNumber() != 0)
				continue;

			// Device check.
			for (NSObject supportedDevice:entry.supportedDevices()) {
				if (device.equals(supportedDevice.toString())) {
					matched = true;
					break;
				}
			}

			// Model check, if needed.
			if (matched && modelCheckRequired) {
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
				entryList.add(entry);
			}
		}

		assets = null;
		entry = null;
	}

	private static void cleanup() {
		buildRowspanCount.clear();
		entryList.clear();
		fileRowspanCount.clear(); 
		dateRowspanCount.clear();
		marketingVersionRowspanCount.clear();
		osVersionRowspanCount.clear();
		prereqRowspanCount.clear();
	}

	private static void countRowspan() {
		HashMap<String, Integer> fileNestedCount, prereqNestedCount;
		String pseudoPrerequisite;

		// Count the rowspans for wiki markup.
		for (OTAPackage entry:entryList) {
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

	public void parse() {
		if (root != null) {
			addEntries(root);
			sort();
	
			if (wiki) {
				countRowspan();
				printWikiMarkup();
			}
	
			else
				printHuman();

			cleanup();
		}
	}

	private static void printLine(String value) {
		if (paper == null)
			System.out.println(value);

		else
			paper.append(value + "\n");
	}

	private static void printHuman() {
		String line = "", osName;

		for (OTAPackage entry:entryList) {
			if (device.startsWith("Watch"))
				osName = "watchOS ";
			else if (device.matches("AppleTV(2,1|3,1|3,2)"))
				osName = "iOS ";
			else if (device.startsWith("AppleTV"))
				osName = "tvOS ";
			else
				osName = "iOS ";

			// Output OS version and build.
			line = line.concat(osName + entry.marketingVersion());

				// Is this a beta?
				if (entry.betaType() > 0) {
					if (entry.betaType() == 2)
						line = line.concat(" Public");
	
					line = line.concat(" Beta " + entry.betaNumber());
				}

			printLine(line + " (Build " + entry.actualBuild() + ')');
			line = "";
			printLine("Listed as: "+ entry.osVersion() + " (Build " + entry.declaredBuild() + ')');
			printLine("Marked as beta: " + entry.isDeclaredBeta());

			// Print prerequisites if there are any.
			if (entry.isUniversal())
				printLine("Requires: Not specified");

			else
				printLine("Requires: " + entry.prerequisiteVer() + " (Build " + entry.prerequisiteBuild() + ')');

			// Date as extracted from the URL.
			printLine("Timestamp: " + entry.date('y') + '/' + entry.date('m') + '/' + entry.date('d'));

			// Print out the URL and file size.
			printLine("URL: " + entry.url());
			printLine("File size: " + entry.size() + '\n');
		}
	}

	private static void printWikiMarkup() {
		final Pattern NAME_REGEX = Pattern.compile("[0-9a-f]{40}\\.zip");
		Matcher name;
		String fileName, line = "";

		for (OTAPackage entry:entryList) {
			fileName = "";

			name = NAME_REGEX.matcher(entry.url());
			if (name.find())
				fileName = name.group();

			// Let us begin!
			printLine("|-");

			//Marketing Version for Apple Watch.
			if (device.matches("Watch(\\d)?\\d,\\d") && marketingVersionRowspanCount.containsKey(entry.marketingVersion())) {
				line = line.concat("| ");

				// Only give rowspan if there is more than one row with the OS version.
				if (marketingVersionRowspanCount.get(entry.marketingVersion()).intValue() > 1)
					line = line.concat("rowspan=\"" + marketingVersionRowspanCount.get(entry.marketingVersion()) + "\" | ");

				line = line.concat(entry.marketingVersion());

				// Give it a beta label (if it is one).
				if (entry.betaType() > 0) {
					if (entry.betaType() == 2)
						line = line.concat(" Public Beta");
					else
						line = line.concat(" beta");

					// Don't print a 1 if this is the first beta.
					if (entry.betaNumber() > 1)
						line = line.concat(" " + entry.betaNumber());
				}

				printLine(line);
				line = "";

				//Remove the count since we're done with it.
				marketingVersionRowspanCount.remove(entry.marketingVersion());
			}

			// Output OS version.
			if (osVersionRowspanCount.containsKey(entry.osVersion())) {
				// Output a filler for Marketing Version, if this is a 32-bit Apple TV.
				if (device.matches("AppleTV(2,1|3,1|3,2)")) {
					line = line.concat("| ");

					// Only give rowspan if there is more than one row with the OS version.
					if (osVersionRowspanCount.get(entry.osVersion()).intValue() > 1)
						line = line.concat("rowspan=\"" + osVersionRowspanCount.get(entry.osVersion()) + "\" | ");

					printLine(line + "[MARKETING VERSION]");
					line = "";
				}

				line = line.concat("| ");

				// Only give rowspan if there is more than one row with the OS version.
				if (osVersionRowspanCount.get(entry.osVersion()).intValue() > 1)
					line = line.concat("rowspan=\"" + osVersionRowspanCount.get(entry.osVersion()) + "\" | ");

				line = line.concat(entry.osVersion());

				// Give it a beta label (if it is one).
				if (entry.betaType() > 0) {
					if (entry.betaType() == 2)
						line = line.concat(" Public Beta");
					else
						line = line.concat(" beta");

					// Don't print a 1 if this is the first beta.
					if (entry.betaNumber() > 1)
						line = line.concat(" " + entry.betaNumber());
				}

				printLine(line);
				line = "";

				//Remove the count since we're done with it.
				osVersionRowspanCount.remove(entry.osVersion());
			}

			// Output build number.
			if (buildRowspanCount.containsKey(entry.declaredBuild())) {
				line = line.concat("| ");

				// Only give rowspan if there is more than one row with the OS version.
				// Count declaredBuild() instead of actualBuild() so the entry pointing betas to the final build is treated separately.
				if (buildRowspanCount.get(entry.declaredBuild()).intValue() > 1)
					line = line.concat("rowspan=\"" + buildRowspanCount.get(entry.declaredBuild()) + "\" | ");

				//Remove the count since we're done with it.
				buildRowspanCount.remove(entry.declaredBuild());

				line = line.concat(entry.actualBuild());

				// Do we have a false build number?
				if (!entry.actualBuild().equals(entry.declaredBuild()))
					line = line.concat("<ref name=\"fakefive\" />");

				printLine(line);
				line = "";
			}

			// Print prerequisites if there are any.
			if (entry.isUniversal()) {
				line = line.concat("| colspan=\"2\" {{n/a");

				// Is this "universal" OTA update intended for betas?
				if (entry.isDeclaredBeta() && entry.betaType() == 0)
					line = line.concat("|Beta");

				printLine(line + "}}");
				line = "";
			}
			else {
				// Prerequisite version
				if (prereqRowspanCount.containsKey(entry.declaredBuild()) && prereqRowspanCount.get(entry.declaredBuild()).containsKey(entry.prerequisiteVer())) {
					line = line.concat("| ");

					// Is there more than one of this prerequisite version tallied?
					// Also do not use rowspan if the prerequisite build is a beta.
					if (!entry.prerequisiteBuild().matches("(\\d)?\\d[A-Z][45]\\d{3}[a-z]") && prereqRowspanCount.get(entry.declaredBuild()).get(entry.prerequisiteVer()).intValue() > 1) {
						line = line.concat("rowspan=\"" + prereqRowspanCount.get(entry.declaredBuild()).get(entry.prerequisiteVer()) + "\" | ");
						prereqRowspanCount.get(entry.declaredBuild()).remove(entry.prerequisiteVer());
					}

					line = line.concat(entry.prerequisiteVer());

					// Very quick check if prerequisite is a beta. Won't work if close to final release.
					if (entry.prerequisiteBuild().matches("(\\d)?\\d[A-Z][45]\\d{3}[a-z]"))
						line = line.concat(" beta #");

					printLine(line);
					line = "";
				}

				// Prerequisite build
				printLine("| " + entry.prerequisiteBuild());
			}

			// Date as extracted from the URL. Using the same rowspan count as build.
			// (3.1.1 had two builds released on different dates for iPod touch 3G.)
			if (dateRowspanCount.containsKey(entry.actualBuild())) {
				line = line.concat("| ");

				// Only give rowspan if there is more than one row with the OS version.
				if (dateRowspanCount.get(entry.actualBuild()).intValue() > 1) {
					line = line.concat("rowspan=\"" + dateRowspanCount.get(entry.actualBuild()) + "\" | ");
					dateRowspanCount.remove(entry.actualBuild()); //Remove the count since we already used it.
				}

				printLine(line + "{{date|" + entry.date('y') + '|' + entry.date('m') + '|' + entry.date('d') + "}}");
				line = "";
			}

			if (fileRowspanCount.containsKey(entry.url()) && fileRowspanCount.get(entry.url()).containsKey(entry.prerequisiteVer())) {
				line = line.concat("| ");

				// Is there more than one of this prerequisite version tallied?
				// Also do not use rowspan if the prerequisite build is a beta.
				if (fileRowspanCount.get(entry.url()).get(entry.prerequisiteVer()).intValue() > 1)
					line = line.concat("rowspan=\"" + fileRowspanCount.get(entry.url()).get(entry.prerequisiteVer()) + "\" | ");

				line = line.concat('[' + entry.url() + ' ' + fileName + "]\n| ");

				//Print file size.

				// Only give rowspan if there is more than one row with the OS version.
				if (fileRowspanCount.get(entry.url()).get(entry.prerequisiteVer()).intValue() > 1)
					line = line.concat("rowspan=\"" + fileRowspanCount.get(entry.url()).get(entry.prerequisiteVer()) + "\" | ");

				printLine(line + entry.size());
				line = "";

				//Remove the count since we're done with it.
				fileRowspanCount.get(entry.url()).remove(entry.prerequisiteVer());
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
		Collections.sort(entryList, new Comparator<OTAPackage>() {
			@Override
			public int compare(OTAPackage package1, OTAPackage package2) {
				return ((OTAPackage)package1).sortingMarketingVersion().compareTo(((OTAPackage)package2).sortingMarketingVersion());
			}
		});
	}
}