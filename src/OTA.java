/*
 * OTA Catalog Parser 0.1.1
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

public class OTA {
	public static void main(String[] args) {
		boolean checkModel, mwMarkup = false;
		File file = null;
		int i = 0;
		NSDictionary root;
		String arg = "", device = "", fileName = "", model = "";

		System.out.println("OTA Catalog Parser v0.1.1\n");

		// Reading and (lazily) checking arguments.
		while (i < args.length && args[i].startsWith("-")) {
			arg = args[i++];

			// We need to know the device.
			if (arg.equals("-d")) {
				if (i < args.length)
					device = args[i++];
				else
					System.err.println("-d requires a device, e.g. iPad2,1 or iPhone6,2");
			}
			// We also need to know what file we're looking at.
			else if (arg.equals("-f")) {
				if (i < args.length)
					fileName = args[i++];
				else
					System.err.println("-f requires a filename");
			}
			else if (arg.equals("-m")) {
				if (i < args.length)
					model = args[i++];
				else
					System.err.println("-m requires a device, e.g. N71AP or N66mAP");
			}
			else if (arg.equals("-w")) {
				mwMarkup = true;
			}
		}

		// Flag whether or not we need to check the model.
		checkModel = (device.equals("iPhone8,1") || device.equals("iPhone8,2"));

		if (device.isEmpty()) {
			System.err.println("You need to set a device with the \"-d\" argument.");
			System.exit(1);
		}
		if (fileName.isEmpty()) {
			System.err.println("You need to set a file name with the \"-f\" argument.");
			System.exit(2);
		}
		if (checkModel && !model.matches("[JKMNP]\\d(\\d)?(\\d)?[a-z]?AP")) {
			System.err.println("You need to specify a model (e.g. N71AP) with the \"-m\" argument.");
			System.exit(3);
		}

		file = new File(fileName);

		try {
			root = (NSDictionary)PropertyListParser.parse(file); // The first <dict>.
			NSObject[] assets = ((NSArray)root.objectForKey("Assets")).getArray(); // Looking for the array with key "Assets."

			// Look at every item in the array with the key "Assets."
			for (NSObject item:assets) {
				boolean entryMatch = false;
				OTAPackage entry = new OTAPackage((NSDictionary)item); // Feed it into our own object. This will be used for sorting in the future.

				// Device check.
				for (NSObject supportedDevice:entry.supportedDevices()) {
					if (device.equals(supportedDevice.toString()))
						entryMatch = true;

					if (checkModel) {
						entryMatch = false;

						if (entry.supportedDeviceModels() != null) {
							for (NSObject supportedDeviceModel:entry.supportedDeviceModels())
								if (supportedDeviceModel.toString().equals(model)) {
									entryMatch = true;
									break;
								}
						}
					}
				}

				if (entryMatch) {
					if (mwMarkup) {
						System.out.println("|-");

						// Output iOS version and build. 
						System.out.print("| " + entry.version());
						if (entry.isBeta()) // Is this a beta?
							System.out.print(" beta #"); // Number sign is supposed to be replaced by user. We can't keep track of whether this is beta 2 or beta 89.
						System.out.println();
						System.out.println("| " + entry.build());

						// Print prerequisites if there are any.
						if (entry.isUniversal())
							System.out.println("| colspan=\"2\" {{n/a}}");
						else {
							System.out.println("| " + entry.prerequisiteVer());
							System.out.println("| " + entry.prerequisiteBuild());
						}

						// Date as extracted from the URL.
						System.out.println("| {{date|" + entry.date().substring(0, 4) + "|" + entry.date().substring(4, 6) + "|" + entry.date().substring(6, 8) + "}}");

						// Prints out fileURL, reuses fileURL to store just the file name, and then prints fileURL again.
						System.out.print("| [" + entry.url() + " ");
						System.out.println(entry.url().replace("com_apple_MobileAsset_SoftwareUpdate/", "") + "]");

						System.out.println("| " + entry.size());
					}
					else {
						// Output iOS version and build.
						System.out.println("iOS " + entry.version() + " (Build " + entry.build() + ")");
						if (entry.isBeta()) // Is this a beta?
							System.out.println("This is marked as a beta release.");

						// Print prerequisites if there are any.
						if (entry.isUniversal())
							System.out.println("Requires: Not specified");
						else
							System.out.println("Requires: iOS " + entry.prerequisiteVer() + " (Build " + entry.prerequisiteBuild() + ")");

						// Date as extracted from the URL.
						System.out.println("Timestamp: " + entry.date().substring(0, 4) + "/" + entry.date().substring(4, 6) + "/" + entry.date().substring(6, 8));

						// Print out the URL and file size.
						System.out.println("URL: " + entry.url());
						System.out.println("File size: " + entry.size());

						System.out.println();
					}
				}
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
}
