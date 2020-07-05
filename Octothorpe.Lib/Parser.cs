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
using Claunia.PropertyList;
using JWT;
using Newtonsoft.Json.Linq;
using RestSharp;
using System;
using System.Collections.Generic;
using System.IO;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading.Tasks;

namespace Octothorpe.Lib
{
    public class Parser
    {
        private bool fullTable, removeStubs, showBeta, wikiMarkup, AddStubBlurb, DeviceIsWatch, ModelNeedsChecking;
        private Dictionary<string, List<string>> FileRowspan = new Dictionary<string, List<string>>();
        private Dictionary<string, uint> BuildNumberRowspan = new Dictionary<string, uint>(),
            DateRowspan = new Dictionary<string, uint>(),
            MarketingVersionRowspan = new Dictionary<string, uint>();
        private Dictionary<string, Dictionary<string, uint>> PrereqBuildRowspan = new Dictionary<string, Dictionary<string, uint>>(), // DeclaredBuild, <PrereqBuild, count>
            PrereqOSRowspan = new Dictionary<string, Dictionary<string, uint>>(); // DeclaredBuild, <PrereqOS, count>
        private object[] Assets;
        private readonly List<OTAPackage> Packages = new List<OTAPackage>();
        private string model = null, pallasBuild = null;
        private Version max, minimum;

        public string Device { get; set; }

        public bool RemoveStubs
        {
            set { removeStubs = value; }
        }

        public bool FullTable
        {
            set { fullTable = value; }
        }

        public Version Maximum
        {
            set { max = value; }
        }

        public Version Minimum
        {
            set { minimum = value; }
        }

        public string Model
        {
            get
            {
                return model;
            }

            set
            {
                model = value;
                ModelNeedsChecking = Regex.IsMatch(Device, "(iPad6,(11|12)|iPhone8,(1|2|4))");
            }
        }

        public string PallasBuild
        {
            set { pallasBuild = value; }
        }

        public bool ShowBeta
        {
            set { showBeta = value; }
        }

        public bool WikiMarkup
        {
            set { wikiMarkup = value; }
        }

        public void LoadPlist(string AssetFile)
        {
            NSDictionary root;

            if (Regex.IsMatch(AssetFile, @"://mesu.apple.com/assets/"))
            {
                RestClient Fido = new RestClient();
                var request = new RestRequest(AssetFile);
                IRestResponse response = Fido.Execute(request);
                MemoryStream ResponseAsStream = new MemoryStream(Encoding.UTF8.GetBytes(response.Content));
                root = (NSDictionary)PropertyListParser.Parse(ResponseAsStream);
            }

            else if (AssetFile.Contains("://"))
                throw new ArgumentException("notmesu");

            else
                root = (NSDictionary)PropertyListParser.Parse(AssetFile);

            Assets = (object[])(root.Get("Assets").ToObject());
        }

        public string ParseAssets(bool Pallas)
        {
            Cleanup();

            if (Pallas)
                GetPallasEntries();

            else
            {
                ErrorCheck();
                AddPlistEntries();
            }


            if (wikiMarkup)
            {
                CountRowspan();

                return OutputWikiMarkup();
            }

            else
                return OutputHumanFormat();
        }

        private void AddPlistEntries()
        {
            List<Task> AssetTasks = new List<Task>();

            // Look at every item in the NSArray named "Assets."
            foreach (object entry in Assets)
            {
                AssetTasks.Add(Task.Factory.StartNew( () => {
                    bool matched = false;
                    OTAPackage package = new OTAPackage((Dictionary<string, object>)entry); // Feed the info into a custom object so we can easily pull info and sort.

                    // Beta check.
                    if (showBeta == false && package.ActualReleaseType > 0)
                        return;

                    // Device check.
                    matched = package.SupportedDevices.Contains(Device);

                    // Model check, if needed.
                    if (matched && ModelNeedsChecking)
                    {
                        matched = false; // Skipping unless we can verify we want it.

                        // Make sure "SupportedDeviceModels" exists before checking it.
                        if (package.SupportedDeviceModels.Count > 0)
                            matched = (package.SupportedDeviceModels.Contains(Model));
                    }

                    // Stub check.
                    // But first, determine if we need to add the stub blurb.
                    if (Regex.IsMatch(Device, "iPad[4-6]|iPhone[6-8]|iPod7,1") && package.AllowableOTA == false && package.ActualBuild == "99Z999")
                        AddStubBlurb = true;

                    if (removeStubs && package.AllowableOTA == false)
                        return;

                    // If it's still a match, check the OS version.
                    // If the OS version doesn't fit what we're
                    // searching for, continue to the next entry.
                    if (matched)
                    {
                        if (max != null && max.CompareTo(new Version(package.MarketingVersion)) < 0)
                            return;
                        if (minimum != null && minimum.CompareTo(new Version(package.MarketingVersion)) > 0)
                            return;

                        // It survived the checks!
                        Packages.Add(package);
                    }
                }));
            }
            Task.WaitAll(AssetTasks.ToArray());

            Packages.Sort();
        }

        private void Cleanup()
        {
            AddStubBlurb = false;
            BuildNumberRowspan.Clear();
            DateRowspan.Clear();
            FileRowspan.Clear();
            MarketingVersionRowspan.Clear();
            Packages.Clear();
            PrereqBuildRowspan.Clear();
            PrereqOSRowspan.Clear();
        }

        private void CountRowspan()
        {
            foreach (OTAPackage entry in Packages)
            {            
                // Increment the count if the build exists.
                if (BuildNumberRowspan.ContainsKey(entry.DeclaredBuild))
                    BuildNumberRowspan[entry.DeclaredBuild]++;
                // If not, add the first tally.
                else
                    BuildNumberRowspan.Add(entry.DeclaredBuild, 1);


                // Count OTAPackage.ActualBuild and not OTAPackage.Date because x.0 GM and x.1 beta can technically be pushed at the same time.
                // Increment the count if it exists.
                if (DateRowspan.ContainsKey(entry.ActualBuild))
                    DateRowspan[entry.ActualBuild]++;
                // If not, add the first tally.
                else
                    DateRowspan.Add(entry.ActualBuild, 1);


                // Kill rowspan for iPod5,1 10B141 (public releases used the universal entry).
                if ((entry.SupportedDevices.Contains("iPod5,1") && entry.OSVersion == "8.4.1" && entry.PrerequisiteBuild == "10B141") == false)
                {
                    // Increment the count if file URL exists (this can be the case for universal entries).
                    if (FileRowspan.ContainsKey(entry.URL))
                        FileRowspan[entry.URL].Add(entry.PrerequisiteBuild);

                    else
                        FileRowspan.Add(entry.URL, new List<string>(new string[] { entry.PrerequisiteBuild }));
                }


                // Increment the count if marketing version already exists.
                // (This can happen for silent build updates, e.g. 10.1.1.)
                if (MarketingVersionRowspan.ContainsKey(entry.MarketingVersion))
                    MarketingVersionRowspan[entry.MarketingVersion]++;
                // If not, add the first tally.
                else
                    MarketingVersionRowspan.Add(entry.MarketingVersion, 1);


                // Increment the count if Prerequisite OS version exists.
                try
                {
                    PrereqOSRowspan[entry.DeclaredBuild][entry.PrerequisiteVer()]++;
                }
                // If not, add the first tally.
                catch (KeyNotFoundException)
                {
                    if (PrereqOSRowspan.ContainsKey(entry.DeclaredBuild) == false)
                        PrereqOSRowspan.Add(entry.DeclaredBuild, new Dictionary<string, uint>());

                    PrereqOSRowspan[entry.DeclaredBuild].Add(entry.PrerequisiteVer(), 1);
                }


                // Prerequisite Build version
                // Increment the count if it exists.
                // If not, add the first tally.
                try
                {
                    PrereqBuildRowspan[entry.DeclaredBuild][entry.PrerequisiteBuild]++;
                }

                catch (KeyNotFoundException)
                {
                    if (PrereqBuildRowspan.ContainsKey(entry.DeclaredBuild) == false)
                        PrereqBuildRowspan.Add(entry.DeclaredBuild, new Dictionary<string, uint>());

                    PrereqBuildRowspan[entry.DeclaredBuild].Add(entry.PrerequisiteBuild, 1);
                }
            }
        }

        private void ErrorCheck()
        {
            // Device check.
            if (Device == null || Regex.IsMatch(Device, @"(AppleTV|AudioAccessory|iPad|iPhone|iPod|Watch)(\d)?\d,\d") == false)
                throw new ArgumentException("device");

            DeviceIsWatch = Regex.IsMatch(Device, @"Watch\d,\d");

            // Model check.
            if (ModelNeedsChecking)
            {
                if (Model == null)
                    throw new ArgumentException("model");

                else if (Regex.IsMatch(Model, @"[BDJKMNP]\d((\d)?){2}[A-Za-z]?AP") == false)
                    throw new ArgumentException("model");
            }

            if (Assets == null)
                throw new ArgumentException("nofile");
        }

        private void GetPallasEntries()
        {
            Dictionary<string, object> DecryptedPayload;
            IRestResponse response;
            JwtDecoder ResponseDecoder = new JwtDecoder(new JWT.Serializers.JsonNetSerializer(), new JwtBase64UrlEncoder());
            List<string> Builds = new List<string>();
            OTAPackage package;
            RestClient Fido = new RestClient();
            RestRequest request = new RestRequest("https://gdmf.apple.com/v2/assets");
            string AssetAudience = null, PostingDate;

            switch (Device.Substring(0, 3))
            {
                // audioOS
                case "Aud":
                    AssetAudience = "0322d49d-d558-4ddf-bdff-c0443d0e6fac";
                    break;

                // tvOS
                case "App":
                    // I don't think Apple TV 2nd and 3rd gen. use Pallas? Can't hurt to be cautious I guess.
                    AssetAudience = (Device == "AppleTV2,1" || Device.Substring(0, 9) == "AppleTV3,") ? "01c1d682-6e8f-4908-b724-5501fe3f5e5c" : "356d9da0-eee4-4c6c-bbe5-99b60eadddf0";
                    break;

                // iOS / iPadOS
                case "iPa":
                case "iPh":
                case "iPo":
                    AssetAudience = "01c1d682-6e8f-4908-b724-5501fe3f5e5c";
                    break;

                // watchOS
                case "Wat":
                    AssetAudience = "b82fcf9c-c284-41c9-8eb2-e69bf5a5269f";
                    break;
            }

            // Put together the request.
            request.AddJsonBody(new
            {
                AssetAudience = AssetAudience,
                AssetType = "com.apple.MobileAsset.SoftwareUpdate",
                BuildVersion = pallasBuild,
                ClientVersion = 2,
                HWModelStr = Model,
                ProductType = Device
            });
            request.AddHeader("Accept", "application/json");
            request.Method = Method.POST;

            // Get Apple's response, then decode it.
            response = Fido.Execute(request);
            DecryptedPayload = ResponseDecoder.DecodeToObject<Dictionary<string, object>>(response.Content);

            // Grab the release date.
            PostingDate = ((string)DecryptedPayload["PostingDate"]).Replace("-", string.Empty);

            if (((Dictionary<string, object>)DecryptedPayload).TryGetValue("Assets", out object AssetsArray))
            {
                foreach (JContainer container in (JArray)AssetsArray)
                {
                    package = new OTAPackage(container, PostingDate);

                    Packages.Add(package);
                }
            }
        }

        private string OutputHumanFormat()
        {
            StringBuilder Output = new StringBuilder();
            string osName;

            // So we don't add on to a previous run.
            Output.Length = 0;
        
            foreach (OTAPackage package in Packages)
            {
                switch (Device.Substring(0, 4))
                {
                    case "Appl":
                        osName = (Regex.Match(Device, @"AppleTV(2,1|3,1|3,2)").Success) ? "Apple TV software" : "tvOS";
                        break;

                    case "Audi":
                        osName = "audioOS";
                        break;

                    case "Watc":
                        osName = "watchOS";
                        break;

                    default:
                        osName = "iOS";
                        break;
                }

                // Output OS version and build.
                Output.Append($"{osName} {package.MarketingVersion}");

                // Give it a beta label (if it is one).
                if (package.ActualReleaseType > 0)
                {
                    switch (package.ActualReleaseType)
                    {
                        case 1:
                            Output.Append(" Public Beta");
                            break;
                        case 2:
                            Output.Append(" beta");
                            break;
                        case 3:
                            Output.Append(" Carrier Beta");
                            break;
                        case 4:
                            Output.Append(" Internal");
                            break;
                    }

                    // Don't print a 1 if this is the first beta.
                    if (package.BetaNumber > 1)
                        Output.Append($" {package.BetaNumber}");
                }

                Output.AppendLine($" (Build {package.ActualBuild})");
                Output.AppendLine($"Listed as: {package.OSVersion} (Build {package.DeclaredBuild})");
                // Stub OTA?
                Output.AppendLine($"Installation permitted: {((package.AllowableOTA) ? "Yes" : "No")}");

                // Auto-Update
                Output.AppendLine($"Auto-Update permitted: {((package.AllowableOTA) ? "Yes" : "No")}");

                Output.AppendLine($"Reported Release Type: {package.ReleaseType}");

                // Print prerequisites if there are any.
                if (package.PrerequisiteBuild == "N/A")
                    Output.AppendLine("Requires: Not specified");

                else
                    Output.AppendLine($"Requires: {package.PrerequisiteVer()} (Build {package.PrerequisiteBuild})");

                // Date as extracted from the URL.
                Output.AppendLine($"Timestamp: {package.Date('y')}/{package.Date('m')}/{package.Date('d')}");

                // Compatibility Version.
                Output.AppendLine($"Compatibility Version: {package.CompatibilityVersion}");

                // Print out the URL and file size.
                Output.AppendLine($"URL: {package.URL}");
                Output.AppendLine($"File size: {package.Size}{Environment.NewLine}");
            }

            Cleanup();

            return Output.ToString();
        }

        private string OutputWikiMarkup()
        {
            bool BorkedDelta, WatchPlus2;
            NSDictionary info = null;
            int ReduceRowspanBy = 0, RowspanOverride;
            Match name;
            NSDictionary deviceInfo = (NSDictionary)PropertyListParser.Parse(AppContext.BaseDirectory + Path.DirectorySeparatorChar + "DeviceInfo.plist");
            string deviceName = "", fileName, NewTableCell = "| ";
            // So we don't add on to a previous run.
            StringBuilder Output = new StringBuilder { Length = 0 };

            // Looking through the <dict>s for each device class.
            foreach (NSDictionary deviceClass in deviceInfo.Values)
            {
                // Loading it as a KeyValuePair instead of an NSDictionary so we can grab the key name.
                foreach (KeyValuePair<string, NSObject> deviceEntry in deviceClass)
                {
                    if (((NSDictionary)((NSDictionary)deviceEntry.Value)["Models"]).ContainsKey(Model))
                    {
                        deviceName = deviceEntry.Key;
                        info = (NSDictionary)deviceEntry.Value;
                        break;
                    }
                }
            }

            if (fullTable)
            {
                // Header
                for (int i = 0; i < (int)info["HeaderLevel"].ToObject(); i++)
                    Output.Append('=');

                if ((int)info["HeaderLevel"].ToObject() == 3)
                    Output.Append($" [[{Model}]] ");

                else
                    Output.Append($" [[{Model}|{deviceName}]] ");

                for (int i = 0; i < (int)info["HeaderLevel"].ToObject(); i++)
                    Output.Append('=');

                Output.AppendLine();

                // Message about dummy update
                if (AddStubBlurb)
                    Output.AppendLine("Users still running older versions of iOS (up to 9.3.5) are now presented with a [http://appldnld.apple.com/ios9/031-21276-20150906-9C5374F6-0D6F-4CEC-A322-668F61700CC9/com_apple_MobileAsset_OTARescueAsset/f393ae5156319e127a2b21d2f85b66a151c44ff5.zip dummy update file], and are instructed to use [[iTunes]] to install software updates.\n");

                // Table
                Output.AppendLine("{| class=\"wikitable\" style=\"font-size: smaller; text-align: center;\"");
                Output.AppendLine("|-");
                Output.AppendLine("! Version");
                Output.AppendLine("! Build");
                Output.AppendLine("! Prerequisite Version");
                Output.AppendLine("! Prerequisite Build");

                if (DeviceIsWatch)
                    Output.AppendLine("! Compatibility Version");

                Output.AppendLine("! Release Date");
                Output.AppendLine("! Release Type");
                Output.AppendLine("! OTA Download URL");
                Output.AppendLine("! File Size");
            }
        
            foreach (OTAPackage package in Packages)
            {
                BorkedDelta = (package.SupportedDevices.Contains("iPod5,1") && package.PrerequisiteBuild == "10B141");
                WatchPlus2 = false;

                // Some firmwares use one firmware file in multiple spots (that are separated by other files).
                // (e.g. FILE_A, FILE_A, FILE_B, FILE_C, FILE_A, FILE_D, FILE_A, FILE_E)
                ReduceRowspanBy = 0;

                switch (package.PrerequisiteBuild)
                {
                    case "N/A":
                        // Final releases only— don't hit betas.
                        if (package.BetaNumber == 0)
                        {
                            switch (package.OSVersion)
                            {
                                case "9.2":
                                    if (Device == "iPhone4,1" || Device == "iPhone5,1" || Device == "iPhone5,2")
                                        ReduceRowspanBy = 4;
                                    break;

                                case "9.2.1":
                                    ReduceRowspanBy = 2;
                                    break;
                            }
                        }
                        break;

                    case "13A340":
                        if (package.OSVersion == "9.2")
                            ReduceRowspanBy = 2;
                        break;

                    case "13A344":
                        if (package.OSVersion == "9.2.1")
                            ReduceRowspanBy = 1;
                        break;

                    // For iOS 11.2 and newer, iOS 10.2 (and iOS 10.3 for iPad 5th generation)
                    // needs its rowspan reduced because iOS 10.3.3 uses the same delta, but
                    // iOS 10.3.3 beta 6 (and 10.3.2, for iPad 5th generation) separate it.
                    case "14C92":
                    case "14E277":
                        if (Version.Parse(package.OSVersion).CompareTo(Version.Parse("11.2")) >= 0)
                            ReduceRowspanBy = 1;
                        break;
                }

                // Obtain the file name.
                fileName = string.Empty;
                name = Regex.Match(package.URL, @"[0-9a-f]{40}\.zip");

                if (name.Success)
                    fileName = name.ToString();

                // Hacky workaround to handle 9.0 rowspan for watchOS.
                if (DeviceIsWatch && MarketingVersionRowspan.ContainsKey("9.0"))
                {
                    MarketingVersionRowspan.Remove("9.0");
                    WatchPlus2 = true;
                }

                // Let us begin!
                Output.AppendLine("|-");

                if (MarketingVersionRowspan.TryGetValue(package.MarketingVersion, out uint MarketVerRowspanCount))
                {
                    // Spit out a rowspan attribute.
                    if (MarketVerRowspanCount > 1)
                    {
                        // 32-bit Apple TV receives a filler for Marketing Version.
                        // (OTAPackage.MarketingVersion for 32-bit Apple TVs returns the OS version because the Marketing Version isn't specified in the XML... Confusing, I know.)
                        if (Regex.Match(Device, "AppleTV(2,1|3,1|3,2)").Success)
                            Output.AppendLine($"| rowspan=\"{MarketVerRowspanCount}\" | [MARKETING VERSION]");

                        Output.Append("| rowspan=\"");

                        if (WatchPlus2)
                            Output.Append(MarketVerRowspanCount + 2);

                        else
                            Output.Append(MarketVerRowspanCount);
                        
                        Output.Append("\" ");
                    }

                    // 32-bit Apple TV receives a filler for Marketing Version.
                    // (OTAPackage.MarketingVersion for 32-bit Apple TVs returns the OS version because the Marketing Version isn't specified in the XML... Confusing, I know.)
                    else if (Regex.Match(Device, "AppleTV(2,1|3,1|3,2)").Success)
                        Output.AppendLine("| [MARKETING VERSION]");

                    // Don't let entries for watchOS 1.0(.1) spit out an OS version of 9.0.
                    // This was necessitated by the watchOS 4 GM.
                    if (package.PrerequisiteBuild != "12S507" && package.PrerequisiteBuild != "12S632")
                        Output.Append($"| {package.MarketingVersion}");

                    // Give it a beta label (if it is one).
                    if (package.BetaNumber > 0)
                    {
                        switch (package.ActualReleaseType)
                        {
                            case 1:
                                Output.Append(" Public Beta");
                                break;
                            case 2:
                            case 3:
                                Output.Append(" beta");
                                break;
                            case 4:
                                Output.Append(" Internal");
                                break;
                        }

                        // Don't print a 1 if this is the first beta.
                        if (package.BetaNumber > 1)
                            Output.Append($" {package.BetaNumber}");
                    }

                    // Add the suffix (if appropriate).
                    if (string.IsNullOrEmpty(package.Suffix) == false)
                        Output.Append($" {package.Suffix}");

                    Output.AppendLine();

                    // Output the purported version for watchOS 1.0.x.
                    if (package.MarketingVersion.Contains("1.0") && package.OSVersion.Contains("8.2"))
                        Output.AppendLine($"| rowspan=\"{MarketVerRowspanCount}\" | {package.OSVersion}");

                    // Remove the count since we're done with it.
                    MarketingVersionRowspan.Remove(package.MarketingVersion);
                }

                // Output build number.
                if (BuildNumberRowspan.TryGetValue(package.DeclaredBuild, out uint BuildRowspanCount))
                {
                    Output.Append(NewTableCell);

                    // Only give rowspan if there is more than one row with the OS version.
                    // Count DeclaredBuild() instead of ActualBuild() so the entry pointing betas to the final build is treated separately.
                    if (BuildRowspanCount > 1)
                        Output.Append($"rowspan=\"{BuildRowspanCount}\" | ");

                    //Remove the count since we're done with it.
                    BuildNumberRowspan.Remove(package.DeclaredBuild);

                    Output.Append(package.ActualBuild);

                    // Do we have a false build number? If so, add a footnote reference.
                    if (package.IsHonestBuild == false)
                        Output.Append("<ref name=\"inflated\" />");

                    Output.AppendLine();
                }

                // Printing prerequisite version
                if (PrereqOSRowspan.ContainsKey(package.DeclaredBuild) && PrereqOSRowspan[package.DeclaredBuild].ContainsKey(package.PrerequisiteVer()))
                {
                    Output.Append(NewTableCell);

                    // Is there more than one of this prerequisite version tallied?
                    if (PrereqOSRowspan[package.DeclaredBuild][package.PrerequisiteVer()] > 1)
                    {
                        Output.Append($"rowspan=\"{PrereqOSRowspan[package.DeclaredBuild][package.PrerequisiteVer()]}\" ");

                        PrereqOSRowspan[package.DeclaredBuild].Remove(package.PrerequisiteVer());

                        if (package.PrerequisiteBuild != "N/A")
                            Output.Append(NewTableCell);
                    }

                    // Print out the cell text
                    if (package.PrerequisiteBuild == "N/A")
                        Output.AppendLine("colspan=\"2\" {{n/a}}");

                    else
                    {
                        // If this is a GM, print the link to Golden Master.
                        if (package.PrerequisiteVer().Contains(" GM"))
                            Output.AppendLine(package.PrerequisiteVer().Replace("GM", "[[Golden Master|GM]]"));

                        // Very quick check if prerequisite is a beta. This is not bulletproof.
                        else if (Regex.Match(package.PrerequisiteBuild, OTAPackage.REGEX_BETA).Success && package.PrerequisiteVer().Contains("beta") == false)
                            Output.AppendLine($"{package.PrerequisiteVer()} beta #");

                        else
                            Output.AppendLine(package.PrerequisiteVer());
                    }
                }

                // Printing prerequisite build
                if (package.PrerequisiteBuild != "N/A" &&
                    PrereqBuildRowspan.ContainsKey(package.DeclaredBuild) &&
                    PrereqBuildRowspan[package.DeclaredBuild].TryGetValue(package.PrerequisiteBuild, out uint PrereqRowspanCount))
                {
                    Output.Append(NewTableCell);

                    // Is there more than one of this prerequisite build tallied?
                    // Also do not use rowspan if the prerequisite build is a beta.
                    if (PrereqRowspanCount > 1)
                    {
                        Output.Append($"rowspan=\"{PrereqRowspanCount}\" | ");

                        PrereqBuildRowspan[package.DeclaredBuild].Remove(package.PrerequisiteBuild);
                    }

                    Output.AppendLine(package.PrerequisiteBuild);
                }

                if (package.CompatibilityVersion > 0)
                    Output.AppendLine($"| {package.CompatibilityVersion}");

                // Date as extracted from the URL. Using the same rowspan count as build.
                // (Apple occasionally releases updates with the same version, but different build number, silently.)
                if (DateRowspan.TryGetValue(package.ActualBuild, out uint DateRowspanCount))
                {
                    Output.Append(NewTableCell);

                    // Only give rowspan if there is more than one row with the OS version.
                    if (DateRowspanCount > 1)
                    {
                        Output.Append($"rowspan=\"{DateRowspanCount}\" | ");

                        DateRowspan.Remove(package.ActualBuild); //Remove the count since we already used it.
                    }

                    Output.Append("{{");
                    Output.Append($"date|{package.Date('y')}|{package.Date('m')}|{package.Date('d')}");
                    Output.AppendLine("}}");
                }

                // Release Type.
                Output.Append("| ");

                switch (package.ReleaseType)
                {
                    case "Public":
                        Output.AppendLine("{{n/a}}");
                        break;

                    default:
                        Output.AppendLine(package.ReleaseType);
                        break;
                }

                // Is there more than one of this prerequisite version tallied?
                if ((FileRowspan.ContainsKey(package.URL) && FileRowspan[package.URL].Contains(package.PrerequisiteBuild)) || (BorkedDelta && package.OSVersion != "8.4.1"))
                {
                    RowspanOverride = FileRowspan[package.URL].Count - ReduceRowspanBy;

                    Output.Append(NewTableCell);

                    if (BorkedDelta == false && RowspanOverride > 1)
                        Output.Append($"rowspan=\"{RowspanOverride}\" | ");

                    Output.AppendLine($"[{package.URL} {fileName}]");
                    Output.Append(NewTableCell);

                    // Print file size.
                    // Only give rowspan if there is more than one row with the OS version.
                    if (BorkedDelta == false && RowspanOverride > 1)
                        Output.Append($"rowspan=\"{RowspanOverride}\" | ");

                    Output.AppendLine(package.Size);

                    // If we still need to list this file, we'll just take the build off of the List.
                    // Otherwise, we can chuck this.
                    if (RowspanOverride > 0)
                    {
                        while (FileRowspan[package.URL].Count > ReduceRowspanBy)
                            FileRowspan[package.URL].RemoveAt(0);
                    }

                    else
                        FileRowspan.Remove(package.URL);
                }
            }

            if (fullTable)
                Output.Append("|}");

            Cleanup();

            return Output.ToString();
        }
    }
}
