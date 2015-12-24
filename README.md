# OTA Catalog Parser
This program lets you view an OTA update catalog for [iOS](http://mesu.apple.com/assets/com_apple_MobileAsset_SoftwareUpdate/com_apple_MobileAsset_SoftwareUpdate.xml), [tvOS](http://mesu.apple.com/assets/tv/com_apple_MobileAsset_SoftwareUpdate/com_apple_MobileAsset_SoftwareUpdate.xml), and [watchOS](http://mesu.apple.com/assets/watch/com_apple_MobileAsset_SoftwareUpdate/com_apple_MobileAsset_SoftwareUpdate.xml) in a more pleasant format. It can also output the information in a format suitable for entry on [The iPhone Wiki](https://www.theiphonewiki.com/wiki/OTA_Updates).

NOTE: The date is extracted from the file's URL, which may not be the actual release date.

## Running the Program
You must have Java 7 or newer installed on your computer. The newest version of Java is available from [Java.com](http://www.java.com/download/).

This is a command line program. Open the Command Prompt or Terminal, and run it like any other JAR file, with whatever arguments you find necessary:

`java -jar path/to/Parser.jar -d DEVICE -f path/to/com_apple_MobileAsset_SoftwareUpdate.xml`

The program will output its findings into standard output.

## Arguments
### Required Arguments
* `-d <device>` specifies what device you're looking for. This argument is looking for a value like "iPad2,3" or "iPod7,1." (If you do not know what value to use, you may refer to the identifiers listed on [The iPhone Wiki](https://www.theiphonewiki.com/wiki/Models).)
* `-f <file>` specifies the location of the OTA update catalog. You must save the file to your computer before running the program.
* `-m <model>` specifies what device you're looking for. This argument is looking for a value like "N71AP" or "N66mAP." (If you do not know what value to use, you may refer to the internal names listed on [The iPhone Wiki](https://www.theiphonewiki.com/wiki/Models).) __This argument is required only if you are looking for OTA updates for the iPhone 6S or 6S Plus. It is ignored for all other devices.__

### Optional Arguments
* `-b` specifies that you would like to see beta releases. By default, this program will not display beta releases.
* `-max <OS version>` specifies the _maximum_ version of iOS you're looking for. This argument is looking for a value like "4.3" or "8.0.1."
* `-min <OS version>` _(renamed from_ `-o` _as of version 0.3)_ specifies the _minimum_ version of iOS you're looking for. This argument is looking for a value like "4.3" or "8.0.1."
* `-v` specifies whether or not the version number (and GitHub URL) will be added to the top of the program's output.
* `-w` allows you to see the results formatted better for [The iPhone Wiki](https://www.theiphonewiki.com/wiki/OTA_Updates). Manual editing may still be required (e.g. for colspan/rowspan), but this will make the burden more bearable.

## License Information
This program is distributed under the [MIT License](http://opensource.org/licenses/MIT).

This program makes use of [com.java.dd-plist](https://github.com/3breadt/dd-plist), which is also distributed under the MIT License by its author.
