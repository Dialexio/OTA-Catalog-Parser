# OTA Catalog Parser
This program lets you view an OTA update catalog for [iOS](http://mesu.apple.com/assets/com_apple_MobileAsset_SoftwareUpdate/com_apple_MobileAsset_SoftwareUpdate.xml), [tvOS](http://mesu.apple.com/assets/tv/com_apple_MobileAsset_SoftwareUpdate/com_apple_MobileAsset_SoftwareUpdate.xml), and [watchOS](http://mesu.apple.com/assets/watch/com_apple_MobileAsset_SoftwareUpdate/com_apple_MobileAsset_SoftwareUpdate.xml) in a more pleasant format. It can also output the information in a format suitable for entry on [The iPhone Wiki](https://www.theiphonewiki.com/wiki/OTA_Updates).

NOTE: The date is extracted from the file's URL, which may not be the actual release date.

## Compilation
Apache Ant is used to compile this program. Just cd to the base directory and run
`ant`

This produces a large (over 8 MB) JAR file that you can run. If you prefer a smaller, less universal JAR, other targets are included in build.xml:
`ant linux`

`ant mac`

`ant windows`

Whichever method you choose, you may want to remove the compiled .class files. (This will not delete the JAR.)
`ant cleanup`

## Running the Program
You must have Java 7 or newer installed on your computer. The newest version of Java is available from [Java.com](http://www.java.com/download/).

You may run this JAR with the following command:
`java -jar path/to/Parser.jar`

Mac OS X users must open it via the .app (bypassing Gatekeeper if necessary), or add an additional argument:
`java -XstartOnFirstThread -jar path/to/Parser.jar`

If there are arguments, the program will operate in the command prompt/terminal. If there are no provided arguments, the program will display a GUI.

## Command-Line Arguments
### Required Arguments
* `-d <device>` specifies what device you're looking for. This argument is looking for a value like "iPad2,3" or "iPod7,1." (If you do not know what value to use, you may refer to the identifiers listed on [The iPhone Wiki](https://www.theiphonewiki.com/wiki/Models).)
* `-f <file>` specifies the location of the OTA update catalog. You must save the file to your computer before running the program.
* `-m <model>` specifies what device you're looking for. This argument is looking for a value like "N71AP" or "N66mAP." (If you do not know what value to use, you may refer to the internal names listed on [The iPhone Wiki](https://www.theiphonewiki.com/wiki/Models).) __This argument is required only if you are looking for OTA updates for the iPhone 6S or 6S Plus. It is ignored for all other devices.__

### Optional Arguments
* `-b` specifies that you would like to see beta releases. By default, this program will not display beta releases.
* `-max <OS version>` specifies the _maximum_ version of iOS you're looking for. This argument is looking for a value like "4.3" or "8.0.1."
* `-min <OS version>` _(renamed from_ `-o` _as of version 0.3)_ specifies the _minimum_ version of iOS you're looking for. This argument is looking for a value like "4.3" or "8.0.1."
* `-w` allows you to see the results formatted better for [The iPhone Wiki](https://www.theiphonewiki.com/wiki/OTA_Updates). Manual editing may still be required (e.g. for colspan/rowspan), but this will make the burden more bearable.

## License Information
This program is distributed under the [MIT License](http://opensource.org/licenses/MIT).

This program uses [com.java.dd-plist](https://github.com/3breadt/dd-plist). It is distributed under the [MIT License](http://opensource.org/licenses/MIT).

This program uses binaries from [SWT](http://www.eclipse.org/swt/). SWT is distributed under the [Eclipse Public License 1.0](http://www.eclipse.org/legal/epl-v10.html); source code may be acquired from the SWT website.

This program uses binaries from [SWTJar](http://mchr3k.github.io/swtjar/). SWTJar is distributed under the [Eclipse Public License 1.0](http://www.eclipse.org/legal/epl-v10.html); source code may be acquired from its [GitHub repo](https://github.com/mchr3k/swtjar).
