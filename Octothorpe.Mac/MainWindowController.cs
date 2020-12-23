/*
 * Copyright (c) 2020 Dialexio
 * 
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
using AppKit;
using Claunia.PropertyList;
using Foundation;
using Octothorpe.Lib;
using System;
using System.Collections.Generic;
using System.IO;

using NSDictionary = Claunia.PropertyList.NSDictionary;
using NSObject = Claunia.PropertyList.NSObject;

namespace Octothorpe.Mac
{
    public partial class MainWindowController : NSWindowController
    {
        private bool DisplayWikiMarkup = true;
        private string DisplayMode { get; set; } = "Mesu Mode";
        private NSAlert alert;
        private NSDictionary deviceInfo = (NSDictionary)PropertyListParser.Parse(AppContext.BaseDirectory + "DeviceInfo.plist");
        private Parser parser = new Parser();

        public MainWindowController(IntPtr handle) : base(handle)
        {
        }

        [Export("initWithCoder:")]
        public MainWindowController(NSCoder coder) : base(coder)
        {
        }

        public MainWindowController() : base("MainWindow")
        {
        }

        public override void AwakeFromNib()
        {
            base.AwakeFromNib();

            // Populate the Class dropdown box
            foreach (KeyValuePair<string, NSObject> deviceClass in deviceInfo)
                ClassSelection.AddItem(deviceClass.Key);

            ClassSelection.SelectItem(0);
            ClassChanged(ClassSelection);
        }

        public new MainWindow Window
        {
            get { return (MainWindow)base.Window; }
        }

        partial void BrowseForFile(NSButton sender)
        {
            try
            {
                NSOpenPanel FilePrompt = NSOpenPanel.OpenPanel;
                FilePrompt.AllowedFileTypes = new string[] { "xml", "plist" };
                FilePrompt.AllowsMultipleSelection = false;
                FilePrompt.CanChooseFiles = true;
                FilePrompt.CanChooseDirectories = false;

                if (FilePrompt.RunModal() == 1)
                    MesuFileLoc.StringValue = FilePrompt.Url.Path;

                else
                    throw new ArgumentException("nofile");

                parser.LoadPlist(MesuFileLoc.StringValue);
                NSButtonParse.Enabled = true;
            }

            catch (ArgumentException message)
            {
                switch (message.Message)
                {
                    case "nofile":
                        alert = new NSAlert()
                        {
                            MessageText = "File Not Specified",
                            InformativeText = "You must select a PLIST file (.plist or .xml) to load."
                        };
                        break;

                    default:
                        alert = new NSAlert()
                        {
                            MessageText = "Argument Error",
                            InformativeText = "There is an unknown error with the arguments provided."
                        };
                        break;
                }

                alert.RunModal();
                NSButtonParse.Enabled = false;
            }

            catch (DirectoryNotFoundException)
            {
                alert = new NSAlert()
                {
                    MessageText = "Directory Not Found",
                    InformativeText = "Please double-check that you entered the path correctly!"
                };

                alert.RunModal();
                NSButtonParse.Enabled = false;
            }

            catch (FileNotFoundException)
            {
                alert = new NSAlert()
                {
                    MessageText = "Directory Not Found",
                    InformativeText = "Please double-check that you entered the file name correctly!"
                };

                alert.RunModal();
                NSButtonParse.Enabled = false;
            }
        }

        partial void ChangeOutputFormat(NSButton sender)
        {
            DisplayWikiMarkup = (sender.Title == "The iPhone Wiki markup");
            TableHeaders.Enabled = (sender.Title == "The iPhone Wiki markup");
            TableHeaders.Hidden = (sender.Title != "The iPhone Wiki markup");

            if (TableHeaders.Enabled == false)
                TableHeaders.State = NSCellStateValue.Off;
        }

        partial void ClassChanged(NSPopUpButton sender)
        {
            NSDictionary deviceClass = (NSDictionary)deviceInfo[ClassSelection.SelectedItem.Title];

            // Make sure the dropdown box for devices is empty
            DeviceSelection.RemoveAllItems();

            // And populate the dropdown box for devices
            foreach (KeyValuePair<string, NSObject> device in deviceClass)
                DeviceSelection.AddItem(device.Key);

            DeviceSelection.SelectItem(0);
            DeviceChanged(DeviceSelection);
        }

        partial void DeviceChanged(NSPopUpButton sender)
        {
            string SelectedDevice = DeviceSelection.SelectedItem.Title;

            // Empty out the dropdown box for models
            ModelSelection.RemoveAllItems();

            foreach (NSDictionary deviceClass in deviceInfo.Values)
            {
                if (deviceClass.ContainsKey(SelectedDevice))
                {
                    // Repopulate the dropdown box for models
                    foreach (KeyValuePair<string, NSObject> model in (NSDictionary)((NSDictionary)deviceClass[SelectedDevice])["Models"])
                        ModelSelection.AddItem(model.Key);

                    ModelSelection.SelectItem(0);
                    ModelChanged(ModelSelection);
                }
            }
        }

        partial void ModelChanged(NSPopUpButton sender)
        {
            bool loopBreak = false;
            string ModelSelected = (string)ModelSelection.SelectedItem.Title;

            foreach (NSDictionary deviceClassDict in deviceInfo.Values)
            {
                foreach (NSDictionary deviceDict in deviceClassDict.Values)
                {
                    foreach (KeyValuePair<string, NSObject> modelDeviceString in (NSDictionary)deviceDict["Models"])
                    {
                        if (ModelSelected == modelDeviceString.Key)
                        {
                            parser.Device = modelDeviceString.Value.ToString();
                            parser.Model = ModelSelected;
                            loopBreak = true;
                            break;
                        }

                        else
                            parser.Device = "iProd999,99";
                    }

                    if (loopBreak)
                        break;
                }

                if (loopBreak)
                    break;
            }
        }

        partial void ParsingSTART(NSButton sender)
        {
            try
            {
                alert = null;

                parser.Model = ModelSelection.SelectedItem.Title;
                parser.ShowBeta = (ShowBeta.State == NSCellStateValue.On);
                parser.WikiMarkup = DisplayWikiMarkup;

                parser.FullTable = (TableHeaders.State == NSCellStateValue.On);

                if (DisplayMode == "Pallas Mode")
                {
                    parser.PallasCurrentBuild = PallasCurrentBuild.StringValue;
                    parser.PallasCurrentVersion = (uint.TryParse(PallasCurrentVersion.StringValue, out var curverstring)) ?
                        $"{curverstring}.0" :
                        PallasCurrentVersion.StringValue;

                    parser.PallasRequestedVersion = (uint.TryParse(PallasRequestedVersion.StringValue, out var reqverstring)) ?
                        $"{reqverstring}.0" :
                        PallasCurrentVersion.StringValue;

                    parser.PallasSupervised = (PallasSupervised.State == NSCellStateValue.On);
                }

                else
                {
                    parser.RemoveStubs = (MesuRemoveStubs.State == NSCellStateValue.On);

                    try
                    {
                        // Set maximum version if one was specified
                        if (string.IsNullOrEmpty(MaxVersion.StringValue) == false)
                        {
                            // Doing it like this converts an integer, e.g. "11" into "11.0"
                            parser.Maximum = (uint.TryParse(MaxVersion.StringValue, out var verstring)) ?
                                new Version($"{verstring}.0") :
                                new Version(MaxVersion.StringValue);
                        }

                        // Set minimum version if one was specified
                        if (string.IsNullOrEmpty(MesuMinVersion.StringValue) == false)
                        {
                            // Doing it like this converts an integer, e.g. "11" into "11.0"
                            parser.Minimum = (uint.TryParse(MesuMinVersion.StringValue, out var verstring)) ?
                                new Version($"{verstring}.0") :
                                new Version(MaxVersion.StringValue);
                        }
                    }

                    catch (ArgumentException)
                    {
                        throw new ArgumentException("badversion");
                    }
                }

                NSTextViewOutput.Value = parser.ParseAssets(DisplayMode == "Pallas Mode");
            }

            catch (ArgumentException message)
            {
                switch (message.Message)
                {
                    case "badbuild":
                        alert = new NSAlert()
                        {
                            MessageText = "Build Not Specified",
                            InformativeText = "A build number was not specified."
                        };
                        break;

                    case "badversion":
                        alert = new NSAlert()
                        {
                            MessageText = "Not A Version Number",
                            InformativeText = "You need to specify a version number for the minimum and maximum fields, or leave the field blank."
                        };
                        break;

                    case "device":
                        alert = new NSAlert()
                        {
                            MessageText = "Device Not Specified",
                            InformativeText = "You did not specify a device to search for (e.g. iPhone8,1)."
                        };
                        break;

                    case "model":
                        alert = new NSAlert()
                        {
                            MessageText = "Model Not Specified",
                            InformativeText = "This device requires you to specify a model number. For example, N71AP is a model number for the iPhone 6S."
                        };
                        break;

                    case "needspallas":
                        alert = new NSAlert()
                        {
                            MessageText = "Incorrect Source Selected",
                            InformativeText = "This device's software updates are found via Pallas, not Mesu."
                        };
                        break;

                    case "nofile":
                    case "The path is not of a legal form.":
                        alert = new NSAlert()
                        {
                            MessageText = "No Source",
                            InformativeText = "You must select a PLIST file (.plist or .xml) to load."
                        };
                        break;

                    default:
                        alert = new NSAlert()
                        {
                            MessageText = "Argument Error",
                            InformativeText = "You appear to be missing a required field."
                        };
                        break;
                }

                alert.RunModal();
            }

            catch (FileNotFoundException)
            {
                alert = new NSAlert()
                {
                    MessageText = "File Not Found",
                    InformativeText = "The program was unable to load the specified file."
                };

                alert.RunModal();
            }

            // No file selected.
            catch (NullReferenceException)
            {
                alert = new NSAlert()
                {
                    MessageText = "No Catalog Selected",
                    InformativeText = "You need to select an OTA catalog to search through."
                };

                alert.RunModal();
            }

            catch (Exception objection)
            {
                alert = new NSAlert()
                {
                    MessageText = objection.Message,
                    InformativeText = objection.StackTrace
                };
                alert.RunModal();
            }
        }

        partial void PlistChanged(NSPopUpButton sender)
        {
            try
            {
                switch (sender.SelectedItem.Title)
                {
                    case "Custom URL...":
                    case "Custom URL…":
                    case "Custom URL":
                        NSBoxFile.Hidden = true;
                        NSBoxLoc.Hidden = false;
                        MesuURL.StringValue = "https://mesu.apple.com/assets/com_apple_MobileAsset_SoftwareUpdate/com_apple_MobileAsset_SoftwareUpdate.xml";
                        MesuURL.Enabled = true;
                        parser.LoadPlist(MesuURL.StringValue);
                        break;

                    case "audioOS (Public)":
                        NSBoxFile.Hidden = true;
                        NSBoxLoc.Hidden = false;
                        MesuURL.StringValue = "https://mesu.apple.com/assets/audio/com_apple_MobileAsset_SoftwareUpdate/com_apple_MobileAsset_SoftwareUpdate.xml";
                        MesuURL.Enabled = false;
                        parser.LoadPlist(MesuURL.StringValue);
                        break;

                    case "iOS (Public)":
                        NSBoxFile.Hidden = true;
                        NSBoxLoc.Hidden = false;
                        MesuURL.StringValue = "https://mesu.apple.com/assets/com_apple_MobileAsset_SoftwareUpdate/com_apple_MobileAsset_SoftwareUpdate.xml";
                        MesuURL.Enabled = false;
                        parser.LoadPlist(MesuURL.StringValue);
                        break;

                    case "tvOS (Public)":
                        NSBoxFile.Hidden = true;
                        NSBoxLoc.Hidden = false;
                        MesuURL.StringValue = "https://mesu.apple.com/assets/tv/com_apple_MobileAsset_SoftwareUpdate/com_apple_MobileAsset_SoftwareUpdate.xml";
                        MesuURL.Enabled = true;
                        parser.LoadPlist(MesuURL.StringValue);
                        break;

                    case "watchOS (Public)":
                        NSBoxFile.Hidden = true;
                        NSBoxLoc.Hidden = false;
                        MesuURL.StringValue = "https://mesu.apple.com/assets/watch/com_apple_MobileAsset_SoftwareUpdate/com_apple_MobileAsset_SoftwareUpdate.xml";
                        MesuURL.Enabled = false;
                        parser.LoadPlist(MesuURL.StringValue);
                        break;

                    default:
                        NSBoxFile.Hidden = false;
                        NSBoxLoc.Hidden = true;
                        break;
                }

                NSButtonParse.Enabled = true;
            }

            catch (ArgumentException message)
            {
                switch (message.Message)
                {
                    case "nofile":
                        alert = new NSAlert()
                        {
                            MessageText = "File Not Specified",
                            InformativeText = "You must select a PLIST file (.plist or .xml) to load."
                        };
                        break;

                    default:
                        alert = new NSAlert()
                        {
                            MessageText = "Argument Error",
                            InformativeText = "There is an unknown error with the arguments provided."
                        };
                        break;
                }

                alert.RunModal();
                NSButtonParse.Enabled = false;
            }
        }

        partial void PlistPathEdited(NSTextField sender)
        {
            try
            {
                parser.LoadPlist(MesuURL.StringValue);
                NSButtonParse.Enabled = true;
            }
            
            catch (ArgumentException message)
            {
                switch (message.Message)
                {
                    case "nofile":
                        alert = new NSAlert()
                        {
                            MessageText = "File Not Specified",
                            InformativeText = "You must select a PLIST file (.plist or .xml) to load."
                        };
                        break;

                    case "notmesu":
                        alert = new NSAlert()
                        {
                            MessageText = "Incorrect URL",
                            InformativeText = "The URL supplied should belong to mesu.apple.com."
                        };
                        break;

                    default:
                        alert = new NSAlert()
                        {
                            MessageText = "Argument Error",
                            InformativeText = "There is an unknown error with the arguments provided."
                        };
                        break;
                }

                alert.RunModal();
                NSButtonParse.Enabled = false;
            }
        }

        public void ParserModeChanged(string mode)
        {
            DisplayMode = mode;

            MesuOptional.Hidden = (DisplayMode == "Pallas Mode");
            MesuView.Hidden = (DisplayMode == "Pallas Mode");
            PallasOptional.Hidden = (DisplayMode != "Pallas Mode");
            PallasView.Hidden = (DisplayMode != "Pallas Mode");

            NSTextViewOutput.Value = $"Parser is now in {DisplayMode}.";
        }
    }
}
