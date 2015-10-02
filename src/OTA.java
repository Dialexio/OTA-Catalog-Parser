/*
 * OTA Catalog Parser 0.1
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
import java.text.NumberFormat;
import java.util.Locale;

public class OTA {
	public static void main(String[] args) {
		boolean mwMarkup = false;
		File file = null;
		int i = 0;
		NSDictionary entry, root;
		String arg = "", device = "", fileName = "";

		System.out.println("OTA Catalog Parser v0.1");

		// Checking arguments.
		while (i < args.length && args[i].startsWith("-")) {
			arg = args[i++];

			// We need to know the device.
			if (arg.equals("-d")) {
				if (i < args.length) {
					device = args[i++];
				}
				else
					System.err.println("-d requires a device, e.g. iPad2,1 or iPhone6,2");
			}
			// We also need to know what file we're looking at.
			else if (arg.equals("-f")) {
				if (i < args.length) {
					fileName = args[i++];
				}
				else
					System.err.println("-f requires a filename");
			}
			else if (arg.equals("-w")) {
				mwMarkup = true;
			}
		}

		if (device.isEmpty()) {
			System.err.println("You need to set a device with the \"-d\" argument.");
			System.exit(1);
		}
		if (fileName.isEmpty()) {
			System.err.println("You need to set a file name with the \"-f\" argument.");
			System.exit(2);
		}

		file = new File(fileName);

		try {
			root = (NSDictionary)PropertyListParser.parse(file); // The first <dict>.
			NSObject[] assets = ((NSArray)root.objectForKey("Assets")).getArray(); // Looking for the array with key "Assets."

			// Check every item in the array with the key "Assets."
			for (NSObject item:assets) {
				entry = (NSDictionary)item; //...which should be a <dict>. Each <dict> in this array is an OTA package.

				// Look in the <dict> properties to see if the item is intended for the device we're looking for.
				NSObject[] supportedDevices = ((NSArray)entry.objectForKey("SupportedDevices")).getArray();
				for (NSObject supportedDevice:supportedDevices) {
					if (supportedDevice.toString().equals(device)) { // We got one!

						String fileSize, fileURL;

						// Make sure we don't get the dummy file for some entries.
						if (entry.containsKey("RealUpdateAttributes")) {
							NSDictionary realUpdateAttrs = (NSDictionary)entry.get("RealUpdateAttributes");

							fileSize = realUpdateAttrs.get("RealUpdateDownloadSize").toString();
							fileURL = realUpdateAttrs.get("RealUpdateURL").toString();
						}
						else {
							fileSize = entry.get("_DownloadSize").toString();
							fileURL = ((NSString)entry.get("__BaseURL")).getContent() + ((NSString)entry.get("__RelativePath")).getContent();
						}

						// Give the file size some commas.
						fileSize = NumberFormat.getNumberInstance(Locale.US).format(Integer.parseInt(fileSize));

						// Time to spit out what we found.
						if (mwMarkup) {
							System.out.println("|-");

							// Output iOS version and build. 
							System.out.print("| " + entry.get("OSVersion"));
							if (entry.containsKey("ReleaseType") && entry.get("ReleaseType").toString().equals("Beta")) // Is this a beta?
								System.out.print("b#"); // Number sign is supposed to be replaced by user. We can't keep track of whether this is beta 2 or beta 89.
							System.out.println();
							System.out.println("| " + entry.get("Build"));

							// Report prerequisite. If one isn't found, it's "universal."
							if (!entry.containsKey("PrerequisiteBuild"))
								System.out.println("| colspan=\"2\" {{n/a}}");
							else {
								System.out.println("| " + entry.get("PrerequisiteOSVersion"));
								System.out.println("| " + entry.get("PrerequisiteBuild"));
							}

							// Prints out fileURL, reuses fileURL to store just the file name, and then prints fileURL again.
							System.out.print("| [" + fileURL + " ");
							fileURL = ((NSString)entry.get("__RelativePath")).getContent().replace("com_apple_MobileAsset_SoftwareUpdate/", "");
							System.out.println(fileURL + "]");

							System.out.println("| " + fileSize);
						}
						else {
							// Output iOS version and build.
							System.out.println("iOS " + entry.get("OSVersion") + " (Build " + entry.get("Build") + ")");
							if (entry.containsKey("ReleaseType") && entry.get("ReleaseType").toString().equals("Beta")) // Is this a beta?
								System.out.println("This is marked as a beta release.");

							// Report prerequisite. If one isn't found, it's "universal."
							if (!entry.containsKey("PrerequisiteBuild"))
								System.out.println("Requires: Not specified");
							else
								System.out.println("Requires: iOS " + entry.get("PrerequisiteOSVersion") + " (Build " + entry.get("PrerequisiteBuild") + ")");

							System.out.println("URL: " + fileURL);
							System.out.println("File size: " + fileSize);

							System.out.println();
						}
					}
				}
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
}
