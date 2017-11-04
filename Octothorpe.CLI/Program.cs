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
using Octothorpe.Lib;
using System;
using System.IO;

namespace Octothorpe.CLI
{
    class MainClass
    {
        public static void Main(string[] args)
        {
            Parser parser = new Parser();
            int i = 0;

            while (i < args.Length && args[i][0] == '-')
            {
                switch (args[i++])
                {
                    case "-b":
                        parser.ShowBeta = true;
                        break;

                    case "-d":
                        if (i < args.Length)
                            parser.Device = args[i++];
                        break;

                    case "-f":
                        if (i < args.Length)
                            parser.Plist = args[i++];
                        break;

                    case "-h":
                        Console.WriteLine("OTA Catalog Parser");
                        Console.WriteLine("https://github.com/Dialexio/OTA-Catalog-Parser");

                        Console.WriteLine("\nRequired Arguments:");
                        Console.WriteLine("-d <device>      Choose the device you are searching for. (e.g. iPhone8,1)");
                        Console.WriteLine("-f <file>        Specify the path to the PLIST file you are searching in.");
                        Console.WriteLine("                 This may be either a local file, or a mesu.apple.com URL.");
                        Console.WriteLine("-m <model>       Choose the model you are searching for. (e.g. N71mAP)\n                 This is only used and required for iPhone 6S or 6S Plus.");

                        Console.WriteLine("\nOptional Arguments:");
                        Console.WriteLine("-b               Displays beta firmwares. By default, this is disabled.");
                        Console.WriteLine("-max <version>   Choose the highest firmware version you are searching for. (e.g. 9.0.2)");
                        Console.WriteLine("-min <version>   Choose the lowest firmware version you are searching for. (e.g. 8.4.1)");
                        Console.WriteLine("-w               Formats the output for The iPhone Wiki.");
                        Environment.Exit(0);
                        break;

                    case "-m":
                        if (i < args.Length)
                            parser.Model = args[i++];
                        break;

                    case "-max":
                        if (i < args.Length)
                            parser.Maximum = (uint.TryParse(args[i++], out var verstring)) ?
                                new Version(verstring + ".0") :
                                new Version(verstring.ToString());
                        break;

                    case "-min":
                        if (i < args.Length)
                            parser.Minimum = (uint.TryParse(args[i++], out var verstring)) ?
                                new Version(verstring + ".0") :
                                new Version(verstring.ToString());
                        break;

                    case "-w":
                        parser.WikiMarkup = true;
                        break;
                }
            }

            try
            {
                Console.WriteLine(parser.ParsePlist());
            }

            catch (ArgumentException message)
            {
                Console.WriteLine("Argument Error!");

                switch (message.Message)
                {
                    case "device":
                        Console.WriteLine("You did not specify a device to search for. Use the \"-d\" argument to specify, e.g. \"iPhone8,1\"");
                        break;

                    case "model":
                        Console.WriteLine("You need to specify a model for this device. Use the \"-m\" argument to specify, e.g. \"N71AP\"");
                        break;

                    case "notmesu":
                        Console.WriteLine("The URL supplied should belong to mesu.apple.com.");
                        break;

                    default:
                        Console.WriteLine(message.Message);
                        Console.WriteLine(message.StackTrace);
                        break;
                }
            }

            catch (FileNotFoundException)
            {
                Console.WriteLine("File Not Found!");
                Console.WriteLine("The program was unable to load the specified file.");
            }

            catch (Exception e)
            {
                Console.WriteLine(e.Message);
                Console.WriteLine(e.StackTrace);
            }
        }
    }
}
