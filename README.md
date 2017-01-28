# OTA Catalog Parser
This program lets you view an OTA update catalog for [iOS](http://mesu.apple.com/assets/com_apple_MobileAsset_SoftwareUpdate/com_apple_MobileAsset_SoftwareUpdate.xml), [tvOS](http://mesu.apple.com/assets/tv/com_apple_MobileAsset_SoftwareUpdate/com_apple_MobileAsset_SoftwareUpdate.xml), and [watchOS](http://mesu.apple.com/assets/watch/com_apple_MobileAsset_SoftwareUpdate/com_apple_MobileAsset_SoftwareUpdate.xml) in a more pleasant format. It can also output the information in a format suitable for entry on [The iPhone Wiki](https://www.theiphonewiki.com/wiki/OTA_Updates).

__NOTE:__ Dates are extracted from the file URL, which may not be the actual release date.

## Running the Program
The Mac OS X version requires Mac OS X v10.7 (Lion) or newer. It is built with Xamarin.Mac, so no other software is required.

The Windows version requires .NET Framework 4.x ([link to .NET Framework 4.6.2](https://www.microsoft.com/en-us/download/details.aspx?id=53345)).

The command-line version requires either [Mono](http://www.mono-project.com/) or .NET Framework 4.x ([link to .NET Framework 4.6.2](https://www.microsoft.com/en-us/download/details.aspx?id=53345)) to be installed.

### Command-Line Arguments
#### Required Arguments
* `-d <device>` specifies what device you're looking for. This argument is looking for a value like "iPad2,3" or "iPod7,1." (If you do not know what value to use, you may refer to the identifiers listed on [The iPhone Wiki](https://www.theiphonewiki.com/wiki/Models).)
* `-f <file>` specifies the location of the OTA update catalog. This may be either an XML file saved on your computer, or a mesu.apple.com URL.
* `-m <model>` specifies what device you're looking for. This argument is looking for a value like "N71AP" or "N66mAP." (If you do not know what value to use, you may refer to the internal names listed on [The iPhone Wiki](https://www.theiphonewiki.com/wiki/Models).) __This argument is required only if you are looking for OTA updates for the iPhone 6S, 6S Plus, or iPhone SE. It is ignored for all other devices.__

#### Optional Arguments
* `-b` specifies that you would like to see beta releases. By default, this program does not display beta releases.
* `-max <OS version>` specifies the _highest_ version of iOS you're looking for. This argument is looking for a value like "4.3" or "8.0.1."
* `-min <OS version>` _(renamed from_ `-o` _as of version 0.3)_ specifies the _lowest_ version of iOS you're looking for. This argument is looking for a value like "4.3" or "8.0.1."
* `-w` allows you to see the results formatted more appropriately for [The iPhone Wiki](https://www.theiphonewiki.com/wiki/OTA_Updates). Manual editing may still be required (e.g. for "marketing version"), but this will make the burden more bearable.

## Third-Party Libraries
This program utilizes [Json.NET](http://www.newtonsoft.com/json) and [plist-cil](https://github.com/claunia/plist-cil), both of which are included.