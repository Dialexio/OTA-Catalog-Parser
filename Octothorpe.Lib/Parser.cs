/*
 * Copyright (c) 2021 Dialexio
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
        private bool fullTable, removeStubs, showBeta, pallasSupervised, wikiMarkup, AddStubBlurb, DeviceIsWatch;
        private Dictionary<string, List<string>> FileRowspan = new Dictionary<string, List<string>>();
        private Dictionary<string, uint> BuildNumberRowspan = new Dictionary<string, uint>(),
            DateRowspan = new Dictionary<string, uint>(),
            MarketingVersionRowspan = new Dictionary<string, uint>();
        private Dictionary<string, Dictionary<string, uint>> PrereqBuildRowspan = new Dictionary<string, Dictionary<string, uint>>(), // DeclaredBuild, <PrereqBuild, count>
            PrereqOSRowspan = new Dictionary<string, Dictionary<string, uint>>(); // DeclaredBuild, <PrereqOS, count>
        private object[] Assets;
        private readonly List<OTAPackage> Packages = new List<OTAPackage>();
        private string pallasCurrentBuild = null, pallasRequestedVersion;
        private Version max, minimum, pallasCurrentVersion;

        public string Device { get; set; }

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

        public string Model { get; set; }

        public string PallasCurrentBuild
        {
            set { pallasCurrentBuild = value; }
        }

        public string PallasCurrentVersion
        {
            set { pallasCurrentVersion = new Version(value); }
        }

        public string PallasRequestedVersion
        {
            set { pallasRequestedVersion = value; }
        }

        public bool PallasSupervised
        {
            set { pallasSupervised = value; }
        }

        public bool RemoveStubs
        {
            set { removeStubs = value; }
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
            ErrorCheck(Pallas);

            if (Pallas)
                GetPallasEntries();

            else
                GetMesuEntries();


            if (wikiMarkup)
            {
                CountRowspan();

                return OutputWikiMarkup();
            }

            else
                return OutputHumanFormat();
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

        private void ErrorCheck(bool Pallas)
        {
            // Device check.
            if (Device == null || Regex.IsMatch(Device, @"(ADP|AppleTV|AudioAccessory|iMac(Pro)?|iPad|iPhone|iPod|Mac(mini|Pro)?|MacBook(Air|Pro)?|Watch)(\d)?\d,\d") == false)
                throw new ArgumentException("device");

            if ((Device.Contains("ADP") || Device.Contains("Mac")) && Pallas == false)
                throw new ArgumentException("needspallas");

            DeviceIsWatch = Regex.IsMatch(Device, @"Watch\d,\d");

            // Model check.
            if (Regex.IsMatch(Device, @"(iPad6,1(1|2)|iPhone8,(1|2|4))"))
            {
                if (Model == null)
                    throw new ArgumentException("model");

                else if (Regex.IsMatch(Model, @"[BDJKMNP]\d((\d)?){2}[A-Za-z]?AP") == false)
                    throw new ArgumentException("model");
            }

            // Make sure we have a build number to search from via Pallas.
            if (Pallas)
            {
                if (Regex.IsMatch(pallasCurrentBuild, @"\d{2}[A-Z]\d{2}\d?\d?[a-z]?") == false)
                    throw new ArgumentException("badbuild");
            }

            // If we're not using Pallas, we need to check a Mesu-formatted plist
            else if (Assets == null)
                throw new ArgumentException("nofile");
        }

        private void GetMesuEntries()
        {
            List<Task> AssetTasks = new List<Task>();

            // Look at every item in the NSArray named "Assets."
            foreach (object entry in Assets)
            {
                AssetTasks.Add(Task.Factory.StartNew(() => {
                    bool matched = false;
                    OTAPackage package = new OTAPackage((Dictionary<string, object>)entry); // Feed the info into a custom object so we can easily pull info and sort.

                    // Beta check.
                    if (showBeta == false && package.ActualReleaseType > 0)
                        return;

                    // Device check.
                    matched = package.SupportedDevices.Contains(Device);

                    // Model check, if needed.
                    if (matched && package.SupportedDeviceModels.Count > 0)
                        matched = package.SupportedDeviceModels.Contains(Model);

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

        private void GetPallasEntries()
        {
            Dictionary<string, object> DecryptedPayload;
            int ArrayIndex = 0;
            IRestResponse response;
            JwtDecoder ResponseDecoder = new JwtDecoder(new JWT.Serializers.JsonNetSerializer(), new JwtBase64UrlEncoder());
            List<string> AssetAudiences = new List<string>(),
                PackagesPresent = new List<string>();
            NSDictionary BuildInfo = (NSDictionary)PropertyListParser.Parse($"{AppContext.BaseDirectory}BuildInfo.plist");
            OTAPackage package;
            RestClient Fido = new RestClient();
            RestRequest request = new RestRequest("https://gdmf.apple.com/v2/assets");
            string SUAssetType = "com.apple.MobileAsset.SoftwareUpdate";
            string[] SplicedBuildNum = new string[4];

            // Gather the asset audiences.
            switch (Device.Substring(0, 3))
            {
                // AirPods Pro
                case "iPr":
                    BuildInfo = (NSDictionary)BuildInfo["AirPods"];

                    if (showBeta)
                        AssetAudiences.Add("f859d8a7-5f87-4659-b6a0-5ee9fa439b8f");
                    break;

                // audioOS
                case "Aud":
                    BuildInfo = (NSDictionary)BuildInfo["audioOS"];
                    AssetAudiences.Add("0322d49d-d558-4ddf-bdff-c0443d0e6fac");

                    if (showBeta)
                        AssetAudiences.AddRange(new string[] {
                            "b05ddb59-b26d-4c89-9d09-5fda15e99207", // audioOS 14 beta
                            "58ff8d56-1d77-4473-ba88-ee1690475e40"  // audioOS 15 beta
                        });
                    break;

                // tvOS
                case "App":
                    BuildInfo = (NSDictionary)BuildInfo["tvOS"];

                    AssetAudiences.Add("356d9da0-eee4-4c6c-bbe5-99b60eadddf0");

                    if (showBeta)
                        AssetAudiences.AddRange(new string[] {
                            "5b220c65-fe50-460b-bac5-b6774b2ff475", // tvOS 12 beta
                            "975af5cb-019b-42db-9543-20327280f1b2", // tvOS 13 beta
                            "65254ac3-f331-4c19-8559-cbe22f5bc1a6", // tvOS 14 beta
                            "4d0dcdf7-12f2-4ebf-9672-ac4a4459a8bc"  // tvOS 15 beta
                        });
                    break;

                // iOS / iPadOS
                case "iPa":
                case "iPh":
                case "iPo":
                    BuildInfo = (NSDictionary)BuildInfo["iOS"];

                    AssetAudiences.Add("01c1d682-6e8f-4908-b724-5501fe3f5e5c");

                    if (showBeta)
                        AssetAudiences.AddRange(new string[] {
                            "b7580fda-59d3-43ae-9488-a81b825e3c73", // iOS 11 beta
                            "ef473147-b8e7-4004-988e-0ae20e2532ef", // iOS 12 beta
                            "d8ab8a45-ee39-4229-891e-9d3ca78a87ca", // iOS 13 beta
                            "dbbb0481-d521-4cdf-a2a4-5358affc224b", // iOS 14 beta
                            "ce48f60c-f590-4157-a96f-41179ca08278"  // iOS 15 beta
                        });
                    break;

                // macOS
                case "ADP":
                case "iMa":
                case "Mac":
                    BuildInfo = (NSDictionary)BuildInfo["macOS"];
                    SUAssetType = "com.apple.MobileAsset.MacSoftwareUpdate";

                    AssetAudiences.Add("60b55e25-a8ed-4f45-826c-c1495a4ccc65"); // macOS Public

                    if (showBeta)
                        AssetAudiences.AddRange(new string[] {
                            "ca60afc6-5954-46fd-8cb9-60dde6ac39fd", // macOS 11 beta
                            "298e518d-b45e-4d36-94be-34a63d6777ec"  // macOS 12 beta
                        });

                    // We also need to splice the build number. This looked like an ideal spot to put it without creating another if statement.
                    foreach (char BuildChar in pallasCurrentBuild)
                    {
                        if ((char.IsDigit(BuildChar) || char.IsLower(BuildChar)) == false)
                            ArrayIndex++;

                        SplicedBuildNum[ArrayIndex] = $"{SplicedBuildNum[ArrayIndex]}{BuildChar}";

                        if (char.IsDigit(BuildChar) == false)
                            ArrayIndex++;
                    }

                    ArrayIndex = 0;
                    break;

                // watchOS
                case "Wat":
                    BuildInfo = (NSDictionary)BuildInfo["watchOS"];

                    AssetAudiences.Add("b82fcf9c-c284-41c9-8eb2-e69bf5a5269f");

                    if (showBeta)
                        AssetAudiences.AddRange(new string[] {
                            "e841259b-ad2e-4046-b80f-ca96bc2e17f3", // watchOS 5 beta
                            "d08cfd47-4a4a-4825-91b5-3353dfff194f", // watchOS 6 beta
                            "ff6df985-3cbe-4d54-ba5f-50d02428d2a3", // watchOS 7 beta
                            "b407c130-d8af-42fc-ad7a-171efea5a3d0"  // watchOS 8 beta
                        });
                    break;
            }

            // Put together the request.
            request.AddHeader("Accept", "application/json");
            request.Method = Method.POST;

            foreach (KeyValuePair<string, NSObject> osVersion in BuildInfo)
            {
                // If the version is lower than our starting version, skip it.
                if (new Version(osVersion.Key).CompareTo(pallasCurrentVersion) < 0)
                    continue;

                // If the version we're querying for is higher than the maximum version, we're finished.
                if (max != null && new Version(osVersion.Key).CompareTo(max) > 0)
                    break;

                foreach (KeyValuePair<string, NSObject> build in (NSDictionary)osVersion.Value)
                {
                    foreach (string AssetAudience in AssetAudiences)
                    {
                        string PostingDate;

                        // If this isn't the first run-through, remove the previous JSON body.
                        while (request.Parameters.Count >= 2)
                            request.Parameters.RemoveAt(1);

                        // Add the JSON body. Macs have slightly different parameters.
                        if (SUAssetType == "com.apple.MobileAsset.MacSoftwareUpdate")
                            request.AddJsonBody(new
                            {
                                AllowSameBuildVersion = false,
                                AssetAudience = AssetAudience,
                                AssetType = SUAssetType,
                                BaseUrl = "https://mesu.apple.com/assets/macos/",
                                BuildVersion = build.Key,
                                ClientData = new
                                {
                                    AllowXmlFallback = false,
                                    DeviceAccessClient = "softwareupdated"
                                },
                                ClientVersion = 2,
                                DelayRequested = false,
                                DeviceName = "Mac",
                                DeviceOSData = new { },
                                HWModelStr = Model,
                                InternalBuild = false,
                                NoFallback = true,
                                ProductType = Device,
                                ProductVersion = osVersion.Key,
                                RequestedVersion = pallasRequestedVersion,
                                RestoreVersion = $"{SplicedBuildNum[0]}.{((int)SplicedBuildNum[1][0]) - 64}.{SplicedBuildNum[2]}.0.0,0",
                                Supervised = pallasSupervised
                            });

                        else
                            request.AddJsonBody(new
                            {
                                AllowSameBuildVersion = false,
                                AssetAudience = AssetAudience,
                                AssetType = SUAssetType,
                                BaseUrl = "https://mesu.apple.com/assets/",
                                BuildVersion = build.Key,
                                ClientData = new
                                {
                                    AllowXmlFallback = false,
                                    DeviceAccessClient = "softwareupdateservicesd"
                                },
                                ClientVersion = 2,
                                DelayRequested = false,
                                DeviceCheck = "Foreground",
                                DeviceClass = 1,
                                DeviceOSData = new
                                {
                                    BuildVersion = build.Key,
                                    HWModelStr = Model,
                                    ProductType = Device,
                                    ProductVersion = osVersion.Key
                                },
                                HWModelStr = Model,
                                InternalBuild = false,
                                IsUIBuild = true,
                                NoFallback = true,
                                ProductType = Device,
                                ProductVersion = osVersion.Key,
                                ScanRequestCount = 1,
                                SigningFuse = true,
                                Supervised = false
                           });

                        // Disable TLS verification. (Needed for Windows since it doesn't have Apple Root CA.)
                        Fido.RemoteCertificateValidationCallback = (sender, certificate, chain, sslPolicyErrors) => true;

                        // Get Apple's response, then decode it.
                        response = Fido.Execute(request);
                        DecryptedPayload = ResponseDecoder.DecodeToObject<Dictionary<string, object>>(response.Content);

                        // Grab the release date. If none is present, default to all zeroes.
                        PostingDate = (DecryptedPayload.ContainsKey("PostingDate")) ?
                            ((string)DecryptedPayload["PostingDate"]).Replace("-", string.Empty) :
                            "00000000";

                        if (((Dictionary<string, object>)DecryptedPayload).TryGetValue("Assets", out object AssetsArray))
                        {
                            foreach (JContainer container in (JArray)AssetsArray)
                            {
                                package = new OTAPackage(container, PostingDate);

                                // If the version is lower than the requested version, skip it.
                                if (new Version(package.OSVersion).CompareTo(pallasCurrentVersion) < 0)
                                    continue;

                                // If the version is higher than the maximum version, skip it.
                                if (max != null && new Version(package.OSVersion).CompareTo(max) > 0)
                                    continue;

                                // Prevent addition of duplicate entries.
                                if (PackagesPresent.Contains(package.SortingString) == false)
                                {
                                    Packages.Add(package);
                                    PackagesPresent.Add(package.SortingString);
                                }
                            }
                        }
                    }
                }
            }

            Packages.Sort();
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
                Output.AppendLine($"Auto-Update permitted: {((package.AutoUpdate) ? "Yes" : "No")}");

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

                        // If this is a release candidate, print a link.
                        else if (package.PrerequisiteVer().Contains(" RC"))
                            Output.AppendLine(package.PrerequisiteVer().Replace("RC", "[[Release Candidate|RC]]"));

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
