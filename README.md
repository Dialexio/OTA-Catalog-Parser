# OTA Catalog Parser
A somewhat crude Java program to view an OTA update catalog for [iOS](http://mesu.apple.com/assets/com_apple_MobileAsset_SoftwareUpdate/com_apple_MobileAsset_SoftwareUpdate.xml), [tvOS](http://mesu.apple.com/assets/tv/com_apple_MobileAsset_SoftwareUpdate/com_apple_MobileAsset_SoftwareUpdate.xml), and [watchOS](http://mesu.apple.com/assets/watch/com_apple_MobileAsset_SoftwareUpdate/com_apple_MobileAsset_SoftwareUpdate.xml). It can probably be optimized to be more efficient (aside from using another language, anyways :P), but it does what it was meant to.

## Running the Program
This program requires [Java 8](http://www.java.com/en/download/) or newer to be installed.

This is a command line program. Open the Command Prompt or Terminal, and run it like any other JAR file:

`java -jar path/to/Parser.jar -d DEVICE -f path/to/com_apple_MobileAsset_SoftwareUpdate.xml`

Arguments can be added after the JAR name. The program will output its findings into standard output.

## Arguments
* `-b` specifies that you would like to see beta releases. By default, this program will not display beta releases. _This argument is optional._
* `-d <device>` specifies what device you're looking for. This argument is looking for a value like "iPad2,3" or "iPod7,1." (If you do not know what value to use, you may refer to the identifiers listed on [The iPhone Wiki](https://www.theiphonewiki.com/wiki/Models).) __This argument is required.__
* `-f <file>` specifies the location of the OTA update catalog. You must save the file to your computer before running the program. __This argument is required.__
* `-m <model>` specifies what device you're looking for. This argument is looking for a value like "N71AP" or "N66mAP." (If you do not know what value to use, you may refer to the internal names listed on [The iPhone Wiki](https://www.theiphonewiki.com/wiki/Models).) __This argument is required only if you are looking for OTA updates for the iPhone 6S or 6S Plus. It is ignored for all other devices.__
* `-max <OS version>` _(as of version 0.3)_ specifies the _maximum_ version of iOS you're looking for. This argument is looking for a value like "4.3" or "8.0.1." _This argument is optional._
* `-min <OS version>` _(renamed from_ `-o` _as of version 0.3)_ specifies the _minimum_ version of iOS you're looking for. This argument is looking for a value like "4.3" or "8.0.1." _This argument is optional._
* `-w` allows you to see the results formatted better for [The iPhone Wiki](https://www.theiphonewiki.com/wiki/OTA_Updates). Manual editing may still be required (e.g. for colspan/rowspan), but this will make the burden more bearable. _This argument is optional._

## License Information
This program is distributed under the [MIT License](http://opensource.org/licenses/MIT).

This program makes use of [com.java.dd-plist](https://github.com/3breadt/dd-plist), which is also distributed under the MIT License by its author.
