# OTA Parser
A somewhat crude Java program to view the [OTA update catalog for iOS](http://mesu.apple.com/assets/com_apple_MobileAsset_SoftwareUpdate/com_apple_MobileAsset_SoftwareUpdate.xml). It can probably be optimized to be more efficient (aside from using another language, anyways :P), but it does what it was meant to.

You must save the OTA update catalog to your computer. The program will output its findings into standard output.

## Arguments
* `-d <device>` specifies what device you're looking for. This argument is looking for a value like "iPad2,3" or "iPod7,1." (If you do not know what value to use, you may refer to the identifiers listed on [The iPhone Wiki](https://www.theiphonewiki.com/wiki/Models).) __This argument is required.__
* `-f <file>` specifies the file location. __This argument is required.__
* `-w` allows you to see the results formatted better for [The iPhone Wiki](https://www.theiphonewiki.com/wiki/OTA_Updates). Manual editing will still be required (e.g. sorting, colspan/rowspan), but this will make the burden more bearable. _This argument is optional._

## License Information
This program is distributed under the [MIT License](http://opensource.org/licenses/MIT).

This program makes use of [com.java.dd-plist](https://github.com/3breadt/dd-plist), which is also distributed under the MIT License by its author.
