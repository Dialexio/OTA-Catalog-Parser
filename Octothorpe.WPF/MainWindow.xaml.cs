/*
 * Copyright (c) 2019 Dialexio
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
using Claunia.PropertyList;
using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Data;
using Octothorpe.Lib;

namespace Octothorpe
{
    /// <summary>
    /// Interaction logic for MainWindow.xaml
    /// </summary>
    public partial class MainWindow : Window
    {
        private Microsoft.Win32.OpenFileDialog FilePrompt;
        private NSDictionary deviceInfo = (NSDictionary)PropertyListParser.Parse(AppContext.BaseDirectory + Path.DirectorySeparatorChar + "DeviceInfo.plist");
        private Parser parser = new Parser();


        public MainWindow()
        {
            InitializeComponent();

            foreach (KeyValuePair<string, NSObject> deviceClass in deviceInfo)
            {
                ComboBoxDevice.Items.Add(new ComboBoxItem() { Content = deviceClass.Key, IsEnabled = false });

                foreach (KeyValuePair<string, NSObject> device in (NSDictionary)deviceInfo.Get(deviceClass.Key))
                    ComboBoxDevice.Items.Add(new ComboBoxItem() { Content = device.Key });
            }
        }

        private void BrowseForFile(object sender, RoutedEventArgs e)
        {
            try
            {
                FilePrompt = new Microsoft.Win32.OpenFileDialog();
                FilePrompt.Filter = "Apple XML Property List (.xml)|*.xml|Apple Property List (.plist)|*.plist";
                FilePrompt.FilterIndex = 0;
                FilePrompt.ShowDialog();

                if (FilePrompt.FileName == null)
                    throw new ArgumentException("nofile");

                TextBoxFile.Text = FilePrompt.FileName;

                parser.LoadPlist(TextBoxFile.Text);
                ButtonParse.IsEnabled = true;
            }

            catch (ArgumentException message)
            {
                switch (message.Message)
                {
                    case "nofile":
                    case "The path is not of a legal form.":
                        MessageBox.Show("You must select a PLIST file (.plist or .xml) to load.");
                        break;

                    default:
                        MessageBox.Show("There is an unknown error with the arguments provided.");
                        break;
                }

                ButtonParse.IsEnabled = false;
            }

            catch (DirectoryNotFoundException)
            {
                MessageBox.Show("Please double-check that you entered the path correctly!");
                ButtonParse.IsEnabled = false;
            }

            catch (FileNotFoundException)
            {
                MessageBox.Show("Please double-check that you entered the file name correctly!");
                ButtonParse.IsEnabled = false;
            }
        }

        private void DeviceChanged(object sender, SelectionChangedEventArgs e)
        {
            string selecteditem = ((ComboBoxItem)((ComboBox)sender).SelectedItem).Content.ToString();

            // Empty out the dropdown box for models
            while (ComboBoxModel.Items.IsEmpty == false)
                ComboBoxModel.Items.RemoveAt(0);

            foreach (KeyValuePair<string, NSObject> deviceClass in deviceInfo)
            {
                if (((NSDictionary)deviceClass.Value).ContainsKey(selecteditem))
                {
                    parser.Device = ((NSDictionary)((NSDictionary)deviceClass.Value)[selecteditem])["Device"].ToString();

                    // If we have an A9 device with multiple models, we need to show the models
                    switch (parser.Device)
                    {
                        case "iPad6,11":
                        case "iPad6,12":
                        case "iPhone8,1":
                        case "iPhone8,2":
                        case "iPhone8,4":
                            GridModel.Visibility = Visibility.Visible;

                            foreach (NSObject model in ((NSArray)((NSDictionary)((NSDictionary)deviceClass.Value)[selecteditem])["Models"]))
                                ComboBoxModel.Items.Add(new ComboBoxItem() { Content = model.ToString() });
                            break;

                        default:
                            GridModel.Visibility = Visibility.Collapsed;
                            break;
                    }

                    break;
                }

                else
                    parser.Device = "iProd999,99"; // This should never be a final value.
            }
        }

        private void ParsingSTART(object sender, RoutedEventArgs e)
        {
            try
            {
                parser.Model = (string)ComboBoxModel.SelectedValue;
                parser.WikiMarkup = (RadioWiki.IsChecked == true);

                parser.FullTable = CheckBoxFullTable.IsChecked.Value;
                parser.RemoveStubs = CheckBoxRemoveStubs.IsChecked.Value;
                parser.ShowBeta = CheckBoxBeta.IsChecked.Value;

                try
                {
                    // Set maximum version if one was specified
                    if (String.IsNullOrEmpty(TextBoxMax.Text) == false)
                    {
                        // Doing it like this converts an integer, e.g. "11" into "11.0"
                        parser.Maximum = (uint.TryParse(TextBoxMax.Text, out var verstring)) ?
                            new Version(TextBoxMax.Text + ".0") :
                            new Version(TextBoxMax.Text);
                    }

                    // Set minimum version if one was specified
                    if (String.IsNullOrEmpty(TextBoxMin.Text) == false)
                    {
                        // Doing it like this converts an integer, e.g. "11" into "11.0"
                        parser.Minimum = (uint.TryParse(TextBoxMin.Text, out var verstring)) ?
                            new Version(TextBoxMin.Text + ".0") :
                            new Version(TextBoxMin.Text);
                    }
                }

                catch (ArgumentException)
                {
                    throw new ArgumentException("badvalue");
                }

                TextOutput.Text = parser.ParseAssets();
            }

            catch (ArgumentException message)
            {
                switch (message.Message)
                {
                    case "device":
                        MessageBox.Show("You did not specify a device to search for (e.g. iPhone8,1).");
                        break;

                    case "model":
                        MessageBox.Show("This device requires you to specify a model number. For example, N71AP is a model number for the iPhone 6S.");
                        break;

                    default:
                        MessageBox.Show("You appear to be missing a required field.");
                        break;
                }
            }

            catch (FileNotFoundException)
            {
                MessageBox.Show("The program was unable to load the specified file.");
            }

            catch (NullReferenceException)
            {
                MessageBox.Show("You need to select an OTA catalog to search through.");
            }

            catch (Exception objection)
            {
                MessageBox.Show(objection.Message);
            }
        }

        private void SourceChanged(object sender, SelectionChangedEventArgs e)
        {
            try
            {
                switch (((ComboBoxItem)((ComboBox)sender).SelectedItem).Content)
                {
                    case "Custom URL...":
                    case "Custom URL…":
                    case "Custom URL":
                        TextBoxLoc.Text = "https://mesu.apple.com/assets/com_apple_MobileAsset_SoftwareUpdate/com_apple_MobileAsset_SoftwareUpdate.xml";
                        parser.LoadPlist(TextBoxLoc.Text);
                        break;

                    case "audioOS (Public)":
                        TextBoxLoc.Text = "https://mesu.apple.com/assets/audio/com_apple_MobileAsset_SoftwareUpdate/com_apple_MobileAsset_SoftwareUpdate.xml";
                        parser.LoadPlist(TextBoxLoc.Text);
                        break;

                    case "iOS (Public)":
                        TextBoxLoc.Text = "https://mesu.apple.com/assets/com_apple_MobileAsset_SoftwareUpdate/com_apple_MobileAsset_SoftwareUpdate.xml";
                        parser.LoadPlist(TextBoxLoc.Text);
                        break;

                    case "tvOS (Public)":
                        TextBoxLoc.Text = "https://mesu.apple.com/assets/tv/com_apple_MobileAsset_SoftwareUpdate/com_apple_MobileAsset_SoftwareUpdate.xml";
                        parser.LoadPlist(TextBoxLoc.Text);
                        break;

                    case "watchOS (Public)":
                        TextBoxLoc.Text = "https://mesu.apple.com/assets/watch/com_apple_MobileAsset_SoftwareUpdate/com_apple_MobileAsset_SoftwareUpdate.xml";
                        parser.LoadPlist(TextBoxLoc.Text);
                        break;
                }
            }

            catch (ArgumentException message)
            {
                switch (message.Message)
                {
                    case "nofile":
                    case "The path is not of a legal form.":
                        MessageBox.Show("You must select a PLIST file (.plist or .xml) to load.");
                        break;

                    default:
                        MessageBox.Show("There is an unknown error with the arguments provided.");
                        break;
                }
            }
        }

        private void SourceEdited(object sender, RoutedEventArgs e)
        {
            try
            {
                parser.LoadPlist(((TextBox)sender).Text);
                ButtonParse.IsEnabled = true;
            }

            catch (ArgumentException message)
            {
                switch (message.Message)
                {
                    case "nofile":
                    case "The path is not of a legal form.":
                        MessageBox.Show("You must select a PLIST file (.plist or .xml) to load.");
                        break;

                    case "notmesu":
                        MessageBox.Show("The URL supplied should belong to mesu.apple.com.");
                        break;

                    default:
                        MessageBox.Show("There is an unknown error with the arguments provided.");
                        break;
                }

                ButtonParse.IsEnabled = false;
            }
        }
    }

    public class InvertVisibilityConverter : IValueConverter
    {
        public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
        {
            if (targetType == typeof(Visibility))
            {
                return (Visibility)value == Visibility.Collapsed ?
                    Visibility.Visible :
                    Visibility.Collapsed;
            }

            throw new InvalidOperationException("Converter can only convert to value of type Visibility.");
        }

        public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
        {
            throw new Exception("Invalid call - one way only");
        }
    }
}
