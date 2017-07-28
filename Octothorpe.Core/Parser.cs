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
using Claunia.PropertyList;
using System;
using System.Collections.Generic;
using System.Net;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading.Tasks;

namespace Octothorpe
{
	public class Parser
	{
		private bool showBeta = false,
			wikiMarkup = false,
			DeviceIsWatch = false,
			ModelNeedsChecking = false;
		private Dictionary<string, uint> BuildNumberRowspan = new Dictionary<string, uint>(),
			DateRowspan = new Dictionary<string, uint>(),
			FileRowspan = new Dictionary<string, uint>(),
			MarketingVersionRowspan = new Dictionary<string, uint>();
		private Dictionary<string, Dictionary<string, uint>> PrereqBuildRowspan = new Dictionary<string, Dictionary<string, uint>>(), // DeclaredBuild, <PrereqBuild, count>
			PrereqOSRowspan = new Dictionary<string, Dictionary<string, uint>>(); // DeclaredBuild, <PrereqOS, count>
		private readonly List<OTAPackage> Packages = new List<OTAPackage>();
		private string device, model, plist;
		private Version max, minimum;

		public string Device
		{
			set { device = value; }
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
			set
			{
				model = value;
				ModelNeedsChecking = Regex.IsMatch(model, @"iPhone8,(1|2|4)");
			}
		}

		public string Plist
		{
			get { return plist; }
			set { plist = value; }
		}

		public bool ShowBeta
		{
			set { showBeta = value; }
		}

		public bool WikiMarkup
		{
			set { wikiMarkup = value; }
		}

		public string ParsePlist()
		{
			ErrorCheck();

			AddEntries();
			SortEntries();

			if (wikiMarkup)
			{
				CountRowspan();
				return OutputWikiMarkup();
			}

			else
				return OutputHumanFormat();
		}

		private void AddEntries()
		{
			NSDictionary root;

			// Load the PLIST.
			if (Regex.IsMatch(plist, @"://mesu.apple.com/assets/"))
			{
				WebClient Fido = new WebClient();
				root = (NSDictionary)PropertyListParser.Parse(Fido.DownloadData(plist));
				Fido.Dispose();
			}

			else if (plist.Contains("://"))
				throw new ArgumentException("notmesu");

			else
				root = (NSDictionary)PropertyListParser.Parse(plist);

			// Look at every item in the NSArray named "Assets."
			Parallel.ForEach((object[])(root.Get("Assets").ToObject()), entry => 
				{
					bool matched = false;
					OTAPackage package = new OTAPackage((Dictionary<string, object>)entry); // Feed the info into a custom object so we can easily pull info and sort.
					
					// Beta check.
					if (showBeta == false && package.ActualReleaseType > 0)
						return;
	
					// For wikiMarkup markup: If a beta has two entries
					// (one for betas, one for non-betas), don't count it twice.
					if (wikiMarkup &&
						package.ReleaseType != "Public" &&
						package.BetaNumber > 0 &&
						package.DocumentationID != "iOS7Seed6")
							return;
	
					// Device check.
					matched = package.SupportedDevices.Contains(device);
	
					// Model check, if needed.
					if (matched && ModelNeedsChecking)
					{
						matched = false; // Skipping unless we can verify we want it.
	
						// Make sure "SupportedDeviceModels" exists before checking it.
						if (package.SupportedDeviceModels.Count > 0)
							matched = (package.SupportedDeviceModels.Contains(model));
					}

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
				});
		}

		private void Cleanup()
		{
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
						FileRowspan[entry.URL]++;

					else
						FileRowspan.Add(entry.URL, 1);
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
					PrereqOSRowspan[entry.DeclaredBuild][entry.PrerequisiteVer]++;
				}
				// If not, add the first tally.
				catch (KeyNotFoundException)
				{
					if (PrereqOSRowspan.ContainsKey(entry.DeclaredBuild) == false)
						PrereqOSRowspan.Add(entry.DeclaredBuild, new Dictionary<string, uint>());

					PrereqOSRowspan[entry.DeclaredBuild].Add(entry.PrerequisiteVer, 1);
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
			if (device == null || Regex.IsMatch(device, @"(AppleTV|AudioAccessory|iPad|iPhone|iPod|Watch)(\d)?\d,\d") == false)
				throw new ArgumentException("device");

			DeviceIsWatch = Regex.IsMatch(device, @"Watch\d,\d");
			ModelNeedsChecking = Regex.IsMatch(device, "(iPad6,(11|12)|iPhone8,(1|2|4))");

			// Model check.
			if (ModelNeedsChecking && (model == null || Regex.IsMatch(model, @"[BJKMNP]\d((\d)?){2}[A-Za-z]?AP") == false))
				throw new ArgumentException("model");
		}

		private string OutputHumanFormat()
		{
			StringBuilder Output = new StringBuilder();
			string osName;

			// So we don't add on to a previous run.
			Output.Length = 0;

			foreach (OTAPackage package in Packages)
			{
				if (DeviceIsWatch)
					osName = "watchOS ";

				else if (device.StartsWith("AppleTV"))
					osName = (Regex.Match(device, @"AppleTV(2,1|3,1|3,2)").Success) ? "Apple TV software " : "tvOS ";
				
				else
					osName = "iOS ";

				// Output OS version and build.
				Output.Append(osName + package.MarketingVersion);

				// Give it a beta label (if it is one).
				if (package.ActualReleaseType > 0) {
					switch (package.ActualReleaseType) {
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
						Output.Append(" " + package.BetaNumber);
				}

				Output.AppendLine(" (Build " + package.ActualBuild + ')');
				Output.AppendLine("Listed as: " + package.OSVersion + " (Build " + package.DeclaredBuild + ')');
				Output.AppendLine("Reported Release Type: " + package.ReleaseType);

				// Print prerequisites if there are any.
				if (package.IsUniversal)
					Output.AppendLine("Requires: Not specified");

				else
					Output.AppendLine(string.Format("Requires: {0} (Build {1})", package.PrerequisiteVer, package.PrerequisiteBuild));

				// Date as extracted from the url.
				Output.AppendLine(string.Format("Timestamp: {0}/{1}/{2}", package.Date('y'), package.Date('m'), package.Date('d')));

				// Compatibility Version.
				Output.AppendLine("Compatibility Version: " + package.CompatibilityVersion);

				// Print out the url and file Size.
				Output.AppendLine("URL: " + package.URL);
				Output.AppendLine("File size: " + package.Size + Environment.NewLine);
			}

			Cleanup();

			return Output.ToString();
		}

		private string OutputWikiMarkup()
		{
			bool BorkedDelta;
			Match name;
			string fileName, NewTableCell = "| ";
			StringBuilder Output = new StringBuilder();

			// So we don't add on to a previous run.
			Output.Length = 0;

			foreach (OTAPackage package in Packages)
			{
				BorkedDelta = (package.SupportedDevices.Contains("iPod5,1") && package.PrerequisiteBuild == "10B141");

				// Obtain the file name.
				fileName = string.Empty;
				name = Regex.Match(package.URL, @"[0-9a-f]{40}\.zip");

				if (name.Success)
					fileName = name.ToString();

				// Let us begin!
				Output.AppendLine("|-");

				if (MarketingVersionRowspan.ContainsKey(package.MarketingVersion))
				{
                    // Spit out a rowspan attribute.
                    if (MarketingVersionRowspan[package.MarketingVersion] > 1)
                    {
	                    // 32-bit Apple TV receives a filler for Marketing Version.
	                    // (OTAPackage.MarketingVersion for 32-bit Apple TVs returns the OS version because the Marketing Version isn't specified in the XML... Confusing, I know.)
	                    if (Regex.Match(device, "AppleTV(2,1|3,1|3,2)").Success)
	                        Output.AppendLine("| rowspan=\"" + MarketingVersionRowspan[package.MarketingVersion] + "\" | [MARKETING VERSION]");
	
	                    Output.Append("| rowspan=\"" + MarketingVersionRowspan[package.MarketingVersion] + "\" ");
                    }

                    // 32-bit Apple TV receives a filler for Marketing Version.
                    // (OTAPackage.MarketingVersion for 32-bit Apple TVs returns the OS version because the Marketing Version isn't specified in the XML... Confusing, I know.)
                    else if (Regex.Match(device, "AppleTV(2,1|3,1|3,2)").Success)
	                    Output.AppendLine("| [MARKETING VERSION]");

	                Output.Append(NewTableCell + package.MarketingVersion);

	                // Give it a beta label (if it is one).
	                if (package.ActualReleaseType > 0)
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
	                        Output.Append(" " + package.BetaNumber);
	                }

                    Output.AppendLine();

					// Output the purported version for watchOS 1.0.x.
					if (package.MarketingVersion.Contains("1.0") && package.OSVersion.Contains("8.2"))
					{
						Output.Append("| rowspan=\"" + MarketingVersionRowspan[package.MarketingVersion] + "\" | " + package.OSVersion);
						Output.AppendLine();
					}

					// Remove the count since we're done with it.
					MarketingVersionRowspan.Remove(package.MarketingVersion);
				}

				// Output build number.
				if (BuildNumberRowspan.ContainsKey(package.DeclaredBuild))
				{
					Output.Append(NewTableCell);

					// Only give rowspan if there is more than one row with the OS version.
					// Count DeclaredBuild() instead of ActualBuild() so the entry pointing betas to the final build is treated separately.
					if (BuildNumberRowspan[package.DeclaredBuild] > 1)
						Output.Append("rowspan=\"" + BuildNumberRowspan[package.DeclaredBuild] + "\" | ");

					//Remove the count since we're done with it.
					BuildNumberRowspan.Remove(package.DeclaredBuild);

					Output.Append(package.ActualBuild);

					// Do we have a false build number? If so, add a footnote reference.
					if (package.IsHonestBuild == false)
						Output.Append("<ref name=\"fakefive\" />");

					Output.AppendLine();
				}

				// Printing prerequisite version
				if (PrereqOSRowspan.ContainsKey(package.DeclaredBuild) && PrereqOSRowspan[package.DeclaredBuild].ContainsKey(package.PrerequisiteVer))
				{
					Output.Append(NewTableCell);

					// Is there more than one of this prerequisite version tallied?
					if (PrereqOSRowspan[package.DeclaredBuild][package.PrerequisiteVer] > 1)
					{
						Output.Append("rowspan=\"" + PrereqOSRowspan[package.DeclaredBuild][package.PrerequisiteVer] + "\" ");
						PrereqOSRowspan[package.DeclaredBuild].Remove(package.PrerequisiteVer);

						if (package.IsUniversal == false)
							Output.Append(NewTableCell);
					}

					// Print out the cell text
					if (package.IsUniversal)
						Output.AppendLine("colspan=\"2\" {{n/a}}");

					else
					{
						// If this is a GM, print the link to Golden Master.
						if (package.PrerequisiteVer.Contains(" GM"))
							Output.AppendLine(package.PrerequisiteVer.Replace("GM", "[[Golden Master|GM]]"));

						// Very quick check if prerequisite is a beta. This is not bulletproof.
						else if (Regex.Match(package.PrerequisiteBuild, OTAPackage.REGEX_BETA).Success && package.PrerequisiteVer.Contains("beta") == false)
							Output.AppendLine(package.PrerequisiteVer + " beta #");

						else
							Output.AppendLine(package.PrerequisiteVer);
					}
				}

				// Printing prerequisite build
				if (package.IsUniversal == false
					&& PrereqBuildRowspan.ContainsKey(package.DeclaredBuild)
					&& PrereqBuildRowspan[package.DeclaredBuild].ContainsKey(package.PrerequisiteBuild))
				{
					Output.Append(NewTableCell);

					// Is there more than one of this prerequisite build tallied?
					// Also do not use rowspan if the prerequisite build is a beta.
					if (PrereqBuildRowspan[package.DeclaredBuild][package.PrerequisiteBuild] > 1)
					{
						Output.Append("rowspan=\"" + PrereqBuildRowspan[package.DeclaredBuild][package.PrerequisiteBuild] + "\" | ");
						PrereqBuildRowspan[package.DeclaredBuild].Remove(package.PrerequisiteBuild);
					}

					Output.AppendLine(package.PrerequisiteBuild);
				}

				if (package.CompatibilityVersion > 0)
					Output.AppendLine(NewTableCell + package.CompatibilityVersion);

				// Date as extracted from the URL. Using the same rowspan count as build.
				// (Apple occasionally releases updates with the same version, but different build number, silently.)
				if (DateRowspan.ContainsKey(package.ActualBuild))
				{
					Output.Append(NewTableCell);

					// Only give rowspan if there is more than one row with the OS version.
					if (DateRowspan[package.ActualBuild] > 1)
					{
						Output.Append("rowspan=\"" + DateRowspan[package.ActualBuild] + "\" | ");
						DateRowspan.Remove(package.ActualBuild); //Remove the count since we already used it.
					}

					Output.AppendLine("{{date|" + package.Date('y') + '|' + package.Date('m') + '|' + package.Date('d') + "}}");
				}

				// Release Type.
				switch (package.ActualReleaseType)
				{
					case 1:
					case 2:
						Output.AppendLine("| Beta");
						break;
					case 3:
						Output.AppendLine("| Carrier");
						break;
					case 4:
						Output.AppendLine("| Internal");
						break;
					default:
						if (package.ReleaseType != "Public")
							Output.AppendLine("| Beta");

						else
							Output.AppendLine("| {{n/a}}");
						break;
				}

				// Is there more than one of this prerequisite version tallied?
				if (FileRowspan.ContainsKey(package.URL) || (BorkedDelta && package.OSVersion != "8.4.1"))
				{
					Output.Append(NewTableCell);

					if (BorkedDelta == false
					    && FileRowspan.ContainsKey(package.URL)
					    && FileRowspan[package.URL] > 1)
					{
						Output.Append("rowspan=\"" + FileRowspan[package.URL] + "\" | ");
					}

					Output.Append('[' + package.URL + ' ' + fileName + ']' + Environment.NewLine + NewTableCell);

					//Print file size.
					// Only give rowspan if there is more than one row with the OS version.
					if (BorkedDelta == false
						&& FileRowspan.ContainsKey(package.URL)
					    && FileRowspan[package.URL] > 1)
					{
						Output.Append("rowspan=\"" + FileRowspan[package.URL] + "\" | ");
					}

					Output.AppendLine(package.Size);

					//Remove the count since we're done with it.
					FileRowspan.Remove(package.URL);
				}
			}

			Cleanup();

			return Output.ToString();
		}

		private void SortEntries()
		{
			Packages.Sort
			(
				delegate(OTAPackage one, OTAPackage two)
				{
					return one.SortingString.CompareTo(two.SortingString);
				}
			);
		}
	}
}
