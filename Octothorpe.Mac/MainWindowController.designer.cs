// WARNING
//
// This file has been generated automatically by Visual Studio to store outlets and
// actions made in the UI designer. If it is removed, they will be lost.
// Manual changes to this file may not be handled correctly.
//
using Foundation;
using System.CodeDom.Compiler;

namespace Octothorpe.Mac
{
	[Register ("MainWindowController")]
	partial class MainWindowController
	{
		[Outlet]
		AppKit.NSPopUpButton DeviceSelection { get; set; }

		[Outlet]
		AppKit.NSPopUpButton FileSelection { get; set; }

		[Outlet]
		AppKit.NSPopUpButton ModelSelection { get; set; }

		[Outlet]
		AppKit.NSBox NSBoxFile { get; set; }

		[Outlet]
		AppKit.NSBox NSBoxLoc { get; set; }

		[Outlet]
		AppKit.NSButton NSButtonParse { get; set; }

		[Outlet]
		AppKit.NSButton NSButtonRemoveStubs { get; set; }

		[Outlet]
		AppKit.NSTextField NSTextFieldFile { get; set; }

		[Outlet]
		AppKit.NSTextField NSTextFieldLoc { get; set; }

		[Outlet]
		AppKit.NSTextField NSTextFieldMax { get; set; }

		[Outlet]
		AppKit.NSTextField NSTextFieldMin { get; set; }

		[Outlet]
		AppKit.NSTextView NSTextViewOutput { get; set; }

		[Outlet]
		AppKit.NSTextField PallasBuild { get; set; }

		[Outlet]
		AppKit.NSView PallasView { get; set; }

		[Outlet]
		AppKit.NSView PlistView { get; set; }

		[Outlet]
		AppKit.NSButton ShowBeta { get; set; }

		[Outlet]
		AppKit.NSButton TableHeaders { get; set; }

		[Action ("BrowseForFile:")]
		partial void BrowseForFile (AppKit.NSButton sender);

		[Action ("ChangeOutputFormat:")]
		partial void ChangeOutputFormat (AppKit.NSButton sender);

		[Action ("DeviceChanged:")]
		partial void DeviceChanged (AppKit.NSPopUpButton sender);

		[Action ("DeviceModelUpdate:")]
		partial void DeviceModelUpdate (AppKit.NSPopUpButton sender);

		[Action ("ParsingSTART:")]
		partial void ParsingSTART (AppKit.NSButton sender);

		[Action ("SourceChanged:")]
		partial void PlistChanged (AppKit.NSPopUpButton sender);

		[Action ("SourceEdited:")]
		partial void PlistPathEdited (AppKit.NSTextField sender);

		[Action ("UpdateSourceChanged:")]
		partial void UpdateSourceChanged (AppKit.NSButton sender);
		
		void ReleaseDesignerOutlets ()
		{
			if (DeviceSelection != null) {
				DeviceSelection.Dispose ();
				DeviceSelection = null;
			}

			if (FileSelection != null) {
				FileSelection.Dispose ();
				FileSelection = null;
			}

			if (ModelSelection != null) {
				ModelSelection.Dispose ();
				ModelSelection = null;
			}

			if (NSBoxFile != null) {
				NSBoxFile.Dispose ();
				NSBoxFile = null;
			}

			if (NSBoxLoc != null) {
				NSBoxLoc.Dispose ();
				NSBoxLoc = null;
			}

			if (NSButtonParse != null) {
				NSButtonParse.Dispose ();
				NSButtonParse = null;
			}

			if (NSButtonRemoveStubs != null) {
				NSButtonRemoveStubs.Dispose ();
				NSButtonRemoveStubs = null;
			}

			if (NSTextFieldFile != null) {
				NSTextFieldFile.Dispose ();
				NSTextFieldFile = null;
			}

			if (NSTextFieldLoc != null) {
				NSTextFieldLoc.Dispose ();
				NSTextFieldLoc = null;
			}

			if (NSTextFieldMax != null) {
				NSTextFieldMax.Dispose ();
				NSTextFieldMax = null;
			}

			if (NSTextFieldMin != null) {
				NSTextFieldMin.Dispose ();
				NSTextFieldMin = null;
			}

			if (PallasBuild != null) {
				PallasBuild.Dispose ();
				PallasBuild = null;
			}

			if (NSTextViewOutput != null) {
				NSTextViewOutput.Dispose ();
				NSTextViewOutput = null;
			}

			if (PallasView != null) {
				PallasView.Dispose ();
				PallasView = null;
			}

			if (PlistView != null) {
				PlistView.Dispose ();
				PlistView = null;
			}

			if (ShowBeta != null) {
				ShowBeta.Dispose ();
				ShowBeta = null;
			}

			if (TableHeaders != null) {
				TableHeaders.Dispose ();
				TableHeaders = null;
			}
		}
	}
}
