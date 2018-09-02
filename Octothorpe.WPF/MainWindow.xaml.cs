/*
 * Copyright (c) 2018 Dialexio
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
using System;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using Octothorpe.Lib;

namespace Octothorpe
{
    /// <summary>
    /// Interaction logic for MainWindow.xaml
    /// </summary>
    public partial class MainWindow : Window
    {
        private Microsoft.Win32.OpenFileDialog FilePrompt;
        private Parser parser = new Parser();


        public MainWindow()
        {
            InitializeComponent();
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

        private void ParsingSTART(object sender, RoutedEventArgs e)
        {
            try
            {
                parser.Device = TextBoxDevice.Text;
                parser.Model = TextBoxModel.Text;
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
                        TextBoxLoc.IsEnabled = true;
                        GridLoc.Visibility = Visibility.Visible;
                        GridFile.Visibility = Visibility.Hidden;

                        parser.LoadPlist(TextBoxLoc.Text);
                        break;

                    case "audioOS (Public)":
                        TextBoxLoc.Text = "https://mesu.apple.com/assets/audio/com_apple_MobileAsset_SoftwareUpdate/com_apple_MobileAsset_SoftwareUpdate.xml";
                        TextBoxLoc.IsEnabled = false;
                        GridLoc.Visibility = Visibility.Visible;
                        GridFile.Visibility = Visibility.Hidden;

                        parser.LoadPlist(TextBoxLoc.Text);
                        break;

                    case "iOS (Public)":
                        TextBoxLoc.Text = "https://mesu.apple.com/assets/com_apple_MobileAsset_SoftwareUpdate/com_apple_MobileAsset_SoftwareUpdate.xml";
                        TextBoxLoc.IsEnabled = false;
                        GridLoc.Visibility = Visibility.Visible;
                        GridFile.Visibility = Visibility.Hidden;

                        parser.LoadPlist(TextBoxLoc.Text);
                        break;

                    case "tvOS (Public)":
                        TextBoxLoc.Text = "https://mesu.apple.com/assets/tv/com_apple_MobileAsset_SoftwareUpdate/com_apple_MobileAsset_SoftwareUpdate.xml";
                        TextBoxLoc.IsEnabled = false;
                        GridLoc.Visibility = Visibility.Visible;
                        GridFile.Visibility = Visibility.Hidden;

                        parser.LoadPlist(TextBoxLoc.Text);
                        break;

                    case "watchOS (Public)":
                        TextBoxLoc.Text = "https://mesu.apple.com/assets/watch/com_apple_MobileAsset_SoftwareUpdate/com_apple_MobileAsset_SoftwareUpdate.xml";
                        TextBoxLoc.IsEnabled = false;
                        GridLoc.Visibility = Visibility.Visible;
                        GridFile.Visibility = Visibility.Hidden;

                        parser.LoadPlist(TextBoxLoc.Text);
                        break;

                    default:
                        GridLoc.Visibility = Visibility.Hidden;
                        GridFile.Visibility = Visibility.Visible;
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

        private void ToggleModelField(object sender, TextChangedEventArgs e)
        {
            switch (((TextBox)sender).Text)
            {
                case "iPad6,11":
                case "iPad6,12":
                case "iPhone8,1":
                case "iPhone8,2":
                case "iPhone8,4":
                    GridModel.Visibility = Visibility.Visible;
                    break;

                default:
                    GridModel.Visibility = Visibility.Collapsed;
                    break;
            }
        }

        private void RadioWiki_Checked(object sender, RoutedEventArgs e)
        {
            CheckBoxFullTable.Visibility = Visibility.Visible;
        }

        private void RadioWiki_Unchecked(object sender, RoutedEventArgs e)
        {
            CheckBoxFullTable.Visibility = Visibility.Collapsed;
        }
    }
}
