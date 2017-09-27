/*
 * Copyright (c) 2017 Dialexio
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
using Newtonsoft.Json;
using System;
using System.Collections.Generic;
using System.IO;
using System.Text.RegularExpressions;

namespace Octothorpe
{
    class OTAPackage
    {
        private Match match;
        private readonly Dictionary<string, object> ENTRY;

        public OTAPackage(Dictionary<string, object> package)
        {
            ENTRY = package;
        }

        /// <summary>
        /// Returns the package's actual build number. (i.e. Without any of Apple's padding.)
        /// </summary>
        /// <returns>
        /// A string value of the package's actual build number. (e.g. 10A550 will be 10A550, 12F5061 will be 12F61)
        /// </returns>
        public string ActualBuild
        {
            get
            {
                // If it the build number looks like a beta...
                // And it's labeled as a beta...
                // But it's not a beta... We need the actual build number.
                if (Regex.Match(this.DeclaredBuild, REGEX_BETA).Success &&
                    this.ReleaseType != "Public" &&
                    this.ActualReleaseType == 0)
                {
                    int letterPos, numPos;

                    for (letterPos = 1; letterPos < this.DeclaredBuild.Length; letterPos++)
                    {
                        if (char.IsUpper(this.DeclaredBuild[letterPos]))
                        {
                            letterPos++;
                            break;
                        }
                    }

                    numPos = letterPos + 1;
                    if (this.DeclaredBuild[numPos] == '0')
                        numPos++;

                    return this.DeclaredBuild.Substring(0, letterPos) + this.DeclaredBuild.Substring(numPos);
                }

                else
                    return this.DeclaredBuild;
            }
        }

        /// <summary>
        /// Returns the package's actual release type in the form of an integer.
        /// </summary>
        /// <returns>
        /// An integer value of 0 (not a beta), 1 (public beta), 2 (developer beta), 3 (carrier beta), or 4 (internal build). -1 may be returned if the type is unknown.
        /// </returns>
        public int ActualReleaseType
        {
            get
            {
                // Just check ReleaseType and return values based on it.
                switch (this.ReleaseType)
                {
                    // We do need to dig deeper if it's "Beta" though.
                    case "Beta":
                        if (this.DocumentationID == "N/A" || this.DocumentationID == "PreRelease")
                            return 2;

                        else if (this.DocumentationID.Contains("Public"))
                            return 1;

                        else if (this.DocumentationID.Contains("Beta") || this.DocumentationID.Contains("Seed"))
                            return 2;

                        else
                            return 0;

                    case "Carrier":
                        return 3;

                    case "Internal":
                        return 4;

                    case "Public":
                        return 0;

                    default:
                        Console.WriteLine("Unknown ReleaseType: " + ENTRY["ReleaseType"]);
                        return -1;
                }
            }
        }

        /// <summary>
        /// Returns the beta number of this entry. If a beta number cannot be determined, returns 0.
        /// </summary>
        /// <returns>
        /// Whatever number beta this is, as an int.
        /// </returns>
        public int BetaNumber
        {
            get
            {
                char digit = this.DocumentationID[this.DocumentationID.Length - 1];

                if (this.IsHonestBuild && Regex.IsMatch(this.DocumentationID, "(Public|Beta|Seed)"))
                    return (char.IsDigit(digit)) ? (int)char.GetNumericValue(digit) : 1;

                else
                    return 0;
            }
        }

        /// <summary>
        /// Returns the value in the entry's CompatibilityVersion key. If the compatibility version cannot be determined, this returns 0.
        /// </summary>
        /// <returns>
        /// Whatever is in the key CompatibilityVersion, as an int.
        /// </returns>
        public int CompatibilityVersion
        {
            get
            {
                return (ENTRY.ContainsKey("CompatibilityVersion")) ?
                    (int)ENTRY["CompatibilityVersion"] :
                    0;
            }
        }

        /// <summary>
        /// Returns the timestamp found in the url. Note that this is not always accurate; it may be off by a day, or even a week.
        /// </summary>
        /// <returns>
        /// The timestamp found in the url, which may not be accurate. Its format is YYYYMMDD.
        /// </returns>
        public string Date()
        {
            match = Regex.Match(this.URL, @"\d{4}(\-|\.)20\d{8}\-");

            if (match.Success)
                return match.ToString().Substring(5, 9) + match.ToString().Substring(10, 12) + match.ToString().Substring(13, 15);

            else
            {
                match = Regex.Match(this.URL, @"\d{4}(\-|\.)20\d{4}(\d|\.)(\w|\-)");

                if (match.Success)
                {
                    switch (match.ToString().Substring(5))
                    {
                        case "201218.D22":
                            return "20120307";

                        case "2015106-DC":
                            return "20151006";

                        case "20160009/1":
                            return "20160913";

                        default:
                            return match.ToString().Substring(5);
                    }
                }

                else
                    return "00000000";
            }
        }

        /// <summary>
        /// Returns part of the timestamp found in the url.
        /// </summary>
        /// <returns>
        /// The day, month, or year found in the url.
        /// </returns>
        /// <param name="dmy">Indicates if you just want the day, month, or year.</param>
        public string Date(char dmy)
        {
            switch (dmy)
            {
                // Day
                case 'd':
                    return this.Date().Substring(6);

                // Month
                case 'm':
                    return this.Date().Substring(4, 2);

                // Year
                case 'y':
                    return this.Date().Substring(0, 4);

                default:
                    return this.Date();
            }
        }

        public string DeclaredBuild
        {
            get { return (string)ENTRY["Build"]; }
        }

        /// <summary>
        /// Reports the documentation ID that Apple assigned. This tells iOS which documentation file to load, since multiple ones may be offered.
        /// </summary>
        /// <returns>
        /// The documentation ID that corresponds to the OTA update. If one is not specified, returns "N/A."
        /// </returns>
        public string DocumentationID
        {
            get
            {
                return ENTRY.ContainsKey("SUDocumentationID") ?
                    (string)ENTRY["SUDocumentationID"] :
                    "N/A";
            }
        }

        /// <summary>
        /// Checks if the release has an inflated build number. Apple does this to push devices on beta builds to stable builds.
        /// </summary>
        /// <returns>
        /// A boolean value of whether this release has a false build number (true) or not (false).
        /// </returns>
        public bool IsHonestBuild
        {
            get { return this.ActualBuild == this.DeclaredBuild; }
        }

        /// <summary>
        /// A simple check if the release is a large, "one Size fits all" package.
        /// </summary>
        /// <returns>
        /// A boolean value of whether this release is used to cover all scenarios (true) or not (false).
        /// </returns>
        public bool IsUniversal
        {
            get { return this.PrerequisiteBuild == "N/A"; }
        }

        /// <summary>
        /// Returns the value of "MarketingVersion" if present. "MarketingVersion" is used in some entries to display a false version number.
        /// </summary>
        /// <returns>
        /// A string value of the "MarketingVersion" key (if it exists), otherwise returns the "OSVersion" key.
        /// </returns>
        public string MarketingVersion
        {
            get
            {
                if (ENTRY.ContainsKey("MarketingVersion"))
                {
                    string mv = (string)ENTRY["MarketingVersion"];

                    return (mv.Contains(".") == false) ?
                        mv + ".0" :
                        mv;
                }

                else
                    return this.OSVersion;
            }
        }

        /// <returns>
        /// The "OSVersion" key, as a string. For iOS 10 and newer, this will also strip "9.9." from the version.
        /// </returns>
        public string OSVersion
        {
            get
            {
                Dictionary<string, string> VersionStrings = null;

                try
                {
                    using (StreamReader Json = File.OpenText(AppDomain.CurrentDomain.BaseDirectory + "OS versions.json"))
                    {
                        VersionStrings = JsonConvert.DeserializeObject<Dictionary<string, string>>(Json.ReadToEnd());
                        return VersionStrings[this.ActualBuild];
                    }
                }

                catch (KeyNotFoundException)
                {
                    string version = (string)ENTRY["OSVersion"];
                    return (version.Substring(0, 3) == "9.9") ? version.Substring(4) : version;
                }
            }
        }

        /// <summary>
        /// "PrerequisiteBuild" states the specific build that the OTA package is intended for, since most OTA packages are deltas.
        /// </summary>
        /// <returns>
        /// The "PrerequisiteBuild" key, as a string.
        /// </returns>
        public string PrerequisiteBuild
        {
            get
            {
                return (ENTRY.ContainsKey("PrerequisiteBuild")) ?
                    (string)ENTRY["PrerequisiteBuild"] :
                    "N/A";
            }
        }

        /// <summary>
        /// "PrerequisiteVersion()" states the specific version that the OTA package is intended for, since most OTA packages are deltas.
        /// </summary>
        /// <returns>
        /// The "PrerequisiteVersion" key, as a string.
        /// </returns>
        
        public string PrerequisiteVer
        {
            get
            {
                Dictionary<string, string> VersionStrings = null;
                
                try
                {
                    using (StreamReader Json = File.OpenText(AppDomain.CurrentDomain.BaseDirectory + "OS versions.json"))
                    {
                        VersionStrings = JsonConvert.DeserializeObject<Dictionary<string, string>>(Json.ReadToEnd());
                        return VersionStrings[this.PrerequisiteBuild];
                    }
                }
                
                catch (KeyNotFoundException)
                {
                    return (ENTRY.ContainsKey("PrerequisiteOSVersion")) ?
                        (string)ENTRY["PrerequisiteOSVersion"] :
                        "N/A";
                }
            }
        }

        /// <summary>
        /// This provides an easily accessible regular expression to detect build numbers that are (likely to be) beta versions.
        /// </summary>
        /// <returns>
        /// A regular expression, as a string, that can be used to detect build numbers belonging to beta versions.
        /// </returns>
        public static string REGEX_BETA
        {
            get { return @"(\d)?\d[A-Z][4-6]\d{3}[a-z]?"; }
        }

        /// <summary>
        /// Checks if Apple marked the OTA package with a release type. This function returns its value, but it may not be accurate. If accuracy is needed, use the ActualReleaseType() method.
        /// </summary>
        /// <returns>
        /// The "ReleaseType" key, as a string. If the key is not present, returns "Public."
        /// </returns>
        public string ReleaseType
        {
            get
            {
                return (ENTRY.ContainsKey("ReleaseType")) ?
                    (string)ENTRY["ReleaseType"] :
                    "Public";
            }
        }

        /// <summary>
        /// Removes padding from a build number, supplied as a string argument.
        /// </summary>
        /// <returns>
        /// The build number, minus the padding for a beta.
        /// </returns>
        private string RemoveBetaPadding(string BuildNum)
        {
            if (Regex.Match(this.DeclaredBuild, REGEX_BETA).Success)
            {
                int LetterPos;

                for (LetterPos = 1; LetterPos < BuildNum.Length; LetterPos++)
                {
                    if (char.IsUpper(BuildNum[LetterPos]))
                    {
                        LetterPos++;
                        break;
                    }
                }

                return BuildNum.Substring(0, LetterPos) + BuildNum.Substring(LetterPos + 1);
            }

            else
                return BuildNum;
        }

        /// <summary>
        /// This is the size of the ZIP file.
        /// </summary>
        /// <returns>
        /// Returns the size of the OTA package as a number, formatted with commas.
        /// </returns>
        public string Size
        {
            get
            {
                if (ENTRY.ContainsKey("RealUpdateAttributes"))
                {
                    var RealUpdateAttrs = (Dictionary<string, object>)ENTRY["RealUpdateAttributes"];
                    return string.Format("{0:n0}", RealUpdateAttrs["RealUpdateDownloadSize"]);
                }

                else
                    return string.Format("{0:n0}", ENTRY["_DownloadSize"]);
            }
        }

        /// <summary>
        /// This string is used for sorting purposes.
        /// </summary>
        /// <returns>
        /// Returns the values of ActualReleaseType(), a padded build, and a padded prerequisite build number in that order.
        /// </returns>
        public string SortingString
        {
            get { return this.SortingBuild() + this.SortingPrerequisiteBuild() + this.CompatibilityVersion + this.ActualReleaseType; }
        }

        private string SortingBuild()
        {
            int LetterPos;
            string SortBuild = this.DeclaredBuild;

            // Make 9A### appear before 10A###.
            if (char.IsLetter(SortBuild[1]))
                SortBuild = '0' + SortBuild;

            // If the build number is false, replace everything after the letter with "0000."
            // This will cause betas to appear first.
            if (this.IsHonestBuild == false)
            {
                for (LetterPos = 1; LetterPos < SortBuild.Length; LetterPos++)
                {
                    if (char.IsUpper(SortBuild[LetterPos]))
                    {
                        LetterPos++;
                        break;
                    }
                }

                SortBuild = SortBuild.Substring(0, LetterPos) + "0000";
            }

            else if (Regex.Match(this.DeclaredBuild, REGEX_BETA).Success)
                SortBuild = RemoveBetaPadding(SortBuild);

            return SortBuild;
        }

        private string SortingPrerequisiteBuild()
        {
            // Sort by release type.
            int ReleaseTypeInt = 0;
            string build = this.PrerequisiteBuild;

            // Get up to (and including) the first letter. We'll get to this in a bit.
            match = Regex.Match(build, @"\d?\d[A-Z]");

            switch (this.ReleaseType)
            {
                case "Beta":
                    ReleaseTypeInt = 1;
                    break;

                case "Carrier":
                    ReleaseTypeInt = 2;
                    break;

                case "Internal":
                    ReleaseTypeInt = 3;
                    break;
            }

            if (this.IsUniversal)
                return "000000000" + ReleaseTypeInt;

            // If the build is old (i.e. before iOS 7), pad it.
            if (char.IsLetter(build[1]))
                return '0' + build;
                
            if (this.PrerequisiteVer.Contains("beta"))
                build = RemoveBetaPadding(build);

            // If the number after the capital letter is too small, pad it.
            if (new Regex("[A-z]").Split(build)[1].Length < 3 && match.Success)
                build = match.Value + '0' + new Regex(@"\d?\d[A-Z]").Replace(build, "", 1);

            // If the build does not have a letter, add a fake one to push it below similarly-numbered betas.
            if (char.IsLetter(build[build.Length-1]))
                build = build + 'z';

            return build;
        }

        /// <summary>
        /// Contains a list of all supported models.
        /// </summary>
        /// <returns>
        /// A List of strings specifying what models are supported.
        /// </returns>
        public List<string> SupportedDeviceModels
        {
            get
            {
                List<string> Models = new List<string>();

                try
                {
                    foreach (object Model in (object[])ENTRY["SupportedDeviceModels"])
                        Models.Add((string)Model);
                }

                // No models specified. (Older PLISTs do this.)
                catch (KeyNotFoundException)
                { }

                return Models;
            }
        }

        /// <summary>
        /// Contains a list of all supported devices.
        /// </summary>
        /// <returns>
        /// A List of objects (which should be strings specifying what devices are supported).
        /// </returns>
        public List<string> SupportedDevices
        {
            get
            {
                List<string> Devices = new List<string>();

                foreach (object Device in (object[])ENTRY["SupportedDevices"])
                    Devices.Add((string)Device);

                return Devices;
            }
        }

        /// <returns>
        /// The package URL, as a string.
        /// </returns>
        public string URL
        {
            get
            {
                if (ENTRY.ContainsKey("RealUpdateAttributes"))
                {
                    var RealUpdateAttrs = (Dictionary<string, object>)ENTRY["RealUpdateAttributes"];
                    return (string)RealUpdateAttrs["RealUpdateURL"];
                }

                else
                    return (string)ENTRY["__BaseURL"] + (string)ENTRY["__RelativePath"];
            }
        }
    }
}
