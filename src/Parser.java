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
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.regex.*;
import org.eclipse.swt.widgets.Text;

public class Parser {
	private final static ArrayList<OTAPackage> entryList = new ArrayList<OTAPackage>();
	private final static HashMap<String, Integer> buildRowspanCount = new HashMap<String, Integer>(),
		dateRowspanCount = new HashMap<String, Integer>(),
		marketingVersionRowspanCount = new HashMap<String, Integer>(),
		osVersionRowspanCount = new HashMap<String, Integer>();
	private final static HashMap<String, HashMap<String, Integer>> fileRowspanCount = new HashMap<String, HashMap<String, Integer>>(),// URL, <PrereqOS, count> 
		prereqBuildRowspanCount = new HashMap<String, HashMap<String, Integer>>(), // Build, <PrereqBuild, count>
		prereqOSRowspanCount = new HashMap<String, HashMap<String, Integer>>(); // Build, <PrereqOS, count>

	private static boolean isWatch, showBeta = false, modelCheckRequired, wiki = false;
	private static NSDictionary root;
	private static String device, maxOSVer = "", minOSVer = "", model;
	private static Text paper;

	// Getter and setter methods.
	public void defineOutput(Text output) {
		paper = output;
	}

	public int loadXML(String locXML) {
		try {
			if (locXML.startsWith("http://mesu.apple.com/assets/"))
				root = (NSDictionary)PropertyListParser.parse(new URL(locXML).openStream());

			else if (locXML.contains("://")) {
				System.err.println("ERROR: The URL supplied should belong to mesu.apple.com.");
				return 9;
			}

			else
				root = (NSDictionary)PropertyListParser.parse(new File(locXML));

			// Make sure the PLIST is what we want.
			if (root != null && root.containsKey("Assets"))
				return 0;

			else {
				System.err.println("ERROR: This is an Apple property list, but it's not one of Apple's OTA update catalogs.");
				return 7;
			}
		}

		catch (FileNotFoundException e) {
			if (e.getMessage().contains("Permission denied")) {
				System.err.println("ERROR: You don't have permission to read \"" + locXML + "\".");
				return 8;
			}

			else {
				System.err.println("ERROR: The file \"" + locXML + "\" can't be found.");
				return 2;
			}
		}

		catch (PropertyListFormatException e) {
			System.err.println("ERROR: This isn't an Apple property list.");
			return 6;
		}

		catch (UnknownHostException e) {
			System.err.println("ERROR: Can't find the host "+ e.getMessage());
			return 10;
		}

		catch (Exception e) {
			e.printStackTrace();
			return -1;
		}
	}

	public void setDevice(String value) {
		if (value.matches("((AppleTV|iP(ad|hone|od))|Watch)(\\d)?\\d,\\d")) {
			device = value;
			isWatch = device.matches("Watch(\\d)?\\d,\\d");
			modelCheckRequired = device.matches("iPhone8,(1|2|4)");
		}

		else
			System.err.println("ERROR: You need to set a device with the \"-d\" argument, e.g. iPhone5,1 or iPad2,7");
	}

	public void setMax(String value) {
		if (value.matches("(\\d)?\\d\\.\\d(\\.\\d)?(\\d)?"))
			maxOSVer = value;

		else if (value.isEmpty())
			maxOSVer = "";

		else {
			if (paper == null)
				System.err.println("WARNING: For the \"-max\" argument, you need to specify a version of iOS, e.g. 4.3 or 8.0.1. Ignoring...");

			else
				paper.append("WARNING: The value entered for the maximum version is not valid. Ignoring...");

			maxOSVer = "";
		}
	}

	public void setMin(String value) {
		if (value.matches("(\\d)?\\d\\.\\d(\\.\\d)?(\\d)?"))
			minOSVer = value;

		else if (value.isEmpty())
			minOSVer = "";

		else {
			if (paper == null)
				System.err.println("WARNING: For the \"-min\" argument, you need to specify a version of iOS, e.g. 4.3 or 8.0.1. Ignoring...");

			else
				paper.append("WARNING: The value entered for the minimum version is not valid. Ignoring...");

			minOSVer = "";
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
			if (showBeta == false && entry.betaType() > 0)
				continue;

			// For wiki markup: If a beta has two entries
			// (one for betas, one for non-betas), don't count it twice.
			if (wiki && entry.isReleaseTypeDeclared() == false && entry.betaNumber() > 0 && entry.documentationID().equals("iOS7Seed6") == false)
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
				if (maxOSVer.isEmpty() == false && (maxOSVer.compareTo(entry.marketingVersion()) < 0))
					continue;
				if (minOSVer.isEmpty() == false && (minOSVer.compareTo(entry.marketingVersion()) > 0))
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
		prereqBuildRowspanCount.clear();
		prereqOSRowspanCount.clear();
	}

	private static void countRowspan() {
		HashMap<String, Integer> fileNestedCount, prereqBuildNestedCount, prereqOSNestedCount;

		// Count the rowspans for wiki markup.
		for (OTAPackage entry:entryList) {
			fileNestedCount = new HashMap<String, Integer>();
			prereqBuildNestedCount = new HashMap<String, Integer>();
			prereqOSNestedCount = new HashMap<String, Integer>();


			// Build
			// Increment the count if it exists.
			// If not, add the first tally.
			if (buildRowspanCount.containsKey(entry.declaredBuild()))
				buildRowspanCount.put(entry.declaredBuild(), buildRowspanCount.get(entry.declaredBuild()) + 1);

			else
				buildRowspanCount.put(entry.declaredBuild(), 1);


			// Date (Count actualBuild() and not date() because x.0 GM and x.1 beta can technically be pushed at the same time.)
			// Increment the count if it exists.
			// If not, add the first tally.
			if (dateRowspanCount.containsKey(entry.actualBuild()))
				dateRowspanCount.put(entry.actualBuild(), dateRowspanCount.get(entry.actualBuild()) + 1);

			else
				dateRowspanCount.put(entry.actualBuild(), 1);


			// File URL
			// Load nested HashMap into a temporary variable, if it exists.
			if (fileRowspanCount.containsKey(entry.url()))
				fileNestedCount = fileRowspanCount.get(entry.url());


			// Increment the count if it exists.
			// If not, add the first tally.
			if (fileNestedCount.containsKey(entry.prerequisiteVer()))
				fileNestedCount.put(entry.prerequisiteVer(), fileNestedCount.get(entry.prerequisiteVer()) + 1);

			else
				fileNestedCount.put(entry.prerequisiteVer(), 1);

			fileRowspanCount.put(entry.url(), fileNestedCount);


			// Marketing version
			// Increment the count if it exists.
			// If not, add the first tally.
			if (marketingVersionRowspanCount.containsKey(entry.marketingVersion()))
				marketingVersionRowspanCount.put(entry.marketingVersion(), marketingVersionRowspanCount.get(entry.marketingVersion()) + 1);

			else
				marketingVersionRowspanCount.put(entry.marketingVersion(), 1);


			// OS version
			// Increment the count if it exists.
			// If not, add the first tally.
			if (osVersionRowspanCount.containsKey(entry.osVersion()))
				osVersionRowspanCount.put(entry.osVersion(), osVersionRowspanCount.get(entry.osVersion()) + 1);

			else
				osVersionRowspanCount.put(entry.osVersion(), 1);


			// Prerequisite OS version
			if (prereqOSRowspanCount.containsKey(entry.declaredBuild())) // Load nested HashMap into variable temporarily, if it exists.
				prereqOSNestedCount = prereqOSRowspanCount.get(entry.declaredBuild());

			// Increment the count if it exists.
			// If not, add the first tally.
			if (prereqOSNestedCount.containsKey(entry.prerequisiteVer()))
				prereqOSNestedCount.put(entry.prerequisiteVer(), prereqOSNestedCount.get(entry.prerequisiteVer()) + 1);

			else
				prereqOSNestedCount.put(entry.prerequisiteVer(), 1);

			prereqOSRowspanCount.put(entry.declaredBuild(), prereqOSNestedCount);


			// Prerequisite Build version
			if (prereqBuildRowspanCount.containsKey(entry.declaredBuild())) // Load nested HashMap into variable temporarily, if it exists.
				prereqBuildNestedCount = prereqBuildRowspanCount.get(entry.declaredBuild());

			// Increment the count if it exists.
			// If not, add the first tally.
			if (prereqBuildNestedCount.containsKey(entry.prerequisiteBuild()))
				prereqBuildNestedCount.put(entry.prerequisiteBuild(), prereqBuildNestedCount.get(entry.prerequisiteBuild()) + 1);

			else
				prereqBuildNestedCount.put(entry.prerequisiteBuild(), 1);

			prereqBuildRowspanCount.put(entry.declaredBuild(), prereqBuildNestedCount);
		}

		fileNestedCount = null;
		prereqBuildNestedCount = null;
		prereqOSNestedCount = null;
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
			paper.append(value + '\n');
	}

	private static void printHuman() {
		String line = "", osName;

		for (OTAPackage entry:entryList) {
			if (isWatch)
				osName = "watchOS ";
			else if (device.matches("AppleTV(2,1|3,1|3,2)"))
				osName = "Apple TV software ";
			else if (device.startsWith("AppleTV"))
				osName = "tvOS ";
			else
				osName = "iOS ";

			// Output OS version and build.
			line = line.concat(osName + entry.marketingVersion());

			// Give it a beta label (if it is one).
			if (entry.betaType() > 0) {
				switch (entry.betaType()) {
					case 1:
						line = line.concat(" Public Beta");
						break;
					case 2:
						line = line.concat(" beta");
						break;
					case 3:
						line = line.concat(" Carrier Beta");
						break;
					case 4:
						line = line.concat(" Internal");
						break;
				}

				// Don't print a 1 if this is the first beta.
				if (entry.betaNumber() > 1)
					line = line.concat(" " + entry.betaNumber());
			}

			printLine(line + " (Build " + entry.actualBuild() + ')');
			line = "";
			printLine("Listed as: "+ entry.osVersion() + " (Build " + entry.declaredBuild() + ')');
			printLine("Marked as beta: " + entry.isReleaseTypeDeclared());

			// Print prerequisites if there are any.
			if (entry.isUniversal())
				printLine("Requires: Not specified");

			else
				printLine("Requires: " + entry.prerequisiteVer() + " (Build " + entry.prerequisiteBuild() + ')');

			// Date as extracted from the URL.
			printLine("Timestamp: " + entry.date('y') + '/' + entry.date('m') + '/' + entry.date('d'));

			// Compatibility Version.
			printLine("Compatibility Version: " + entry.compatibilityVersion());

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

			// Marketing Version for Apple Watch.
			if (isWatch && marketingVersionRowspanCount.containsKey(entry.marketingVersion())) {
				line = "| ";

				// Only give rowspan if there is more than one row with the OS version.
				if (marketingVersionRowspanCount.get(entry.marketingVersion()) > 1)
					line = line.concat("rowspan=\"" + marketingVersionRowspanCount.get(entry.marketingVersion()) + "\" | ");

				line = line.concat(entry.marketingVersion());

				// Give it a beta label (if it is one).
				if (entry.betaType() > 0) {
					switch (entry.betaType()) {
						case 1:
							line = line.concat(" Public Beta");
							break;
						case 2:
						case 3:
							line = line.concat(" beta");
							break;
						case 4:
							line = line.concat(" Internal");
							break;
					}

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
					// Only give rowspan if there is more than one row with the OS version.
					if (osVersionRowspanCount.get(entry.osVersion()) > 1)
						line = "| rowspan=\"" + osVersionRowspanCount.get(entry.osVersion()) + "\" | ";

					printLine(line + "[MARKETING VERSION]");
				}

				line = "| ";

				// Only give rowspan if there is more than one row with the OS version.
				// (And this isn't the universal Apple Watch entry.)
				if (osVersionRowspanCount.get(entry.osVersion()).intValue() > 1)
					if ((isWatch == false) || (isWatch && entry.isUniversal() == false))
						line = line.concat("rowspan=\"" + osVersionRowspanCount.get(entry.osVersion()) + "\" | ");
				line = line.concat(entry.osVersion());

				// Give it a beta label (if it is one).
				if (entry.betaType() > 0) {
					switch (entry.betaType()) {
						case 1:
							line = line.concat(" Public Beta");
							break;
						case 2:
						case 3:
							line = line.concat(" beta");
							break;
						case 4:
							line = line.concat(" Internal");
							break;
					}

					// Don't print a 1 if this is the first beta.
					if (entry.betaNumber() > 1)
						line = line.concat(" " + entry.betaNumber());
				}

				printLine(line);
				line = "";

				//Remove the count when we're done with it.
				if ((isWatch == false) || (isWatch && entry.isUniversal() == false))
					osVersionRowspanCount.remove(entry.osVersion());

				else
					osVersionRowspanCount.put(entry.osVersion(), osVersionRowspanCount.get(entry.osVersion()).intValue() - 1);
			}

			// Output build number.
			if (buildRowspanCount.containsKey(entry.declaredBuild())) {
				line = "| ";

				// Only give rowspan if there is more than one row with the OS version.
				// Count declaredBuild() instead of actualBuild() so the entry pointing betas to the final build is treated separately.
				if (buildRowspanCount.get(entry.declaredBuild()).intValue() > 1)
					line = line.concat("rowspan=\"" + buildRowspanCount.get(entry.declaredBuild()) + "\" | ");

				//Remove the count since we're done with it.
				buildRowspanCount.remove(entry.declaredBuild());

				line = line.concat(entry.actualBuild());

				// Do we have a false build number? If so, add a footnote reference.
				if (entry.isHonestBuild() == false)
					line = line.concat("<ref name=\"fakefive\" />");

				printLine(line);
				line = "";
			}

			// Print prerequisites if there are any.
			if (entry.isUniversal())
				printLine("| colspan=\"2\" {{n/a}}");

			else {
				// Prerequisite version
				if (prereqOSRowspanCount.containsKey(entry.declaredBuild()) && prereqOSRowspanCount.get(entry.declaredBuild()).containsKey(entry.prerequisiteVer())) {
					line = "| ";

					// Is there more than one of this prerequisite version tallied?
					// Also do not use rowspan if the prerequisite build is a beta.
					if (entry.prerequisiteBuild().matches(OTAPackage.REGEX_BETA) == false && prereqOSRowspanCount.get(entry.declaredBuild()).get(entry.prerequisiteVer()).intValue() > 1) {
						line = line.concat("rowspan=\"" + prereqOSRowspanCount.get(entry.declaredBuild()).get(entry.prerequisiteVer()) + "\" | ");
						prereqOSRowspanCount.get(entry.declaredBuild()).remove(entry.prerequisiteVer());
					}

					// If this is a GM, print the link to Golden Master.
					if (entry.prerequisiteVer().contains(" GM"))
						line = line.concat(entry.prerequisiteVer().replace("GM", "[[Golden Master|GM]]"));

					else
						line = line.concat(entry.prerequisiteVer());

					// Very quick check if prerequisite is a beta. Won't work if close to final release.
					if (entry.prerequisiteBuild().matches(OTAPackage.REGEX_BETA))
						line = line.concat(" beta #");

					printLine(line);
					line = "";
				}

				// Prerequisite build
				if (prereqBuildRowspanCount.containsKey(entry.declaredBuild()) && prereqBuildRowspanCount.get(entry.declaredBuild()).containsKey(entry.prerequisiteBuild())) {
					line = "| ";

					// Is there more than one of this prerequisite build tallied?
					// Also do not use rowspan if the prerequisite build is a beta.
					if (prereqBuildRowspanCount.get(entry.declaredBuild()).get(entry.prerequisiteBuild()).intValue() > 1) {
						line = line.concat("rowspan=\"" + prereqBuildRowspanCount.get(entry.declaredBuild()).get(entry.prerequisiteBuild()) + "\" | ");
						prereqBuildRowspanCount.get(entry.declaredBuild()).remove(entry.prerequisiteBuild());
					}

					printLine(line + entry.prerequisiteBuild());
					line = "";
				}
			}

			if (entry.compatibilityVersion() > 0)
				printLine("| " + entry.compatibilityVersion());

			// Date as extracted from the URL. Using the same rowspan count as build.
			// (3.1.1 had two builds released on different dates for iPod touch 3G.)
			if (dateRowspanCount.containsKey(entry.actualBuild())) {
				line = "| ";

				// Only give rowspan if there is more than one row with the OS version.
				if (dateRowspanCount.get(entry.actualBuild()).intValue() > 1) {
					line = line.concat("rowspan=\"" + dateRowspanCount.get(entry.actualBuild()) + "\" | ");
					dateRowspanCount.remove(entry.actualBuild()); //Remove the count since we already used it.
				}

				printLine(line + "{{date|" + entry.date('y') + '|' + entry.date('m') + '|' + entry.date('d') + "}}");
				line = "";
			}

			// Release Type.
			if (isWatch == false) {
				switch (entry.betaType()) {
					case 1:
					case 2:
						printLine("| Beta");
						break;
					case 3:
						printLine("| Carrier");
						break;
					case 4:
						printLine("| Internal");
						break;
					default:
						if (entry.isReleaseTypeDeclared())
							printLine("| Beta");
	
						else
							printLine("| {{n/a}}");
	
						break;
				}
			}

			if (fileRowspanCount.containsKey(entry.url()) && fileRowspanCount.get(entry.url()).containsKey(entry.prerequisiteVer())) {
				line = "| ";

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
				return (((OTAPackage)package1).betaType() + "").compareTo(((OTAPackage)package2).betaType() + "");
			}
		});
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