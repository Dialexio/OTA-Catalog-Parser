﻿/*
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
using Claunia.PropertyList;
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
		private readonly NSDictionary ENTRY;

		public OTAPackage(NSDictionary package)
		{
			ENTRY = package;
		}

		/// <summary>
		/// Returns the package's actual build number. (i.e. Without any of Apple's padding.)
		/// </summary>
		/// <returns>
		/// An String value of the package's actual build number. (e.g. 10A550 will be 10A550, 12F5061 will be 12F61)
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
						Console.WriteLine("Unknown ReleaseType: " + ENTRY["ReleaseType"].ToString());
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
					return (Char.IsDigit(digit)) ? (int)Char.GetNumericValue(digit) : 1;

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
					(int)ENTRY["CompatibilityVersion"].ToObject() :
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

			else {
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
			get { return ENTRY["Build"].ToString(); }
		}

		/// <summary>
		/// Reports the documentation ID that Apple assigned. This tells iOS which documentation file to load, since multiple ones may be offered.
		/// </summary>
		/// <returns>
		/// The documentation ID that corresponds to the OTA update. If one is not specified, returns "N/A."
		/// </returns>
		public string DocumentationID
		{
			get { return ENTRY.ContainsKey("SUDocumentationID") ? ENTRY["SUDocumentationID"].ToString() : "N/A"; }
		}

		/// <summary>
		/// Checks if the release has an inflated build number. Apple does this to push devices on beta builds to stable builds.
		/// </summary>
		/// <returns>
		/// A boolean value of whether this release has a false build number (true) or not (false).
		/// </returns>
		public Boolean IsHonestBuild
		{
			get { return this.ActualBuild == this.DeclaredBuild; }
		}

		/// <summary>
		/// A simple check if the release is a large, "one Size fits all" package.
		/// </summary>
		/// <returns>
		/// A boolean value of whether this release is used to cover all scenarios (true) or not (false).
		/// </returns>
		public Boolean IsUniversal
		{
			get { return this.PrerequisiteBuild == "N/A"; }
		}

		/// <summary>
		/// Returns the value of "MarketingVersion" if present. "MarketingVersion" is used in some entries to display a false version number.
		/// </summary>
		/// <returns>
		/// A String value of the "MarketingVersion" key (if it exists), otherwise returns the "OSVersion" key.
		/// </returns>
		public string MarketingVersion
		{
			get
			{
				if (ENTRY.ContainsKey("MarketingVersion"))
				{
					string mv = ENTRY["MarketingVersion"].ToString();
					return (mv.Contains(".") == false) ? mv + ".0" : mv;
				}

				else
					return this.OSVersion;
			}
		}

		/// <returns>
		/// The "OSVersion" key, as a String. For iOS 10 and newer, this will also strip "9.9." from the version.
		/// </returns>
		public string OSVersion
		{
			get
			{
				string version = ENTRY["OSVersion"].ToString();
				return (version.Substring(0, 3) == "9.9") ? version.Substring(4) : version;
			}
		}

		/// <summary>
		/// "PrerequisiteBuild" states the specific build that the OTA package is intended for, since most OTA packages are deltas.
		/// </summary>
		/// <returns>
		/// The "PrerequisiteBuild" key, as a String.
		/// </returns>
		public string PrerequisiteBuild
		{
			get
			{
				return (ENTRY.ContainsKey("PrerequisiteBuild")) ?
					ENTRY["PrerequisiteBuild"].ToString() :
					"N/A";
			}
		}

		/// <summary>
		/// "PrerequisiteVersion()" states the specific version that the OTA package is intended for, since most OTA packages are deltas.
		/// </summary>
		/// <returns>
		/// The "PrerequisiteVersion" key, as a String.
		/// </returns>

		public string PrerequisiteVer
		{
			get
			{
				Dictionary<string, string> VersionStrings = null;

				try
				{
					using (StreamReader Json = new StreamReader(AppDomain.CurrentDomain.BaseDirectory + "OS versions.json"))
					{
						VersionStrings = JsonConvert.DeserializeObject<Dictionary<string, string>>(Json.ReadToEnd());
					}
				}

				catch (Exception) { }

				if (VersionStrings != null && VersionStrings.ContainsKey(this.PrerequisiteBuild))
					return VersionStrings[this.PrerequisiteBuild];

				else if (ENTRY.ContainsKey("PrerequisiteOSVersion"))
					return ENTRY["PrerequisiteOSVersion"].ToString();

				else
					return "N/A";
			}
		}

		/// <summary>
		/// This provides an easily accessible regular expression to detect build numbers that are (likely to be) beta versions.
		/// </summary>
		/// <returns>
		/// A regular expression, as a String, that can be used to detect build numbers belonging to beta versions.
		/// </returns>
		public static string REGEX_BETA
		{
			get { return @"(\d)?\d[A-Z][4-6]\d{3}[a-z]?"; }
		}

		/// <summary>
		/// Checks if Apple marked the OTA package with a release type. This function returns its value, but it may not be accurate. If accuracy is needed, use the ActualReleaseType() method.
		/// </summary>
		/// <returns>
		/// The "ReleaseType" key, as a String. If the key is not present, returns "Public."
		/// </returns>
		public string ReleaseType
		{
			get
			{
				return (ENTRY.ContainsKey("ReleaseType")) ? ENTRY["ReleaseType"].ToString() : "Public";
			}
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
					NSDictionary RealUpdateAttrs = (NSDictionary)ENTRY["RealUpdateAttributes"];
					return string.Format("{0:n0}", long.Parse(RealUpdateAttrs["RealUpdateDownloadSize"].ToString()));
				}

				else
				{
					return string.Format("{0:n0}", long.Parse(ENTRY["_DownloadSize"].ToString()));
				}
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
			get { return this.ActualReleaseType + this.SortingBuild() + this.SortingPrerequisiteBuild() + this.CompatibilityVersion; }
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

			// Apple Watch betas go on the bottom in wikiMarkup markup.
			// As dumb as this is, it's a pain because of the OS version.
			// Hopefully this gets changed for consistency in the future...
			else if (Regex.Match(this.DeclaredBuild, REGEX_BETA).Success)
			{
				foreach (string SupportedDevice in this.SupportedDevices)
				{
					if (SupportedDevice.Contains("Watch"))
					{
						SortBuild = SortBuild.Substring(0, 3) + '9' + SortBuild.Substring(4);
						break;
					}
				}
			}

			return SortBuild;
		}

		private string SortingPrerequisiteBuild()
		{
			// Sort by release type.
			int ReleaseTypeInt = 0;
			string build = this.PrerequisiteBuild;

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

			// Get up to (and including) the first letter. We'll get to this in a bit.
			match = Regex.Match(build, @"\d?\d[A-Z]");

			// We need to take care of Apple Watch betas differently.
			if (this.PrerequisiteVer.Contains("beta"))
			{
				foreach (string SupportedDevice in this.SupportedDevices)
				{
					if (SupportedDevice.Contains("Watch"))
					{
						// Non-betas typically have a build number length of 5 or less. Pad it.
						while (build.Length < 6)
							build = match.Value + '0' + new Regex(@"\d?\d[A-Z]").Replace(build, "", 1).Substring(1);

						return build;
					}
				}
			}

			// If the build is old (i.e. before iOS 7), pad it.
			if (char.IsLetter(build[1]))
				return '0' + build;

			// If the number after the capital letter is too small, pad it.
			if (new Regex("[A-z]").Split(build)[1].Length < 3 && match.Success)
				build = match.Value + '0' + new Regex(@"\d?\d[A-Z]").Replace(build, "", 1);

			return build;
		}

		/// <summary>
		/// Contains a list of all supported models.
		/// </summary>
		/// <returns>
		/// A List of Objects (which should be Strings specifying what models are supported).
		/// </returns>
		public List<string> SupportedDeviceModels
		{
			get
			{
				List<string> Models = new List<string>();

				try
				{
					foreach (NSObject Model in ((NSArray)ENTRY["SupportedDeviceModels"]).GetArray())
					{
						Models.Add(Model.ToString());
					}
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
		/// A List of Objects (which should be Strings specifying what devices are supported).
		/// </returns>
		public List<string> SupportedDevices
		{
			get
			{
				List<string> Devices = new List<string>();

				foreach (NSObject Device in ((NSArray)ENTRY["SupportedDevices"]).GetArray())
				{
					Devices.Add(Device.ToString());
				}

				return Devices;
			}
		}

		/// <returns>
		/// The package's URL, as a String.
		/// </returns>
		public string URL
		{
			get
			{
				if (ENTRY.ContainsKey("RealUpdateAttributes"))
				{
					NSDictionary RealUpdateAttrs = (NSDictionary)ENTRY["RealUpdateAttributes"];
					return RealUpdateAttrs["RealUpdateURL"].ToString();
				}

				else
				{
					return ENTRY["__BaseURL"].ToString() + ENTRY["__RelativePath"].ToString();
				}
			}
		}
	}
}