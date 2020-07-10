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
        private bool DisplayWikiMarkup = true, Pallas = false;
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

            // Populate the Device dropdown box
            foreach (KeyValuePair<string, NSObject> deviceClass in deviceInfo)
            {
                // Prevent drawing a separator at the top
                if (DeviceSelection.ItemCount > 0)
                    DeviceSelection.Menu.AddItem(NSMenuItem.SeparatorItem);

                // Group headers
                DeviceSelection.AddItem(deviceClass.Key);
                DeviceSelection.LastItem.Action = null;
                DeviceSelection.LastItem.Enabled = false;

                foreach (KeyValuePair<string, NSObject> device in (NSDictionary)deviceInfo.Get(deviceClass.Key))
                    DeviceSelection.AddItem(device.Key);
            }
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
                    NSTextFieldFile.StringValue = FilePrompt.Url.Path;

                else
                    throw new ArgumentException("nofile");

                parser.LoadPlist(NSTextFieldFile.StringValue);
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
                    DeviceModelUpdate(ModelSelection);
                }
            }
        }

        partial void DeviceModelUpdate(NSPopUpButton sender)
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

                if (Pallas)
                {
                    parser.PallasBuild = PallasBuild.StringValue;
                }

                else
                {
                    parser.RemoveStubs = (NSButtonRemoveStubs.State == NSCellStateValue.On);

                    try
                    {
                        // Set maximum version if one was specified
                        if (string.IsNullOrEmpty(NSTextFieldMax.StringValue) == false)
                        {
                            // Doing it like this converts an integer, e.g. "11" into "11.0"
                            parser.Maximum = (uint.TryParse(NSTextFieldMax.StringValue, out var verstring)) ?
                                new Version(NSTextFieldMax.StringValue + ".0") :
                                new Version(NSTextFieldMax.StringValue);
                        }

                        // Set minimum version if one was specified
                        if (string.IsNullOrEmpty(NSTextFieldMin.StringValue) == false)
                        {
                            // Doing it like this converts an integer, e.g. "11" into "11.0"
                            parser.Minimum = (uint.TryParse(NSTextFieldMin.StringValue, out var verstring)) ?
                                new Version(NSTextFieldMin.StringValue + ".0") :
                                new Version(NSTextFieldMin.StringValue);
                        }
                    }

                    catch (ArgumentException)
                    {
                        throw new ArgumentException("badversion");
                    }
                }

                NSTextViewOutput.Value = parser.ParseAssets(Pallas);
            }

            catch (ArgumentException message)
            {
                switch (message.Message)
                {
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
                        NSTextFieldLoc.StringValue = "https://mesu.apple.com/assets/com_apple_MobileAsset_SoftwareUpdate/com_apple_MobileAsset_SoftwareUpdate.xml";
                        NSTextFieldLoc.Enabled = true;
                        parser.LoadPlist(NSTextFieldLoc.StringValue);
                        break;

                    case "audioOS (Public)":
                        NSBoxFile.Hidden = true;
                        NSBoxLoc.Hidden = false;
                        NSTextFieldLoc.StringValue = "https://mesu.apple.com/assets/audio/com_apple_MobileAsset_SoftwareUpdate/com_apple_MobileAsset_SoftwareUpdate.xml";
                        NSTextFieldLoc.Enabled = false;
                        parser.LoadPlist(NSTextFieldLoc.StringValue);
                        break;

                    case "iOS (Public)":
                        NSBoxFile.Hidden = true;
                        NSBoxLoc.Hidden = false;
                        NSTextFieldLoc.StringValue = "https://mesu.apple.com/assets/com_apple_MobileAsset_SoftwareUpdate/com_apple_MobileAsset_SoftwareUpdate.xml";
                        NSTextFieldLoc.Enabled = false;
                        parser.LoadPlist(NSTextFieldLoc.StringValue);
                        break;

                    case "tvOS (Public)":
                        NSBoxFile.Hidden = true;
                        NSBoxLoc.Hidden = false;
                        NSTextFieldLoc.StringValue = "https://mesu.apple.com/assets/tv/com_apple_MobileAsset_SoftwareUpdate/com_apple_MobileAsset_SoftwareUpdate.xml";
                        NSTextFieldLoc.Enabled = true;
                        parser.LoadPlist(NSTextFieldLoc.StringValue);
                        break;

                    case "watchOS (Public)":
                        NSBoxFile.Hidden = true;
                        NSBoxLoc.Hidden = false;
                        NSTextFieldLoc.StringValue = "https://mesu.apple.com/assets/watch/com_apple_MobileAsset_SoftwareUpdate/com_apple_MobileAsset_SoftwareUpdate.xml";
                        NSTextFieldLoc.Enabled = false;
                        parser.LoadPlist(NSTextFieldLoc.StringValue);
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
                parser.LoadPlist(NSTextFieldLoc.StringValue);
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

        partial void UpdateSourceChanged(NSButton sender)
        {
            Pallas = (sender.Title == "Pallas");
            PallasView.Hidden = !Pallas;
            PlistView.Hidden = Pallas;
        }
    }
}
